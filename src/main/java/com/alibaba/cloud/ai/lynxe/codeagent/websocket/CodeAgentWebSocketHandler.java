/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.lynxe.codeagent.websocket;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.alibaba.cloud.ai.lynxe.codeagent.CodeAgentFileNotifier;
import com.alibaba.cloud.ai.lynxe.codeagent.CodeAgentPlanService;
import com.alibaba.cloud.ai.lynxe.codeagent.model.CodeAgentMessage;
import com.alibaba.cloud.ai.lynxe.recorder.entity.vo.AgentExecutionRecord;
import com.alibaba.cloud.ai.lynxe.recorder.entity.vo.PlanExecutionRecord;
import com.alibaba.cloud.ai.lynxe.recorder.entity.vo.ThinkActRecord;
import com.alibaba.cloud.ai.lynxe.recorder.service.PlanHierarchyReaderService;
import com.alibaba.cloud.ai.lynxe.runtime.entity.vo.PlanExecutionWrapper;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.UnifiedDirectoryManager;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * WebSocket handler for Code Agent sessions. Receives user messages, delegates to the
 * plan execution engine via CodeAgentPlanService, and pushes progress updates back to the
 * client via polling.
 */
public class CodeAgentWebSocketHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(CodeAgentWebSocketHandler.class);

	private static final long POLL_INTERVAL_MS = 800;

	private final CodeAgentPlanService codeAgentPlanService;

	private final PlanHierarchyReaderService planHierarchyReaderService;

	private final UnifiedDirectoryManager directoryManager;

	private final ScheduledExecutorService scheduler;

	private final ObjectMapper objectMapper;

	private final CodeAgentFileNotifier fileNotifier;

	/** Active sessions keyed by session id. */
	private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

	/** Active polling tasks keyed by session id. */
	private final Map<String, ScheduledFuture<?>> pollingTasks = new ConcurrentHashMap<>();

	/** Tracks file paths already sent per session for incremental file pushing. */
	private final Map<String, Set<String>> sentFilePaths = new ConcurrentHashMap<>();

	/** Tracks sessionId -> rootPlanId for cleanup on disconnect. */
	private final Map<String, String> sessionPlanIds = new ConcurrentHashMap<>();

	/** Tracks sessionId -> conversationId for memory sharing across rounds. */
	private final Map<String, String> sessionConversationIds = new ConcurrentHashMap<>();

	public CodeAgentWebSocketHandler(CodeAgentPlanService codeAgentPlanService,
			PlanHierarchyReaderService planHierarchyReaderService, UnifiedDirectoryManager directoryManager,
			ScheduledExecutorService scheduler, ObjectMapper objectMapper, CodeAgentFileNotifier fileNotifier) {
		this.codeAgentPlanService = codeAgentPlanService;
		this.planHierarchyReaderService = planHierarchyReaderService;
		this.directoryManager = directoryManager;
		this.scheduler = scheduler;
		this.objectMapper = objectMapper;
		this.fileNotifier = fileNotifier;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		String sessionId = session.getId();
		sessions.put(sessionId, session);
		log.info("[CodeAgent WS] Session connected: {}", sessionId);
		sendMessage(session, CodeAgentMessage.Response.status("connected"));
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		String sessionId = session.getId();
		try {
			CodeAgentMessage.Request request = objectMapper.readValue(message.getPayload(),
					CodeAgentMessage.Request.class);

			String userInput = request.getContent();
			if (userInput == null || userInput.isBlank()) {
				sendMessage(session, CodeAgentMessage.Response.error("Empty message"));
				return;
			}

			log.info("[CodeAgent WS] Session {} received: {}", sessionId,
					userInput.length() > 100 ? userInput.substring(0, 100) + "..." : userInput);

			// Cancel any existing polling task for this session
			cancelPolling(sessionId);

			sendMessage(session, CodeAgentMessage.Response.status("generating"));

			// Execute plan asynchronously to avoid blocking WebSocket thread
			// This ensures plan_started is sent promptly even if executePlan() takes time
			scheduler.execute(() -> {
				try {
					long t0 = System.nanoTime();
					log.info("[CodeAgent WS Timing] Starting executePlan for session {}", sessionId);

					// 获取或生成当前 session 的 conversationId，用于多轮记忆共享
					String conversationId = sessionConversationIds.computeIfAbsent(sessionId,
							k -> UUID.randomUUID().toString());
					log.info("[CodeAgent WS] Using conversationId={} for session {}", conversationId, sessionId);

					PlanExecutionWrapper wrapper = codeAgentPlanService.executePlan(userInput, conversationId);
					String rootPlanId = wrapper.getRootPlanId();

					long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
					log.info("[CodeAgent WS Timing] executePlan returned: {}ms, rootPlanId={}",
							elapsedMs, rootPlanId);

					if (rootPlanId == null) {
						log.error("[CodeAgent WS] rootPlanId is null for session {}", sessionId);
						sendMessage(session, CodeAgentMessage.Response.error("Failed to start plan execution"));
						sendMessage(session, CodeAgentMessage.Response.done());
						return;
					}

					// 立即发送 plan_started（executePlan 现在毫秒返回）
					CodeAgentMessage.Response planStartedMsg = CodeAgentMessage.Response.planStarted(rootPlanId);
					planStartedMsg.setConversationId(conversationId);
					sendMessage(session, planStartedMsg);

					log.info("[CodeAgent WS Timing] plan_started sent: {}ms",
							(System.nanoTime() - t0) / 1_000_000);

					// 发送中间状态：正在初始化 Agent
					sendMessage(session, CodeAgentMessage.Response.status("initializing_agent"));

					log.info("[CodeAgent WS] Plan started for session {}, rootPlanId={}", sessionId, rootPlanId);

					// Start polling for plan progress
					startPolling(session, sessionId, rootPlanId);

					log.info("[CodeAgent WS Timing] polling started: {}ms",
							(System.nanoTime() - t0) / 1_000_000);

					// Handle async completion
					wrapper.getResult().whenComplete((result, throwable) -> {
						if (throwable != null) {
							log.error("[CodeAgent WS] Plan execution failed for session {}: {}", sessionId,
									throwable.getMessage());
							sendMessage(session,
									CodeAgentMessage.Response.error("Plan execution failed: " + throwable.getMessage()));
						}
						else {
							log.info("[CodeAgent WS] Plan execution completed (async) for session {}, planId={}",
									sessionId, rootPlanId);
						}
						// Polling loop will detect completion and send plan_completed + done
					});
				}
				catch (Exception e) {
					log.error("[CodeAgent WS] Error executing plan for session {}: {}", sessionId, e.getMessage(), e);
					sendMessage(session, CodeAgentMessage.Response.error("Internal error: " + e.getMessage()));
				}
			});

		}
		catch (Exception e) {
			log.error("[CodeAgent WS] Error handling message for session {}: {}", sessionId, e.getMessage(), e);
			sendMessage(session, CodeAgentMessage.Response.error("Internal error: " + e.getMessage()));
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		String sessionId = session.getId();
		sessions.remove(sessionId);
		sessionConversationIds.remove(sessionId);
		cancelPolling(sessionId);
		log.info("[CodeAgent WS] Session disconnected: {} ({})", sessionId, status);
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) {
		log.error("[CodeAgent WS] Transport error for session {}: {}", session.getId(), exception.getMessage());
	}

	/**
	 * Start a periodic polling task that reads plan execution progress and pushes updates
	 * via WebSocket.
	 */
	private void startPolling(WebSocketSession session, String sessionId, String rootPlanId) {
		// Track session -> plan mapping for cleanup
		sessionPlanIds.put(sessionId, rootPlanId);

		// Register event-driven file push listener
		Set<String> alreadySent = sentFilePaths.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet());
		if (fileNotifier != null) {
			fileNotifier.register(rootPlanId, (path, content) -> {
				String normalized = normalizePath(path);
				if (alreadySent.add(normalized)) {
					sendMessage(session, CodeAgentMessage.Response.file(normalized, content));
					log.info("[CodeAgent WS] Sent file via event push: {}", normalized);
				}
			});
		}

		ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(() -> {
			try {
				if (!session.isOpen()) {
					log.info("[CodeAgent WS] Session {} closed during polling, cancelling", sessionId);
					cancelPolling(sessionId);
					return;
				}

				PlanExecutionRecord planRecord = planHierarchyReaderService.readPlanTreeByRootId(rootPlanId);
				if (planRecord == null) {
					log.debug("[CodeAgent WS] Plan record not yet available for planId={}", rootPlanId);
					return; // Plan not yet recorded, wait for next poll
				}

				if (planRecord.isCompleted()) {
					log.info("[CodeAgent WS] Plan completed for session {}, planId={}", sessionId, rootPlanId);

					// Send final progress + completion
					sendMessage(session, CodeAgentMessage.Response.planCompleted(rootPlanId, planRecord));

					// Try record-based extraction first
					int recordFiles = extractAndSendNewFiles(session, planRecord, alreadySent);

					// Filesystem fallback: scan inner_storage directory for files
					int fsFiles = extractAndSendFilesFromFileSystem(session, rootPlanId, alreadySent);

					log.info(
							"[CodeAgent WS] File extraction complete for planId={}: {} from records, {} from filesystem, {} total sent",
							rootPlanId, recordFiles, fsFiles, alreadySent.size());

					sendMessage(session, CodeAgentMessage.Response.done());
					cancelPolling(sessionId);
				}
				else {
					// Send progress update
					sendMessage(session, CodeAgentMessage.Response.planProgress(rootPlanId, planRecord));

					// Try to extract any new files during progress
					int recordFiles = extractAndSendNewFiles(session, planRecord, alreadySent);

					// Also check filesystem for files written but not yet in records
					int fsFiles = extractAndSendFilesFromFileSystem(session, rootPlanId, alreadySent);

					if (recordFiles > 0 || fsFiles > 0) {
						log.info(
								"[CodeAgent WS] Progress file extraction for planId={}: {} from records, {} from filesystem",
								rootPlanId, recordFiles, fsFiles);
					}
				}
			}
			catch (Exception e) {
				log.error("[CodeAgent WS] Polling error for session {}, planId {}: {}", sessionId, rootPlanId,
						e.getMessage(), e);
			}
		}, POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);

		pollingTasks.put(sessionId, future);
		log.info("[CodeAgent WS] Started polling for session {}, planId {}", sessionId, rootPlanId);
	}

	/**
	 * Cancel the polling task for a session.
	 */
	private void cancelPolling(String sessionId) {
		ScheduledFuture<?> future = pollingTasks.remove(sessionId);
		if (future != null) {
			future.cancel(false);
			log.info("[CodeAgent WS] Cancelled polling for session {}", sessionId);
		}
		// Unregister file event listener
		String rootPlanId = sessionPlanIds.remove(sessionId);
		if (rootPlanId != null && fileNotifier != null) {
			fileNotifier.unregister(rootPlanId);
		}
		sentFilePaths.remove(sessionId);
	}

	/**
	 * Extract file changes from the plan execution record's agent execution results and
	 * send them as file messages. Only sends files whose paths are not yet in
	 * {@code alreadySent}, then adds them to the set.
	 * @return number of new files sent
	 */
	private int extractAndSendNewFiles(WebSocketSession session, PlanExecutionRecord planRecord,
			Set<String> alreadySent) {
		List<AgentExecutionRecord> agentRecords = planRecord.getAgentExecutionSequence();
		if (agentRecords == null || agentRecords.isEmpty()) {
			log.debug("[CodeAgent WS] No agent execution records found in plan record");
			return 0;
		}

		log.debug("[CodeAgent WS] Scanning {} agent records for files", agentRecords.size());

		int fileCount = 0;
		for (AgentExecutionRecord agentRecord : agentRecords) {
			List<ThinkActRecord> thinkActSteps = agentRecord.getThinkActSteps();
			if (thinkActSteps == null) {
				continue;
			}
			for (ThinkActRecord tar : thinkActSteps) {
				if (tar.getActToolInfoList() == null) {
					continue;
				}
				for (var actTool : tar.getActToolInfoList()) {
					String toolName = actTool.getName();
					log.debug("[CodeAgent WS] Found tool call: name={}, hasParams={}", toolName,
							actTool.getParameters() != null);

					// Check for file-write tools — match broadly
					if (toolName != null && (toolName.contains("FileWrite") || toolName.contains("file_write")
							|| toolName.contains("WriteFile") || toolName.contains("write-file")
							|| toolName.contains("write_file"))) {
						String toolInput = actTool.getParameters();
						if (toolInput == null) {
							log.warn("[CodeAgent WS] Tool {} has null parameters", toolName);
							continue;
						}
						try {
							var inputNode = objectMapper.readTree(toolInput);
							String path = null;
							String content = null;

							// Try various path key names
							for (String key : new String[] { "file_path", "filePath", "path", "fileName",
									"file_name" }) {
								if (inputNode.has(key)) {
									path = inputNode.get(key).asText();
									break;
								}
							}
							// Try various content key names
							for (String key : new String[] { "contents", "content", "data", "text" }) {
								if (inputNode.has(key)) {
									content = inputNode.get(key).asText();
									break;
								}
							}

							if (path != null && content != null) {
								// Normalize path — remove leading plan-id prefix, slashes, ./
								path = normalizePath(path);
								if (alreadySent.add(path)) {
									sendMessage(session, CodeAgentMessage.Response.file(path, content));
									fileCount++;
									log.info("[CodeAgent WS] Sent file from records: {} ({} bytes)", path,
											content.length());
								}
							}
							else {
								log.warn(
										"[CodeAgent WS] Tool {} missing path or content. path={}, contentLen={}, keys={}",
										toolName, path, content != null ? content.length() : "null",
										iteratorToString(inputNode.fieldNames()));
							}
						}
						catch (Exception e) {
							log.warn("[CodeAgent WS] Failed to parse tool input for {}: {}", toolName,
									e.getMessage());
						}
					}
				}
			}
		}
		return fileCount;
	}

	/**
	 * Scan the inner_storage directory for files and send any that haven't been sent yet.
	 * This serves as a reliable fallback when tool-record-based extraction fails.
	 * @return number of new files sent
	 */
	private int extractAndSendFilesFromFileSystem(WebSocketSession session, String rootPlanId,
			Set<String> alreadySent) {
		try {
			Path planDir = directoryManager.getRootPlanDirectory(rootPlanId);
			if (!Files.exists(planDir) || !Files.isDirectory(planDir)) {
				log.debug("[CodeAgent WS] Plan directory does not exist: {}", planDir);
				return 0;
			}

			int fileCount = 0;
			try (Stream<Path> walk = Files.walk(planDir)) {
				List<Path> filePaths = walk.filter(Files::isRegularFile)
					.filter(p -> !p.getFileName().toString().startsWith("."))
					.filter(p -> {
						// Skip linked_external directory
						String rel = planDir.relativize(p).toString().replace('\\', '/');
						return !rel.startsWith("linked_external");
					})
					.filter(p -> isTextFile(p.getFileName().toString()))
					.toList();

				log.debug("[CodeAgent WS] Found {} text files in plan directory {}", filePaths.size(), planDir);

				for (Path filePath : filePaths) {
					String relativePath = planDir.relativize(filePath).toString().replace('\\', '/');
					// Normalize: remove sub-plan ID directories if present (plan-xxx/file.html
					// -> file.html)
					relativePath = normalizePath(relativePath);

					if (alreadySent.add(relativePath)) {
						try {
							String content = Files.readString(filePath, StandardCharsets.UTF_8);
							sendMessage(session, CodeAgentMessage.Response.file(relativePath, content));
							fileCount++;
							log.info("[CodeAgent WS] Sent file from filesystem: {} ({} bytes)", relativePath,
									content.length());
						}
						catch (IOException e) {
							log.warn("[CodeAgent WS] Failed to read file {}: {}", filePath, e.getMessage());
						}
					}
				}
			}
			return fileCount;
		}
		catch (Exception e) {
			log.error("[CodeAgent WS] Error scanning plan directory for rootPlanId={}: {}", rootPlanId,
					e.getMessage(), e);
			return 0;
		}
	}

	/**
	 * Normalize a file path by removing plan-ID prefixes, leading slashes, and ./
	 * prefixes.
	 */
	private String normalizePath(String path) {
		if (path == null) {
			return null;
		}
		path = path.replace('\\', '/');
		// Remove leading slashes
		while (path.startsWith("/")) {
			path = path.substring(1);
		}
		// Remove ./ prefix
		if (path.startsWith("./")) {
			path = path.substring(2);
		}
		// Remove plan-ID directory prefix (e.g., "plan-123456/index.html" ->
		// "index.html")
		if (path.matches("^plan-[^/]+/.*")) {
			path = path.replaceFirst("^plan-[^/]+/", "");
		}
		return path;
	}

	/**
	 * Check if a file is a supported text file by extension.
	 */
	private boolean isTextFile(String fileName) {
		int dotIdx = fileName.lastIndexOf('.');
		if (dotIdx < 0) {
			return false;
		}
		String ext = fileName.substring(dotIdx).toLowerCase();
		return UnifiedDirectoryManager.SUPPORTED_TEXT_FILE_EXTENSIONS.contains(ext);
	}

	private String iteratorToString(java.util.Iterator<String> iter) {
		StringBuilder sb = new StringBuilder("[");
		while (iter.hasNext()) {
			if (sb.length() > 1) {
				sb.append(", ");
			}
			sb.append(iter.next());
		}
		sb.append("]");
		return sb.toString();
	}

	private void sendMessage(WebSocketSession session, CodeAgentMessage.Response response) {
		if (!session.isOpen()) {
			return;
		}
		try {
			String json = objectMapper.writeValueAsString(response);
			synchronized (session) {
				session.sendMessage(new TextMessage(json));
			}
		}
		catch (IOException e) {
			log.error("[CodeAgent WS] Failed to send message to session {}: {}", session.getId(), e.getMessage());
		}
	}

}
