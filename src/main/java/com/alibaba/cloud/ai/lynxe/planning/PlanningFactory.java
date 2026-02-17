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

package com.alibaba.cloud.ai.lynxe.planning;

import com.alibaba.cloud.ai.lynxe.codeagent.CodeAgentFileNotifier;
import com.alibaba.cloud.ai.lynxe.tool.devops.ServiceOperateTool;
import com.alibaba.cloud.ai.lynxe.tool.hr.HRTool;
import com.alibaba.cloud.ai.lynxe.tool.oss.OssClientTool;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.alibaba.cloud.ai.lynxe.agent.ToolCallbackProvider;
import com.alibaba.cloud.ai.lynxe.config.LynxeProperties;
import com.alibaba.cloud.ai.lynxe.llm.LlmService;
import com.alibaba.cloud.ai.lynxe.llm.StreamingResponseHandler;
import com.alibaba.cloud.ai.lynxe.mcp.model.vo.McpServiceEntity;
import com.alibaba.cloud.ai.lynxe.mcp.model.vo.McpTool;
import com.alibaba.cloud.ai.lynxe.mcp.service.McpService;
import com.alibaba.cloud.ai.lynxe.model.repository.DynamicModelRepository;
import com.alibaba.cloud.ai.lynxe.planning.service.PlanFinalizer;
import com.alibaba.cloud.ai.lynxe.recorder.service.PlanExecutionRecorder;
import com.alibaba.cloud.ai.lynxe.runtime.executor.ImageRecognitionExecutorPool;
import com.alibaba.cloud.ai.lynxe.runtime.service.PlanIdDispatcher;
import com.alibaba.cloud.ai.lynxe.runtime.service.ServiceGroupIndexService;
import com.alibaba.cloud.ai.lynxe.runtime.service.TaskInterruptionManager;
import com.alibaba.cloud.ai.lynxe.subplan.service.SubplanToolService;
import com.alibaba.cloud.ai.lynxe.tool.DebugTool;
import com.alibaba.cloud.ai.lynxe.tool.FormInputTool;
import com.alibaba.cloud.ai.lynxe.tool.TerminateTool;
import com.alibaba.cloud.ai.lynxe.tool.ThinkTool;
import com.alibaba.cloud.ai.lynxe.tool.ToolCallBiFunctionDef;
import com.alibaba.cloud.ai.lynxe.tool.bash.Bash;
import com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators.ClickBrowserTool;
import com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators.CloseTabBrowserTool;
import com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators.DownloadBrowserTool;
import com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators.GetWebContentBrowserTool;
import com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators.InputTextBrowserTool;
import com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators.KeyEnterBrowserTool;
import com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators.NavigateBrowserTool;
import com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators.NewTabBrowserTool;
import com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators.ScreenshotBrowserTool;
import com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators.SwitchTabBrowserTool;
import com.alibaba.cloud.ai.lynxe.tool.browser.service.BrowserUseCommonService;
import com.alibaba.cloud.ai.lynxe.tool.browser.service.ChromeDriverService;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.convertToMarkdown.ImageOcrProcessor;
import com.alibaba.cloud.ai.lynxe.tool.convertToMarkdown.MarkdownConverterTool;
import com.alibaba.cloud.ai.lynxe.tool.convertToMarkdown.PdfOcrProcessor;
import com.alibaba.cloud.ai.lynxe.tool.database.databaseOperators.DatabaseTableToExcelTool;
import com.alibaba.cloud.ai.lynxe.tool.database.databaseOperators.DatabaseWriteTool;
import com.alibaba.cloud.ai.lynxe.tool.database.databaseOperators.ExecuteReadSqlToJsonFileTool;
import com.alibaba.cloud.ai.lynxe.tool.database.databaseOperators.ExecuteReadSqlTool;
import com.alibaba.cloud.ai.lynxe.tool.database.databaseOperators.GetDatasourceInfoTool;
import com.alibaba.cloud.ai.lynxe.tool.database.databaseOperators.GetTableMetaTool;
import com.alibaba.cloud.ai.lynxe.tool.database.databaseOperators.UuidGenerateTool;
import com.alibaba.cloud.ai.lynxe.tool.database.service.DataSourceService;
import com.alibaba.cloud.ai.lynxe.tool.dirOperator.dirOperators.GlobExternalLinkFilesTool;
import com.alibaba.cloud.ai.lynxe.tool.dirOperator.dirOperators.GlobFilesTool;
import com.alibaba.cloud.ai.lynxe.tool.dirOperator.dirOperators.ListExternalLinkFilesTool;
import com.alibaba.cloud.ai.lynxe.tool.dirOperator.dirOperators.ListFilesTool;
import com.alibaba.cloud.ai.lynxe.tool.excelProcessor.IExcelProcessingService;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.GitIgnoreMatcher;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.SmartContentSavingService;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.SymbolicLinkDetector;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.TextFileService;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.UnifiedDirectoryManager;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.alibaba.cloud.ai.lynxe.tool.image.ImageGenerationProvider;
import com.alibaba.cloud.ai.lynxe.tool.image.ImageGenerationTool;
import com.alibaba.cloud.ai.lynxe.tool.jsxGenerator.JsxGeneratorOperator;
import com.alibaba.cloud.ai.lynxe.tool.mapreduce.FileBasedParallelExecutionTool;
import com.alibaba.cloud.ai.lynxe.tool.mapreduce.FunctionRegistryService;
import com.alibaba.cloud.ai.lynxe.tool.mapreduce.ParallelExecutionService;
import com.alibaba.cloud.ai.lynxe.tool.mapreduce.parallelOperators.ClearPendingExecutionTool;
import com.alibaba.cloud.ai.lynxe.tool.mapreduce.parallelOperators.RegisterBatchExecutionTool;
import com.alibaba.cloud.ai.lynxe.tool.mapreduce.parallelOperators.StartAsyncExecutionTool;
import com.alibaba.cloud.ai.lynxe.tool.mapreduce.parallelOperators.StartParallelExecutionTool;
import com.alibaba.cloud.ai.lynxe.tool.office.MarkdownToDocxTool;
import com.alibaba.cloud.ai.lynxe.tool.todo.TodoStorageService;
import com.alibaba.cloud.ai.lynxe.tool.todo.TodoWriteTool;
import com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators.CountExternalLinkFileTool;
import com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators.CountFileTool;
import com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators.DeleteExternalLinkFileOperator;
import com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators.DeleteFileOperator;
import com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators.EnhanceExternalLinkGrep;
import com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators.EnhancedGrep;
import com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators.ReadExternalLinkFileOperator;
import com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators.ReadFileOperator;
import com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators.ReplaceExternalLinkFileOperator;
import com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators.ReplaceFileOperator;
import com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators.SplitExternalLinkFileTool;
import com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators.SplitFileTool;
import com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators.WriteExternalLinkFileOperator;
import com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators.WriteFileOperator;
import com.alibaba.cloud.ai.lynxe.workspace.conversation.service.MemoryService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author yuluo
 * @author <a href="mailto:yuluo08290126@gmail.com">yuluo</a>
 */

