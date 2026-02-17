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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.alibaba.cloud.ai.lynxe.planning.service.IPlanParameterMappingService;
import com.alibaba.cloud.ai.lynxe.planning.service.PlanTemplateService;
import com.alibaba.cloud.ai.lynxe.runtime.entity.vo.PlanExecutionResult;
import com.alibaba.cloud.ai.lynxe.runtime.entity.vo.PlanExecutionWrapper;
import com.alibaba.cloud.ai.lynxe.runtime.entity.vo.PlanInterface;
import com.alibaba.cloud.ai.lynxe.runtime.entity.vo.RequestSource;
import com.alibaba.cloud.ai.lynxe.runtime.service.PlanIdDispatcher;
import com.alibaba.cloud.ai.lynxe.runtime.service.PlanningCoordinator;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service that bridges Code Agent WebSocket requests to the plan execution engine.
 * Loads the code-agent plan template, replaces parameters, and delegates to
 * PlanningCoordinator for execution.
 */
@Service
public class CodeAgentPlanService {

	private static final Logger log = LoggerFactory.getLogger(CodeAgentPlanService.class);

	private static final String CODE_AGENT_PLAN_TEMPLATE_ID = "code-agent-plan-001";

	/** 独立线程池，用于异步执行 planningCoordinator.executeByPlan()，避免阻塞调用线程 */
	private final ExecutorService asyncExecutor = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "code-agent-plan-async");
		t.setDaemon(true);
		return t;
	});

	@Autowired
	@Lazy
	private PlanningCoordinator planningCoordinator;

	@Autowired
	private PlanTemplateService planTemplateService;

	@Autowired
	private IPlanParameterMappingService parameterMappingService;

	@Autowired
	private PlanIdDispatcher planIdDispatcher;

	@Autowired
	private ObjectMapper objectMapper;

	/**
	 * Execute a code generation plan for the given user input. Follows the same pattern
	 * as LynxeController.executePlanTemplate().
	 * @param userInput the user's natural-language requirement
	 * @return PlanExecutionWrapper containing rootPlanId and the async result future
	 */
	public PlanExecutionWrapper executePlan(String userInput) {
		return executePlan(userInput, null);
	}

	/**
	 * Execute a code generation plan for the given user input with conversation context.
	 * @param userInput the user's natural-language requirement
	 * @param conversationId conversation ID for memory sharing across rounds (can be null)
	 * @return PlanExecutionWrapper containing rootPlanId and the async result future
	 */
	public PlanExecutionWrapper executePlan(String userInput, String conversationId) {
		long t0 = System.nanoTime();
		try {
			// 快速部分：模板加载（毫秒级）
			String planJson = planTemplateService.getLatestPlanVersion(CODE_AGENT_PLAN_TEMPLATE_ID);
			if (planJson == null) {
				throw new RuntimeException("Code Agent plan template not found: " + CODE_AGENT_PLAN_TEMPLATE_ID);
			}
			log.info("[CodeAgent Timing] Template loaded: {}ms", (System.nanoTime() - t0) / 1_000_000);

			// 快速部分：planId 生成（毫秒级）
			String currentPlanId = planIdDispatcher.generatePlanId();
			String rootPlanId = currentPlanId;
			log.info("[CodeAgent] Generated planId: {}", currentPlanId);

			// 快速部分：参数替换 + JSON 解析（毫秒级）
			Map<String, Object> replacementParams = new HashMap<>();
			replacementParams.put("userRequirement", userInput);
			replacementParams.put("planId", rootPlanId);

			planJson = parameterMappingService.replaceParametersInJson(planJson, replacementParams);

			PlanInterface plan = objectMapper.readValue(planJson, PlanInterface.class);

			// 将 plan title 设为用户实际输入，使 DB 中 user_request 列保存真实需求
			// （默认 title 是模板标题如 "Code Agent - Web 应用生成器"，不是用户输入）
			plan.setTitle(userInput);

			log.info("[CodeAgent Timing] Plan parsed: {}ms", (System.nanoTime() - t0) / 1_000_000);

			// 重操作全部异步执行：目录创建、DB 写入、Agent 创建、LLM 调用
			// 使用独立线程池，避免 executeByPlan() 中的同步 LLM 调用阻塞当前线程
			CompletableFuture<PlanExecutionResult> future = CompletableFuture.supplyAsync(() -> {
				log.info("[CodeAgent Timing] Async execution started on thread: {}", Thread.currentThread().getName());
				return planningCoordinator.executeByPlan(plan, rootPlanId, null,
						currentPlanId, null, RequestSource.VUE_DIALOG, null, 0, conversationId);
			}, asyncExecutor).thenCompose(f -> f); // 展平 Future<Future<R>> → Future<R>

			log.info("[CodeAgent Timing] executePlan() returning immediately: {}ms", (System.nanoTime() - t0) / 1_000_000);
			log.info("[CodeAgent] Plan execution dispatched (async): rootPlanId={}", rootPlanId);
			return new PlanExecutionWrapper(future, rootPlanId);

		}
		catch (Exception e) {
			log.error("[CodeAgent] Failed to execute plan: {}", e.getMessage(), e);
			CompletableFuture<PlanExecutionResult> failedFuture = new CompletableFuture<>();
			failedFuture.completeExceptionally(new RuntimeException("Code Agent plan execution failed: " + e.getMessage(), e));
			return new PlanExecutionWrapper(failedFuture, null);
		}
	}

}
