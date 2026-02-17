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
package com.alibaba.cloud.ai.lynxe.codeagent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.cloud.ai.lynxe.recorder.entity.po.PlanExecutionRecordEntity;
import com.alibaba.cloud.ai.lynxe.recorder.entity.vo.AgentExecutionRecord;
import com.alibaba.cloud.ai.lynxe.recorder.entity.vo.PlanExecutionRecord;
import com.alibaba.cloud.ai.lynxe.recorder.entity.vo.ThinkActRecord;
import com.alibaba.cloud.ai.lynxe.recorder.repository.PlanExecutionRecordRepository;
import com.alibaba.cloud.ai.lynxe.recorder.service.PlanHierarchyReaderService;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.UnifiedDirectoryManager;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Code Agent 历史会话控制器。
 * <p>
 * GET /api/code-agent/sessions — 列出最近的 Code Agent 历史会话（按 conversationId 分组）。
 * GET /api/code-agent/sessions/{conversationId} — 获取完整会话数据（多轮 planRecords + files）。
 */
@RestController
@RequestMapping("/api/code-agent/sessions")
@CrossOrigin(origins = "*")
public class CodeAgentSessionController {

	private static final Logger log = LoggerFactory.getLogger(CodeAgentSessionController.class);

	private static final String CODE_AGENT_TITLE_PATTERN = "Code Agent";

	@Autowired
	private PlanExecutionRecordRepository planExecutionRecordRepository;

	@Autowired
	private PlanHierarchyReaderService planHierarchyReaderService;

	@Autowired
	private UnifiedDirectoryManager directoryManager;

	@Autowired
	private ObjectMapper objectMapper;

	/**
	 * 列出最近的 Code Agent 历史会话（按 conversationId 去重，最多 20 条）。
	 * 优先使用 conversationId 分组的查询，同时兼容旧数据（没有 conversationId 的记录按 rootPlanId 展示）。
	 */
	@GetMapping
	public ResponseEntity<List<Map<String, Object>>> listSessions() {
		List<Map<String, Object>> sessions = new ArrayList<>();

		// 1. 按 conversationId 分组查询（新数据，plan title 已改为用户输入）
		List<PlanExecutionRecordEntity> groupedRecords = planExecutionRecordRepository
			.findDistinctSessionsByConversationId();

		Set<String> seenConversationIds = new HashSet<>();
		for (PlanExecutionRecordEntity r : groupedRecords) {
			if (sessions.size() >= 20) {
				break;
			}
			String convId = r.getConversationId();
			if (convId == null || !seenConversationIds.add(convId)) {
				continue;
			}

			// 查询该 conversationId 下所有轮次，统计轮数和最新状态
			List<PlanExecutionRecordEntity> rounds = planExecutionRecordRepository
				.findByConversationIdOrderByStartTimeAsc(convId);

			// 取第一轮的 userRequest 作为标题
			String firstUserRequest = r.getUserRequest();
			// 判断是否全部完成
			boolean allCompleted = rounds.stream().allMatch(PlanExecutionRecordEntity::isCompleted);
			// 取最后一轮的结束时间
			PlanExecutionRecordEntity lastRound = rounds.get(rounds.size() - 1);

			Map<String, Object> m = new HashMap<>();
			m.put("conversationId", convId);
			m.put("title", r.getTitle());
			m.put("userRequest", truncateText(firstUserRequest, 80));
			m.put("startTime", r.getStartTime());
			m.put("endTime", lastRound.getEndTime());
			m.put("completed", allCompleted);
			m.put("roundCount", rounds.size());
			sessions.add(m);
		}

		// 2. 兼容旧数据：没有 conversationId 的记录按旧逻辑展示
		if (sessions.size() < 20) {
			List<PlanExecutionRecordEntity> allRecords = planExecutionRecordRepository
				.findRootPlansByTitleContaining(CODE_AGENT_TITLE_PATTERN);
			for (PlanExecutionRecordEntity r : allRecords) {
				if (sessions.size() >= 20) {
					break;
				}
				// 跳过已有 conversationId 的（已在上面处理过）
				if (r.getConversationId() != null) {
					continue;
				}
				String planId = r.getRootPlanId() != null ? r.getRootPlanId() : r.getCurrentPlanId();
				Map<String, Object> m = new HashMap<>();
				m.put("conversationId", planId); // 旧数据用 rootPlanId 作为 conversationId
				m.put("title", r.getTitle());
				m.put("userRequest", truncateText(r.getUserRequest(), 80));
				m.put("startTime", r.getStartTime());
				m.put("endTime", r.getEndTime());
				m.put("completed", r.isCompleted());
				m.put("roundCount", 1);
				sessions.add(m);
			}
		}

		return ResponseEntity.ok(sessions);
	}