@Service
public class PlanningFactory {

	private final ChromeDriverService chromeDriverService;

	private final PlanExecutionRecorder recorder;

	private final LynxeProperties lynxeProperties;

	private final TextFileService textFileService;

	private final SmartContentSavingService innerStorageService;

	private final UnifiedDirectoryManager unifiedDirectoryManager;

	private final DataSourceService dataSourceService;

	private final IExcelProcessingService excelProcessingService;

	private final static Logger log = LoggerFactory.getLogger(PlanningFactory.class);

	private final McpService mcpService;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	@Lazy
	private LlmService llmService;

	@Autowired
	@Lazy
	private ToolCallingManager toolCallingManager;

	@Autowired
	private StreamingResponseHandler streamingResponseHandler;

	@Autowired
	private SubplanToolService subplanToolService;

	@Autowired
	@Lazy
	private TaskInterruptionManager taskInterruptionManager;

	@Autowired
	private PlanIdDispatcher planIdDispatcher;

	@Value("${agent.init}")
	private Boolean agentInit = true;

	@SuppressWarnings("unused")
	@Autowired
	private JsxGeneratorOperator jsxGeneratorOperator;

	@SuppressWarnings("unused")
	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private MemoryService memoryService;

	@Autowired
	private com.alibaba.cloud.ai.lynxe.tool.shortUrl.ShortUrlService shortUrlService;

