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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import com.alibaba.cloud.ai.lynxe.codeagent.model.CodeAgentMessage.FileChange;
import com.alibaba.cloud.ai.lynxe.llm.LlmService;

import reactor.core.publisher.Flux;

/**
 * Service that drives the Code Agent: sends user input to LLM with a specialised system
 * prompt, streams tokens back, and parses structured file output.
 */
@Service
public class CodeAgentService {

	private static final Logger log = LoggerFactory.getLogger(CodeAgentService.class);

	private final LlmService llmService;

	/** Per-session conversation history. */
	private final Map<String, List<Message>> sessionMessages = new ConcurrentHashMap<>();

	public CodeAgentService(LlmService llmService) {
		this.llmService = llmService;
	}

	/**
	 * Stream a code-generation response for the given session.
	 * @param sessionId WebSocket session id
	 * @param userInput the user's natural-language request
	 * @param onToken called for every streamed text token
	 * @param onFile called when a complete file block is parsed
	 * @param onDone called when generation finishes
	 * @param onError called on error
	 */
	public void streamGenerate(String sessionId, String userInput, Consumer<String> onToken,
			Consumer<FileChange> onFile, Runnable onDone, Consumer<String> onError) {

		try {
			List<Message> history = sessionMessages.computeIfAbsent(sessionId, k -> new ArrayList<>());

			// Build messages
			List<Message> messages = new ArrayList<>();
			messages.add(new SystemMessage(SYSTEM_PROMPT));
			messages.addAll(history);
			messages.add(new UserMessage(userInput));

			// Remember user message
			history.add(new UserMessage(userInput));

			ChatClient chatClient = llmService.getDefaultDynamicAgentChatClient();

			// Stream call
			Flux<ChatResponse> flux = chatClient.prompt(new Prompt(messages)).stream().chatResponse();

			StringBuilder fullResponse = new StringBuilder();

			flux.doOnNext(response -> {
				if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
					return;
				}
				String text = response.getResults().get(0).getOutput().getText();
				if (text != null && !text.isEmpty()) {
					fullResponse.append(text);
					onToken.accept(text);
				}
			}).doOnComplete(() -> {
				// Parse file blocks from the complete response
				String complete = fullResponse.toString();
				List<FileChange> files = parseFileBlocks(complete);
				for (FileChange file : files) {
					onFile.accept(file);
				}

				// Remember assistant response
				history.add(new AssistantMessage(complete));

				// Trim history to last 20 messages to avoid token overflow
				if (history.size() > 20) {
					List<Message> trimmed = new ArrayList<>(history.subList(history.size() - 20, history.size()));
					history.clear();
					history.addAll(trimmed);
				}

				onDone.run();
			}).doOnError(error -> {
				log.error("[CodeAgent] Streaming error for session {}: {}", sessionId, error.getMessage(), error);
				onError.accept(error.getMessage());
			}).subscribe();

		}
		catch (Exception e) {
			log.error("[CodeAgent] Failed to start generation for session {}: {}", sessionId, e.getMessage(), e);
			onError.accept(e.getMessage());
		}
	}

	/**
	 * Clear conversation history for a session.
	 */
	public void clearSession(String sessionId) {
		sessionMessages.remove(sessionId);
	}

	// ------------------------------------------------------------------
	// File block parser
	// ------------------------------------------------------------------

	private static final Pattern FILE_BLOCK_PATTERN = Pattern.compile("<file\\s+path=\"([^\"]+)\">(.*?)</file>",
			Pattern.DOTALL);

	static List<FileChange> parseFileBlocks(String text) {
		List<FileChange> files = new ArrayList<>();
		Matcher matcher = FILE_BLOCK_PATTERN.matcher(text);
		while (matcher.find()) {
			String path = matcher.group(1).trim();
			String content = matcher.group(2);
			// Remove leading/trailing blank lines inside the block
			if (content.startsWith("\n")) {
				content = content.substring(1);
			}
			if (content.endsWith("\n")) {
				content = content.substring(0, content.length() - 1);
			}
			files.add(new FileChange(path, content));
		}
		return files;
	}

	// ------------------------------------------------------------------
	// System Prompt
	// ------------------------------------------------------------------

	private static final String SYSTEM_PROMPT = """
			You are an expert web developer AI assistant. Your task is to generate complete, production-ready web application code based on user requirements.

			## Output Format

			You MUST wrap every file you create or modify in a <file> tag with the path attribute:

			<file path="src/App.tsx">
			// file content here
			</file>

			<file path="src/components/Navbar.tsx">
			// file content here
			</file>

			## Rules

			1. Always use React + TypeScript + Tailwind CSS as the default stack.
			2. Use lucide-react for icons.
			3. Generate a complete package.json with all required dependencies.
			4. Generate a vite.config.ts for the project.
			5. Generate an index.html entry point.
			6. Write clean, well-structured, accessible code.
			7. Use functional components with hooks.
			8. Include responsive design by default.
			9. Every file you output MUST be wrapped in <file path="...">...</file> tags.
			10. Do NOT output partial files. Always output the complete file content.

			## Project Structure

			Use this standard structure:
			- package.json
			- vite.config.ts
			- index.html
			- src/main.tsx
			- src/App.tsx
			- src/index.css (Tailwind directives)
			- src/components/*.tsx

			## When Modifying Existing Code

			If the user asks to modify existing code, output only the changed files with their complete content wrapped in <file> tags.

			Think step by step, then output all files.
			""";

}
