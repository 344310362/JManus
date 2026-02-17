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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.alibaba.cloud.ai.lynxe.codeagent.CodeAgentFileNotifier;
import com.alibaba.cloud.ai.lynxe.codeagent.CodeAgentPlanService;
import com.alibaba.cloud.ai.lynxe.recorder.service.PlanHierarchyReaderService;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.UnifiedDirectoryManager;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSocket
public class CodeAgentWebSocketConfig implements WebSocketConfigurer {

	private final CodeAgentPlanService codeAgentPlanService;

	private final PlanHierarchyReaderService planHierarchyReaderService;

	private final UnifiedDirectoryManager directoryManager;

	private final ObjectMapper objectMapper;

	private final CodeAgentFileNotifier fileNotifier;

	public CodeAgentWebSocketConfig(CodeAgentPlanService codeAgentPlanService,
			PlanHierarchyReaderService planHierarchyReaderService, UnifiedDirectoryManager directoryManager,
			ObjectMapper objectMapper, CodeAgentFileNotifier fileNotifier) {
		this.codeAgentPlanService = codeAgentPlanService;
		this.planHierarchyReaderService = planHierarchyReaderService;
		this.directoryManager = directoryManager;
		this.objectMapper = objectMapper;
		this.fileNotifier = fileNotifier;
	}

	@Bean(name = "codeAgentScheduler")
	public ScheduledExecutorService codeAgentScheduler() {
		return Executors.newScheduledThreadPool(4);
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(
				new CodeAgentWebSocketHandler(codeAgentPlanService, planHierarchyReaderService, directoryManager,
						codeAgentScheduler(), objectMapper, fileNotifier),
				"/ws/code-agent").setAllowedOrigins("*");
	}

}