	@Autowired
	private ServiceGroupIndexService serviceGroupIndexService;

	@Autowired
	private ParallelExecutionService parallelExecutionService;

	@Autowired
	private BrowserUseCommonService browserUseCommonService;

	@Autowired
	private FunctionRegistryService functionRegistryService;

	@Autowired
	private ToolI18nService toolI18nService;

	@Autowired
	private SymbolicLinkDetector symlinkDetector;

	@Autowired
	private GitIgnoreMatcher gitIgnoreMatcher;

	@Autowired(required = false)
	private CodeAgentFileNotifier codeAgentFileNotifier;

	@Autowired
	private DynamicModelRepository dynamicModelRepository;

	@Autowired
	private ObjectProvider<RestClient.Builder> restClientBuilderProvider;

	@Autowired(required = false)
	private List<ImageGenerationProvider> imageGenerationProviders;

	@Autowired
	private TodoStorageService todoStorageService;

	public PlanningFactory(ChromeDriverService chromeDriverService, PlanExecutionRecorder recorder,
			LynxeProperties lynxeProperties, TextFileService textFileService, McpService mcpService,
			SmartContentSavingService innerStorageService, UnifiedDirectoryManager unifiedDirectoryManager,
			DataSourceService dataSourceService, IExcelProcessingService excelProcessingService) {
		this.chromeDriverService = chromeDriverService;
		this.recorder = recorder;
		this.lynxeProperties = lynxeProperties;
		this.textFileService = textFileService;
		this.mcpService = mcpService;
		this.innerStorageService = innerStorageService;
		this.unifiedDirectoryManager = unifiedDirectoryManager;
		this.dataSourceService = dataSourceService;
		this.excelProcessingService = excelProcessingService;
	}

	/**
	 * Create a PlanFinalizer instance
	 * @return configured PlanFinalizer instance
	 */
	public PlanFinalizer createPlanFinalizer() {
		return new PlanFinalizer(llmService, recorder, lynxeProperties, streamingResponseHandler,
				taskInterruptionManager, memoryService);
	}

	public static class ToolCallBackContext {

		private final ToolCallback toolCallback;

		private final ToolCallBiFunctionDef<?> functionInstance;

		public ToolCallBackContext(ToolCallback toolCallback, ToolCallBiFunctionDef<?> functionInstance) {
			this.toolCallback = toolCallback;
			this.functionInstance = functionInstance;
		}

		public ToolCallback getToolCallback() {
			return toolCallback;
		}

		public ToolCallBiFunctionDef<?> getFunctionInstance() {
			return functionInstance;
		}

	}

	/**
	 * Create tool callback map with all tools (original behavior).
	 * Delegates to the overloaded method with null selectedToolKeys.
	 */
	public Map<String, ToolCallBackContext> toolCallbackMap(String planId, String rootPlanId,
			String expectedReturnInfo) {
		return toolCallbackMap(planId, rootPlanId, expectedReturnInfo, null);
	}