	/**
	 * 获取指定会话的完整数据：多轮 planRecords + 聚合的文件列表。
	 * 支持 conversationId（新数据）和 rootPlanId（旧数据）两种查询方式。
	 */
	@GetMapping("/{conversationId}")
	public ResponseEntity<Map<String, Object>> getSession(@PathVariable String conversationId) {
		// 先尝试按 conversationId 查询多轮
		List<PlanExecutionRecordEntity> roundEntities = planExecutionRecordRepository
			.findByConversationIdOrderByStartTimeAsc(conversationId);

		List<Map<String, Object>> rounds = new ArrayList<>();
		Set<String> seenPaths = new HashSet<>();
		List<Map<String, String>> allFiles = new ArrayList<>();

		if (roundEntities != null && !roundEntities.isEmpty()) {
			// 新数据：按 conversationId 查到多轮
			for (PlanExecutionRecordEntity entity : roundEntities) {
				String rootPlanId = entity.getRootPlanId() != null ? entity.getRootPlanId()
						: entity.getCurrentPlanId();
				PlanExecutionRecord planRecord = planHierarchyReaderService.readPlanTreeByRootId(rootPlanId);
				if (planRecord == null) {
					planRecord = planHierarchyReaderService.readSinglePlanById(entity.getCurrentPlanId());
				}
				if (planRecord == null) {
					continue;
				}

				Map<String, Object> roundData = new HashMap<>();
				roundData.put("rootPlanId", rootPlanId);
				roundData.put("userRequest", entity.getUserRequest());
				roundData.put("planRecord", planRecord);
				roundData.put("completed", entity.isCompleted());
				roundData.put("startTime", entity.getStartTime());
				rounds.add(roundData);

				// 聚合文件（后面的轮次覆盖前面的同名文件）
				List<Map<String, String>> roundFiles = extractFiles(planRecord, rootPlanId);
				for (Map<String, String> file : roundFiles) {
					String path = file.get("path");
					// 移除旧版本，加入新版本（后续轮次覆盖）
					if (seenPaths.contains(path)) {
						allFiles.removeIf(f -> path.equals(f.get("path")));
					}
					seenPaths.add(path);
					allFiles.add(file);
				}
			}
		}
		else {
			// 兼容旧数据：按 rootPlanId 查单轮
			PlanExecutionRecord planRecord = planHierarchyReaderService.readPlanTreeByRootId(conversationId);
			if (planRecord == null) {
				planRecord = planHierarchyReaderService.readSinglePlanById(conversationId);
			}
			if (planRecord == null) {
				return ResponseEntity.notFound().build();
			}

			Map<String, Object> roundData = new HashMap<>();
			roundData.put("rootPlanId", conversationId);
			roundData.put("userRequest", planRecord.getUserRequest());
			roundData.put("planRecord", planRecord);
			roundData.put("completed", planRecord.isCompleted());
			rounds.add(roundData);

			allFiles = extractFiles(planRecord, conversationId);
		}

		if (rounds.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		Map<String, Object> result = new HashMap<>();
		result.put("conversationId", conversationId);
		result.put("rounds", rounds);
		result.put("files", allFiles);
		result.put("userRequest", rounds.get(0).get("userRequest"));

		return ResponseEntity.ok(result);
	}

	/**
	 * 从计划执行记录中提取文件（工具调用参数 + 文件系统扫描）。
	 */
	private List<Map<String, String>> extractFiles(PlanExecutionRecord planRecord, String rootPlanId) {
		Set<String> seenPaths = new HashSet<>();
		List<Map<String, String>> files = new ArrayList<>();

		// 1. 从 ActToolInfo 中提取文件写操作
		List<AgentExecutionRecord> agentRecords = planRecord.getAgentExecutionSequence();
		if (agentRecords != null) {
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
						if (toolName != null && (toolName.contains("FileWrite") || toolName.contains("file_write")
								|| toolName.contains("WriteFile") || toolName.contains("write-file")
								|| toolName.contains("write_file"))) {
							String toolInput = actTool.getParameters();
							if (toolInput == null) {
								continue;
							}
							try {
								var inputNode = objectMapper.readTree(toolInput);
								String path = null;
								String content = null;
								for (String key : new String[] { "file_path", "filePath", "path", "fileName",
										"file_name" }) {
									if (inputNode.has(key)) {
										path = inputNode.get(key).asText();
										break;
									}
								}
								for (String key : new String[] { "contents", "content", "data", "text" }) {
									if (inputNode.has(key)) {
										content = inputNode.get(key).asText();
										break;
									}
								}
								if (path != null && content != null) {
									path = normalizePath(path);
									if (seenPaths.add(path)) {
										files.add(Map.of("path", path, "content", content));
									}
								}
							}
							catch (Exception e) {
								log.warn("[CodeAgent Session] Failed to parse tool input for {}: {}", toolName,
										e.getMessage());
							}
						}
					}
				}
			}
		}

		// 2. 从文件系统中补充
		try {
			Path planDir = directoryManager.getRootPlanDirectory(rootPlanId);
			if (Files.exists(planDir) && Files.isDirectory(planDir)) {
				try (Stream<Path> walk = Files.walk(planDir)) {
					walk.filter(Files::isRegularFile)
						.filter(p -> !p.getFileName().toString().startsWith("."))
						.filter(p -> {
							String rel = planDir.relativize(p).toString().replace('\\', '/');
							return !rel.startsWith("linked_external");
						})
						.filter(p -> isTextFile(p.getFileName().toString()))
						.forEach(filePath -> {
							String relativePath = normalizePath(
									planDir.relativize(filePath).toString().replace('\\', '/'));
							if (seenPaths.add(relativePath)) {
								try {
									String content = Files.readString(filePath, StandardCharsets.UTF_8);
									files.add(Map.of("path", relativePath, "content", content));
								}
								catch (IOException e) {
									log.warn("[CodeAgent Session] Failed to read file {}: {}", filePath,
											e.getMessage());
								}
							}
						});
				}
			}
		}
		catch (Exception e) {
			log.warn("[CodeAgent Session] Error scanning filesystem for rootPlanId={}: {}", rootPlanId,
					e.getMessage());
		}

		return files;
	}

	private String normalizePath(String path) {
		if (path == null) {
			return null;
		}
		path = path.replace('\\', '/');
		while (path.startsWith("/")) {
			path = path.substring(1);
		}
		if (path.startsWith("./")) {
			path = path.substring(2);
		}
		if (path.matches("^plan-[^/]+/.*")) {
			path = path.replaceFirst("^plan-[^/]+/", "");
		}
		return path;
	}

	private boolean isTextFile(String fileName) {
		int dotIdx = fileName.lastIndexOf('.');
		if (dotIdx < 0) {
			return false;
		}
		String ext = fileName.substring(dotIdx).toLowerCase();
		return UnifiedDirectoryManager.SUPPORTED_TEXT_FILE_EXTENSIONS.contains(ext);
	}

	private String truncateText(String text, int maxLen) {
		if (text == null) {
			return "";
		}
		if (text.length() <= maxLen) {
			return text;
		}
		return text.substring(0, maxLen) + "...";
	}

}