	/**
	 * Create tool callback map with optional selective tool loading.
	 * When selectedToolKeys is non-empty, only creates matching tools + TerminateTool,
	 * skipping MCP and subplan loading for faster initialization.
	 *
	 * @param planId Current plan ID
	 * @param rootPlanId Root plan ID
	 * @param expectedReturnInfo Expected return info for TerminateTool
	 * @param selectedToolKeys Optional list of tool keys to selectively load. When null/empty, all tools are created.
	 */
	public Map<String, ToolCallBackContext> toolCallbackMap(String planId, String rootPlanId,
			String expectedReturnInfo, List<String> selectedToolKeys) {

		long t0 = System.nanoTime();

		Map<String, ToolCallBackContext> toolCallbackMap = new HashMap<>();
		List<ToolCallBiFunctionDef<?>> toolDefinitions = new ArrayList<>();
		if (chromeDriverService == null) {
			log.error("ChromeDriverService is null, skipping BrowserUseTool registration");
			return toolCallbackMap;
		}
		if (innerStorageService == null) {
			log.error("SmartContentSavingService is null, skipping BrowserUseTool registration");
			return toolCallbackMap;
		}

		boolean selectiveMode = selectedToolKeys != null && !selectedToolKeys.isEmpty();
		Set<String> selectedSet = selectiveMode ? new HashSet<>(selectedToolKeys) : Collections.emptySet();

		if (agentInit) {
			if (!selectiveMode) {
				// Full mode: create all tool definitions (original logic)
				addAllToolDefinitions(toolDefinitions, planId, expectedReturnInfo, toolCallbackMap);
			}
			else {
				// Selective mode: only create matching tools + TerminateTool
				addSelectedToolDefinitions(toolDefinitions, selectedSet, planId, expectedReturnInfo, toolCallbackMap);
			}
		}
		else {
			toolDefinitions.add(new TerminateTool(planId, expectedReturnInfo, objectMapper, shortUrlService,
					lynxeProperties, toolI18nService));
		}

		log.info("[PlanningFactory Timing] Tool definitions created ({} tools, selective={}): {}ms",
				toolDefinitions.size(), selectiveMode, (System.nanoTime() - t0) / 1_000_000);

		// MCP: only load in non-selective mode
		if (!selectiveMode) {
			List<McpServiceEntity> functionCallbacks = mcpService.getFunctionCallbacks(planId);
			for (McpServiceEntity toolCallback : functionCallbacks) {
				String serviceGroup = toolCallback.getServiceGroup();
				ToolCallback[] tCallbacks = toolCallback.getAsyncMcpToolCallbackProvider().getToolCallbacks();
				for (ToolCallback tCallback : tCallbacks) {
					toolDefinitions.add(new McpTool(tCallback, serviceGroup, planId, innerStorageService, objectMapper));
				}
			}
			log.info("[PlanningFactory Timing] MCP tools loaded: {}ms", (System.nanoTime() - t0) / 1_000_000);
		}
		else {
			log.info("[PlanningFactory Timing] MCP tools skipped (selective mode): {}ms",
					(System.nanoTime() - t0) / 1_000_000);
		}

		// Create FunctionToolCallback for each tool
		for (ToolCallBiFunctionDef<?> toolDefinition : toolDefinitions) {

			try {
				toolDefinition.setCurrentPlanId(planId);
				toolDefinition.setRootPlanId(rootPlanId);

				// Use qualified key format: serviceGroup-toolName
				String serviceGroup = toolDefinition.getServiceGroup();
				String toolName = toolDefinition.getName();
				String qualifiedKey;

				if (serviceGroup != null && !serviceGroup.isEmpty()) {
					qualifiedKey = serviceGroup + "-" + toolName;
				}
				else {
					qualifiedKey = toolName;
				}

				// Build FunctionToolCallback with qualified name so LLM calls tools with
				// qualified names
				FunctionToolCallback<?, ToolExecuteResult> functionToolcallback = FunctionToolCallback
					.builder(qualifiedKey, toolDefinition)
					.description(toolDefinition.getDescriptionWithServiceGroup())
					.inputSchema(toolDefinition.getParameters())
					.inputType(toolDefinition.getInputType())
					.toolMetadata(ToolMetadata.builder().returnDirect(toolDefinition.isReturnDirect()).build())
					.build();

				log.info("Registering tool: {} with qualified key: {}", toolName, qualifiedKey);
				ToolCallBackContext functionToolcallbackContext = new ToolCallBackContext(functionToolcallback,
						toolDefinition);
				toolCallbackMap.put(qualifiedKey, functionToolcallbackContext);
			}
			catch (Exception e) {
				log.error("Failed to register tool: {} - {}", toolDefinition.getName(), e.getMessage(), e);
			}
		}

		// Subplan: only register in non-selective mode
		if (!selectiveMode && subplanToolService != null) {
			try {
				Map<String, ToolCallBackContext> subplanToolCallbacks = subplanToolService
					.createSubplanToolCallbacks(planId, rootPlanId, expectedReturnInfo, serviceGroupIndexService);
				toolCallbackMap.putAll(subplanToolCallbacks);
				log.info("Registered {} subplan tools", subplanToolCallbacks.size());
			}
			catch (Exception e) {
				log.warn("Failed to register subplan tools: {}", e.getMessage());
			}
		}
		else if (selectiveMode) {
			log.info("[PlanningFactory Timing] Subplan tools skipped (selective mode)");
		}

		log.info("[PlanningFactory Timing] All tools registered ({} total, selective={}): {}ms",
				toolCallbackMap.size(), selectiveMode, (System.nanoTime() - t0) / 1_000_000);

		return toolCallbackMap;
	}

	/**
	 * Add all tool definitions (original full-creation logic).
	 */
	private void addAllToolDefinitions(List<ToolCallBiFunctionDef<?>> toolDefinitions,
			String planId, String expectedReturnInfo,
			Map<String, ToolCallBackContext> toolCallbackMap) {
		// Refactored browser tools (split from BrowserUseTool)
		toolDefinitions.add(new NavigateBrowserTool(browserUseCommonService, toolI18nService));
		toolDefinitions.add(new ClickBrowserTool(browserUseCommonService, toolI18nService));
		toolDefinitions.add(new InputTextBrowserTool(browserUseCommonService, toolI18nService));
		toolDefinitions.add(new KeyEnterBrowserTool(browserUseCommonService, toolI18nService));
		toolDefinitions.add(new ScreenshotBrowserTool(browserUseCommonService, toolI18nService));
		toolDefinitions.add(new NewTabBrowserTool(browserUseCommonService, toolI18nService));
		toolDefinitions.add(new CloseTabBrowserTool(browserUseCommonService, toolI18nService));
		toolDefinitions.add(new SwitchTabBrowserTool(browserUseCommonService, toolI18nService));
		toolDefinitions
			.add(new GetWebContentBrowserTool(browserUseCommonService, textFileService, toolI18nService));
		toolDefinitions
			.add(new DownloadBrowserTool(browserUseCommonService, unifiedDirectoryManager, toolI18nService));
		// Refactored database read tools (split from DatabaseReadTool)
		toolDefinitions.add(new ExecuteReadSqlTool(dataSourceService, toolI18nService));
		toolDefinitions.add(new ExecuteReadSqlToJsonFileTool(dataSourceService, unifiedDirectoryManager,
				objectMapper, toolI18nService));
		toolDefinitions.add(DatabaseWriteTool.getInstance(dataSourceService, objectMapper, toolI18nService));
		toolDefinitions.add(GetTableMetaTool.getInstance(dataSourceService, objectMapper, toolI18nService));
		toolDefinitions.add(GetDatasourceInfoTool.getInstance(dataSourceService, objectMapper, toolI18nService));
		toolDefinitions.add(DatabaseTableToExcelTool.getInstance(lynxeProperties, dataSourceService,
				excelProcessingService, unifiedDirectoryManager, toolI18nService));
		toolDefinitions.add(UuidGenerateTool.getInstance(objectMapper, toolI18nService));
		toolDefinitions.add(new TerminateTool(planId, expectedReturnInfo, objectMapper, shortUrlService,
				lynxeProperties, toolI18nService));
		toolDefinitions.add(new DebugTool(toolI18nService));
		toolDefinitions.add(new ThinkTool());
		toolDefinitions.add(new Bash(unifiedDirectoryManager, objectMapper, toolI18nService, innerStorageService));

		// Refactored file operators
		toolDefinitions
			.add(new ReadFileOperator(textFileService, innerStorageService, shortUrlService, toolI18nService));
		toolDefinitions
			.add(new DeleteFileOperator(textFileService, innerStorageService, shortUrlService, toolI18nService));
		toolDefinitions
			.add(new ReplaceFileOperator(textFileService, innerStorageService, shortUrlService, toolI18nService));
		WriteFileOperator writeFileOp = new WriteFileOperator(textFileService, innerStorageService,
				shortUrlService, toolI18nService);
		if (codeAgentFileNotifier != null) {
			writeFileOp.setFileNotifier(codeAgentFileNotifier);
		}
		toolDefinitions.add(writeFileOp);
		// External link file operators
		toolDefinitions.add(new ReadExternalLinkFileOperator(textFileService, innerStorageService, shortUrlService,
				toolI18nService));
		toolDefinitions.add(new WriteExternalLinkFileOperator(textFileService, innerStorageService, shortUrlService,
				toolI18nService));
		toolDefinitions.add(new DeleteExternalLinkFileOperator(textFileService, innerStorageService,
				shortUrlService, toolI18nService));
		toolDefinitions.add(new ReplaceExternalLinkFileOperator(textFileService, innerStorageService,
				shortUrlService, toolI18nService));
		// External link file tools (split, count, list, grep, glob)
		toolDefinitions.add(new SplitExternalLinkFileTool(textFileService, toolI18nService));
		toolDefinitions.add(new CountExternalLinkFileTool(textFileService, toolI18nService));
		toolDefinitions.add(new ListExternalLinkFilesTool(unifiedDirectoryManager, toolI18nService));
		toolDefinitions.add(new GlobExternalLinkFilesTool(unifiedDirectoryManager, symlinkDetector, toolI18nService,
				gitIgnoreMatcher, lynxeProperties));
		toolDefinitions
			.add(new EnhanceExternalLinkGrep(textFileService, toolI18nService, gitIgnoreMatcher, lynxeProperties));
		toolDefinitions.add(new EnhancedGrep(textFileService, toolI18nService, gitIgnoreMatcher, lynxeProperties));
		// Refactored file splitter (split action only, count removed)
		toolDefinitions.add(new SplitFileTool(textFileService, toolI18nService));
		// File count tool for counting lines and characters
		toolDefinitions.add(new CountFileTool(textFileService, toolI18nService));
		// Refactored directory operators (split from DirectoryOperator)
		toolDefinitions.add(new ListFilesTool(unifiedDirectoryManager, toolI18nService));
		toolDefinitions.add(new GlobFilesTool(unifiedDirectoryManager, symlinkDetector, toolI18nService,
				gitIgnoreMatcher, lynxeProperties));
		toolDefinitions.add(new FormInputTool(objectMapper, toolI18nService));
		// Refactored parallel execution operators (split from ParallelExecutionTool)
		toolDefinitions.add(new RegisterBatchExecutionTool(objectMapper, planIdDispatcher, functionRegistryService,
				toolI18nService));
		StartParallelExecutionTool startParallelExecutionTool = new StartParallelExecutionTool(objectMapper,
				toolCallbackMap, functionRegistryService, parallelExecutionService, toolI18nService);
		toolDefinitions.add(startParallelExecutionTool);
		StartAsyncExecutionTool startAsyncExecutionTool = new StartAsyncExecutionTool(objectMapper, toolCallbackMap,
				functionRegistryService, parallelExecutionService, toolI18nService);
		toolDefinitions.add(startAsyncExecutionTool);
		toolDefinitions.add(new ClearPendingExecutionTool(objectMapper, functionRegistryService, toolI18nService));
		toolDefinitions.add(new FileBasedParallelExecutionTool(objectMapper, toolCallbackMap,
				unifiedDirectoryManager, parallelExecutionService, toolI18nService));
		toolDefinitions.add(new MarkdownConverterTool(unifiedDirectoryManager,
				new PdfOcrProcessor(unifiedDirectoryManager, llmService, lynxeProperties,
						new ImageRecognitionExecutorPool(lynxeProperties)),
				new ImageOcrProcessor(unifiedDirectoryManager, llmService, lynxeProperties,
						new ImageRecognitionExecutorPool(lynxeProperties)),
				excelProcessingService, objectMapper, toolI18nService));
		toolDefinitions.add(new MarkdownToDocxTool(textFileService, unifiedDirectoryManager, toolI18nService));
		toolDefinitions.add(new ImageGenerationTool(dynamicModelRepository, restClientBuilderProvider, objectMapper,
				toolI18nService, lynxeProperties, imageGenerationProviders));
		// Todo management tool
		toolDefinitions.add(new TodoWriteTool(todoStorageService, objectMapper, toolI18nService));
		toolDefinitions.add(new OssClientTool(unifiedDirectoryManager, objectMapper, toolI18nService));
		toolDefinitions.add(new ServiceOperateTool());
		toolDefinitions.add(new HRTool());
	}

	/**
	 * Add only selected tool definitions based on selectedSet, plus TerminateTool always.
	 * Matches by tool name or serviceGroup-toolName format.
	 */
	private void addSelectedToolDefinitions(List<ToolCallBiFunctionDef<?>> toolDefinitions,
			Set<String> selectedSet, String planId, String expectedReturnInfo,
			Map<String, ToolCallBackContext> toolCallbackMap) {

		// Always add TerminateTool
		toolDefinitions.add(new TerminateTool(planId, expectedReturnInfo, objectMapper, shortUrlService,
				lynxeProperties, toolI18nService));

		// Build a list of all candidate tools, then filter
		List<ToolCallBiFunctionDef<?>> allCandidates = new ArrayList<>();

		// Browser tools
		allCandidates.add(new NavigateBrowserTool(browserUseCommonService, toolI18nService));
		allCandidates.add(new ClickBrowserTool(browserUseCommonService, toolI18nService));
		allCandidates.add(new InputTextBrowserTool(browserUseCommonService, toolI18nService));
		allCandidates.add(new KeyEnterBrowserTool(browserUseCommonService, toolI18nService));
		allCandidates.add(new ScreenshotBrowserTool(browserUseCommonService, toolI18nService));
		allCandidates.add(new NewTabBrowserTool(browserUseCommonService, toolI18nService));
		allCandidates.add(new CloseTabBrowserTool(browserUseCommonService, toolI18nService));
		allCandidates.add(new SwitchTabBrowserTool(browserUseCommonService, toolI18nService));
		allCandidates.add(new GetWebContentBrowserTool(browserUseCommonService, textFileService, toolI18nService));
		allCandidates.add(new DownloadBrowserTool(browserUseCommonService, unifiedDirectoryManager, toolI18nService));

		// Database tools
		allCandidates.add(new ExecuteReadSqlTool(dataSourceService, toolI18nService));
		allCandidates.add(new ExecuteReadSqlToJsonFileTool(dataSourceService, unifiedDirectoryManager,
				objectMapper, toolI18nService));
		allCandidates.add(DatabaseWriteTool.getInstance(dataSourceService, objectMapper, toolI18nService));
		allCandidates.add(GetTableMetaTool.getInstance(dataSourceService, objectMapper, toolI18nService));
		allCandidates.add(GetDatasourceInfoTool.getInstance(dataSourceService, objectMapper, toolI18nService));
		allCandidates.add(DatabaseTableToExcelTool.getInstance(lynxeProperties, dataSourceService,
				excelProcessingService, unifiedDirectoryManager, toolI18nService));
		allCandidates.add(UuidGenerateTool.getInstance(objectMapper, toolI18nService));

		// Core tools
		allCandidates.add(new DebugTool(toolI18nService));
		allCandidates.add(new ThinkTool());
		allCandidates.add(new Bash(unifiedDirectoryManager, objectMapper, toolI18nService, innerStorageService));

		// File operators
		allCandidates.add(new ReadFileOperator(textFileService, innerStorageService, shortUrlService, toolI18nService));
		allCandidates.add(new DeleteFileOperator(textFileService, innerStorageService, shortUrlService, toolI18nService));
		allCandidates.add(new ReplaceFileOperator(textFileService, innerStorageService, shortUrlService, toolI18nService));
		WriteFileOperator writeFileOp = new WriteFileOperator(textFileService, innerStorageService,
				shortUrlService, toolI18nService);
		if (codeAgentFileNotifier != null) {
			writeFileOp.setFileNotifier(codeAgentFileNotifier);
		}
		allCandidates.add(writeFileOp);

		// External link file operators
		allCandidates.add(new ReadExternalLinkFileOperator(textFileService, innerStorageService, shortUrlService,
				toolI18nService));
		allCandidates.add(new WriteExternalLinkFileOperator(textFileService, innerStorageService, shortUrlService,
				toolI18nService));
		allCandidates.add(new DeleteExternalLinkFileOperator(textFileService, innerStorageService,
				shortUrlService, toolI18nService));
		allCandidates.add(new ReplaceExternalLinkFileOperator(textFileService, innerStorageService,
				shortUrlService, toolI18nService));

		// External link file tools
		allCandidates.add(new SplitExternalLinkFileTool(textFileService, toolI18nService));
		allCandidates.add(new CountExternalLinkFileTool(textFileService, toolI18nService));
		allCandidates.add(new ListExternalLinkFilesTool(unifiedDirectoryManager, toolI18nService));
		allCandidates.add(new GlobExternalLinkFilesTool(unifiedDirectoryManager, symlinkDetector, toolI18nService,
				gitIgnoreMatcher, lynxeProperties));
		allCandidates.add(new EnhanceExternalLinkGrep(textFileService, toolI18nService, gitIgnoreMatcher, lynxeProperties));
		allCandidates.add(new EnhancedGrep(textFileService, toolI18nService, gitIgnoreMatcher, lynxeProperties));

		// File splitter and count
		allCandidates.add(new SplitFileTool(textFileService, toolI18nService));
		allCandidates.add(new CountFileTool(textFileService, toolI18nService));

		// Directory operators
		allCandidates.add(new ListFilesTool(unifiedDirectoryManager, toolI18nService));
		allCandidates.add(new GlobFilesTool(unifiedDirectoryManager, symlinkDetector, toolI18nService,
				gitIgnoreMatcher, lynxeProperties));

		// Form input
		allCandidates.add(new FormInputTool(objectMapper, toolI18nService));

		// Parallel execution
		allCandidates.add(new RegisterBatchExecutionTool(objectMapper, planIdDispatcher, functionRegistryService,
				toolI18nService));
		allCandidates.add(new StartParallelExecutionTool(objectMapper, toolCallbackMap, functionRegistryService,
				parallelExecutionService, toolI18nService));
		allCandidates.add(new StartAsyncExecutionTool(objectMapper, toolCallbackMap, functionRegistryService,
				parallelExecutionService, toolI18nService));
		allCandidates.add(new ClearPendingExecutionTool(objectMapper, functionRegistryService, toolI18nService));
		allCandidates.add(new FileBasedParallelExecutionTool(objectMapper, toolCallbackMap,
				unifiedDirectoryManager, parallelExecutionService, toolI18nService));

		// Converters
		allCandidates.add(new MarkdownConverterTool(unifiedDirectoryManager,
				new PdfOcrProcessor(unifiedDirectoryManager, llmService, lynxeProperties,
						new ImageRecognitionExecutorPool(lynxeProperties)),
				new ImageOcrProcessor(unifiedDirectoryManager, llmService, lynxeProperties,
						new ImageRecognitionExecutorPool(lynxeProperties)),
				excelProcessingService, objectMapper, toolI18nService));
		allCandidates.add(new MarkdownToDocxTool(textFileService, unifiedDirectoryManager, toolI18nService));
		allCandidates.add(new ImageGenerationTool(dynamicModelRepository, restClientBuilderProvider, objectMapper,
				toolI18nService, lynxeProperties, imageGenerationProviders));

		// Todo, OSS, DevOps, HR
		allCandidates.add(new TodoWriteTool(todoStorageService, objectMapper, toolI18nService));
		allCandidates.add(new OssClientTool(unifiedDirectoryManager, objectMapper, toolI18nService));
		allCandidates.add(new ServiceOperateTool());
		allCandidates.add(new HRTool());

		// Filter: only keep tools whose name or serviceGroup-name matches selectedSet
		for (ToolCallBiFunctionDef<?> candidate : allCandidates) {
			String toolName = candidate.getName();
			String serviceGroup = candidate.getServiceGroup();
			String qualifiedName = (serviceGroup != null && !serviceGroup.isEmpty())
					? serviceGroup + "-" + toolName : toolName;

			if (selectedSet.contains(toolName) || selectedSet.contains(qualifiedName)) {
				toolDefinitions.add(candidate);
				log.debug("Selective mode: including tool '{}'", qualifiedName);
			}
		}

		log.info("Selective tool loading: requested {} keys, created {} tools (+ TerminateTool)",
				selectedSet.size(), toolDefinitions.size() - 1);
	}

	@SuppressWarnings("deprecation")
	@Bean
	public RestClient.Builder createRestClient() {
		// Create RequestConfig and set the timeout (10 minutes for all timeouts)
		RequestConfig requestConfig = RequestConfig.custom()
			.setConnectTimeout(Timeout.of(10, TimeUnit.MINUTES)) // Set the connection
			// timeout
			.setResponseTimeout(Timeout.of(10, TimeUnit.MINUTES))
			.setConnectionRequestTimeout(Timeout.of(10, TimeUnit.MINUTES))
			.build();

		// Create CloseableHttpClient and apply the configuration
		HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build();

		// Use HttpComponentsClientHttpRequestFactory to wrap HttpClient
		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

		// Create RestClient and set the request factory
		return RestClient.builder().requestFactory(requestFactory);
	}

	/**
	 * Provides an empty ToolCallbackProvider implementation when MCP is disabled
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(name = "spring.ai.mcp.client.enabled", havingValue = "false")
	public ToolCallbackProvider emptyToolCallbackProvider() {
		return () -> new HashMap<String, ToolCallBackContext>();
	}

}
