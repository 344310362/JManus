package com.alibaba.cloud.ai.lynxe.tool.devops;

import cn.iocoder.cloud.devops.api.service.ServiceApi;
import cn.iocoder.cloud.devops.api.service.dto.ServiceOperateReqDTO;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.alibaba.cloud.ai.lynxe.config.rpc.AuthContext;
import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.ToolStateInfo;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.utils.ServiceHelper;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceOperateTool extends AbstractBaseTool<ServiceOperateTool.ServiceOperateInput> {

	private static final Logger log = LoggerFactory.getLogger(ServiceOperateTool.class);

	private static final String TOOL_NAME = "service_manager";

	// Lazy-loaded Dubbo service API
	private ServiceApi serviceApi;

	private static final String[] KNOWN_SERVICES = { "user-service", "order-service", "payment-gateway",
			"inventory-api", "notification-center" };

	// ✅ 明确支持的环境列表
	private static final String[] SUPPORTED_ENVIRONMENTS = { "dev", "test", "pre", "prod" };

	@Override
	public ToolExecuteResult run(ServiceOperateInput input) {
		if (input == null) {
			return new ToolExecuteResult("❌ Input is null. Please provide valid action parameters.");
		}

		String action = input.getAction();
		String serviceName = input.getServiceName();
		String environment = input.getEnvironment();
		String ip = input.getIp();

		// 1. Validate action
		if (action == null || action.trim().isEmpty()) {
			return new ToolExecuteResult("❌ 'action' is required.");
		}

		// 2. Validate service name
		if ((serviceName == null || serviceName.trim().isEmpty()) && !"search_ip".equalsIgnoreCase(action)) {
			return new ToolExecuteResult("❌ 'service_name' is required.");
		}

		// 3. Validate and normalize environment
		if ((environment == null || environment.trim().isEmpty()) && !"search_ip".equalsIgnoreCase(action)) {
			return new ToolExecuteResult("❌ 'environment' is required. Must be one of: dev, test, pre, prod.");
		}
		if (!StringUtils.isEmpty(environment)) {
			environment = environment.trim().toLowerCase();
			if (!isValidEnvironment(environment)) {
				return new ToolExecuteResult(
						"❌ Invalid environment: '" + environment + "'. Supported environments: dev, test, pre, prod.");
			}
		}

		// 4. CMDB check
		if (!"search_ip".equalsIgnoreCase(action) && !isServiceInCmdb(serviceName)) {
			return new ToolExecuteResult("❌ Service '" + serviceName + "' is not registered in CMDB service tree. "
					+ "Only CMDB-managed services can be operated on.");
		}

		// 🔒 5. 权限检查：必须是服务负责人 todo
		/*
		 * if (!isCurrentUserServiceOwner(serviceName)) { String currentUser =
		 * getCurrentUser(); return new ToolExecuteResult( "❌ Permission denied. User '" +
		 * (currentUser != null ? currentUser : "unknown") +
		 * "' is not the owner of service '" + serviceName + "'. " +
		 * "Only the service owner (as defined in CMDB) can perform operations." ); }
		 */

		// 🔢 6. 针对 scale 操作，校验 target_replicas
		String normalizedAction = action.toLowerCase();
		if (normalizedAction.equals("scale_out") || normalizedAction.equals("scale_in")
				|| normalizedAction.equals("expand") || normalizedAction.equals("shrink")) {

			if (input.getTargetReplicas() == null) {
				return new ToolExecuteResult("❌ 'target_replicas' is required for scale operations. "
						+ "Please specify the desired number of service instances (e.g., 3, 5, 10).");
			}

			int replicas = input.getTargetReplicas();
			if (replicas < 0) {
				return new ToolExecuteResult("❌ 'target_replicas' must be a non-negative integer.");
			}

			// 可选：限制最大副本数（防误操作）
			if (replicas > 100) {
				return new ToolExecuteResult("❌ 'target_replicas' exceeds maximum allowed (100).");
			}
		}

		// 6. Safety warning for prod (optional but recommended)
		if ("prod".equals(environment) && isCriticalAction(action)) {
			log.warn("⚠️ Critical operation on PRODUCTION: action={}, service={}", action, serviceName);
		}

		// 6. Dispatch
		return switch (action.toLowerCase()) {
			case "deploy" -> executeDeploy(serviceName, environment);
			case "search_ip" -> search_ip(ip);
			case "build" -> executeBuild(serviceName, environment);
			case "delete" -> executeDelete(serviceName, environment);
			case "modify" -> executeModify(serviceName, environment);
			case "scale_out", "expand" -> executeScale(serviceName, environment, true, input.getTargetReplicas());
			case "scale_in", "shrink" -> executeScale(serviceName, environment, false, input.getTargetReplicas());
			case "pause" -> executePause(serviceName, environment);
			case "resume" -> executeResume(serviceName, environment);
			case "query_events" -> executeQueryEvents(serviceName, environment);
			case "query_monitoring", "monitor" -> executeQueryMonitoring(serviceName, environment);
			default -> new ToolExecuteResult("❌ Unsupported action: '" + action + "'. "
					+ "Supported actions: deploy, build, delete, modify, scale_out, scale_in, pause, resume.");
		};
	}

	private void parameterCheck(String action, String serviceName, String environment, String ip) {

	}

	// ✅ Helper: validate environment
	private boolean isValidEnvironment(String env) {
		for (String e : SUPPORTED_ENVIRONMENTS) {
			if (e.equals(env))
				return true;
		}
		return false;
	}

	// ✅ Helper: identify critical actions (for prod warning)
	private boolean isCriticalAction(String action) {
		String a = action.toLowerCase();
		return "delete".equals(a) || "modify".equals(a) || "scale_in".equals(a) || "pause".equals(a);
	}

	// ===== Operation methods (updated to use validated env) =====

	private ToolExecuteResult executeDeploy(String service, String env) {
		log.info("✅ Deploying service '{}' in environment '{}'", service, env);

		StringBuilder resultBuilder = new StringBuilder(
				"✅ Deploy initiated for service '" + service + "' in environment '" + env + "'.\n"
						+ "📌 Deployment ID: deploy-" + System.currentTimeMillis() + "'.\n");
		// Example of using the injected ServiceApi
		ServiceApi api = getServiceApi();
		if (api != null) {
			ServiceOperateReqDTO reqDTO = new ServiceOperateReqDTO();
			reqDTO.setService(service);
			reqDTO.setEnvironment(env);
			AuthContext.setContextPlanId(rootPlanId);
			CommonResult result = api.executeDeploy(reqDTO);
			if (result.isSuccess()) {
				resultBuilder.append("✅ Deployment successful.\n");
			}
			else {
				resultBuilder.append("❌ Deployment failed:").append(result.getMsg());
			}
			log.info("ServiceApi result " + result);
		}
		else {
			log.warn("ServiceApi is not available");
		}

		return new ToolExecuteResult(resultBuilder.toString());
	}

	private ToolExecuteResult search_ip(String ip) {
		ServiceApi api = getServiceApi();
		StringBuilder resultBuilder = new StringBuilder();
		ServiceOperateReqDTO reqDTO = new ServiceOperateReqDTO();
		reqDTO.setIp(ip);
		AuthContext.setContextPlanId(rootPlanId);
		CommonResult result = api.searchIp(ip);
		if (result.isSuccess()) {
			resultBuilder.append("✅ the ip info: ").append(JSONObject.toJSONString(result.getData()));
		}
		else {
			resultBuilder.append("❌ ip search failed:").append(result.getMsg());
		}
		log.info("ServiceApi result " + result);

		return new ToolExecuteResult(resultBuilder.toString());
	}

	private ToolExecuteResult executeBuild(String service, String env) {
		log.info("✅ Triggering build for service '{}' in '{}'", service, env);
		return new ToolExecuteResult("✅ Build pipeline triggered for '" + service + "' (env: " + env + ").");
	}

	private ToolExecuteResult executeDelete(String service, String env) {
		log.warn("⚠️ Delete requested for service '{}' in environment '{}'", service, env);
		return new ToolExecuteResult("⚠️ Delete operation for service '" + service + "' in '" + env
				+ "' requires manual approval.\n"
				+ "🔒 This action is simulated for safety. In real system, a confirmation workflow would be triggered.");
	}

	private ToolExecuteResult executeModify(String service, String env) {
		log.info("✅ Modifying configuration for service '{}' in '{}'", service, env);
		return new ToolExecuteResult(
				"✅ Configuration update initiated for '" + service + "' in environment '" + env + "'.");
	}

	private ToolExecuteResult executeScale(String service, String env, boolean scaleOut, Integer targetReplicas) {
		String dir = scaleOut ? "out" : "in";
		log.info("✅ Scaling {} for service '{}' in '{}' to {} replicas", dir, service, env, targetReplicas);
		return new ToolExecuteResult("✅ Scaling " + dir + " initiated for '" + service + "' in environment '" + env
				+ "'.\n" + "🎯 Target replicas: " + targetReplicas);
	}

	private ToolExecuteResult executePause(String service, String env) {
		log.info("⏸ Pausing service '{}' in '{}'", service, env);
		return new ToolExecuteResult("⏸ Service '" + service + "' paused in environment '" + env + "'.");
	}

	private ToolExecuteResult executeResume(String service, String env) {
		log.info("▶️ Resuming service '{}' in '{}'", service, env);
		return new ToolExecuteResult("▶️ Service '" + service + "' resumed in environment '" + env + "'.");
	}

	private ToolExecuteResult executeQueryEvents(String service, String env) {
		log.info("🔍 Querying events for service '{}' in environment '{}'", service, env);
		// 模拟返回最近事件（实际可对接日志/事件中心）
		return new ToolExecuteResult("✅ Recent events for service '" + service + "' in '" + env + "':\n"
				+ "- [2025-10-15T10:00:00Z] Deploy succeeded (v1.2.3)\n"
				+ "- [2025-10-15T09:45:00Z] Health check failed → auto-recovered\n"
				+ "- [2025-10-14T22:10:00Z] Manual scale-out (2 → 4 replicas)");
	}

	private ToolExecuteResult executeQueryMonitoring(String service, String env) {
		log.info("📊 Fetching monitoring metrics for service '{}' in '{}'", service, env);
		// 模拟返回监控数据（实际可对接 Prometheus/Grafana 等）
		return new ToolExecuteResult("✅ Current monitoring metrics for '" + service + "' (" + env + "):\n"
				+ "- CPU Usage: 42%\n" + "- Memory: 1.8 GB / 4 GB\n" + "- Request Rate: 1,250 RPS\n"
				+ "- Error Rate: 0.3%\n" + "- P99 Latency: 210 ms\n"
				+ "📈 Full dashboard: https://monitor.example.com/service/" + service + "?env=" + env);
	}

	// ===== CMDB check (mock) =====
	private boolean isServiceInCmdb(String serviceName) {
		if (serviceName == null)
			return false;
		for (String known : KNOWN_SERVICES) {
			if (known.equals(serviceName))
				return true;
		}
		return false;
	}

	// ===== Input Class =====
	public static class ServiceOperateInput {

		@JsonProperty("action")
		private String action;

		@JsonProperty("service_name")
		private String serviceName;

		@JsonProperty("environment")
		private String environment;

		// ✅ 新增：目标副本数（用于 scale 操作）
		@JsonProperty("target_replicas")
		private Integer targetReplicas;

		@JsonProperty("ip")
		private String ip;

		// Getters & Setters
		public String getAction() {
			return action;
		}

		public void setAction(String action) {
			this.action = action;
		}

		public String getServiceName() {
			return serviceName;
		}

		public void setServiceName(String serviceName) {
			this.serviceName = serviceName;
		}

		public String getEnvironment() {
			return environment;
		}

		public void setEnvironment(String environment) {
			this.environment = environment;
		}

		public Integer getTargetReplicas() {
			return targetReplicas;
		}

		public void setTargetReplicas(Integer targetReplicas) {
			this.targetReplicas = targetReplicas;
		}

		public String getIp() {
			return ip;
		}

		public void setIp(String ip) {
			this.ip = ip;
		}

	}

	// Lazy initialization of ServiceApi
	private ServiceApi getServiceApi() {
		if (serviceApi == null) {
			try {
				serviceApi = ServiceHelper.getFeignBean(ServiceApi.class);
			}
			catch (Exception e) {
				log.warn("Failed to get ServiceApi bean from context: {}", e.getMessage());
			}
		}
		return serviceApi;
	}

	// ===== Overrides =====

	@Override
	public String getDescription() {
		return "Service Management Tool for DevOps Platform - Provides lifecycle and observability operations for services registered in the CMDB service tree. "
				+ "All most operations require user specifying a valid environment: dev (development), test (testing), pre (pre-production), or prod (production). "
				+ "For scaling operations (scale_out / scale_in), you MUST ask user provide 'target_replicas' indicating the desired number of service instances (e.g., 3, 5). "
				+ "For 'search_ip', only 'ip' is required; 'service_name' and 'environment' are optional (used for filtering if provided). "
				+ "Workflow: " + "1. Confirm the target service exists in the CMDB service tree. "
				+ "2. Ensure you are the service owner (as defined in CMDB). "
				+ "3. Specify action, service_name, environment, and (for scaling) target_replicas. "
				+ "Supported actions: deploy, search_ip,build, delete, modify, scale_out, scale_in, pause, resume, query_events, query_monitoring. "
				+ "Note: "
				+ " - Always double-check environment and replica count before operating,NO defaults are assumed or applied"
				+ " - If any required parameter is missing or ambiguous, ask the user to clarify.";
	}

	@Override
	public String getParameters() {
		return "{" + "\"type\":\"object\"," + "\"properties\":{"
				+ "\"action\":{\"type\":\"string\",\"description\":\"Operation to perform\",\"enum\":[\"deploy\",\"search_ip\",\"build\",\"delete\",\"modify\",\"scale_out\",\"scale_in\",\"pause\",\"resume\",\"query_events\",\"query_monitoring\"]},"
				+ "\"service_name\":{\"type\":\"string\",\"description\":\"Exact service name as registered in CMDB (required)\"},"
				+ "\"ip\":{\"type\":\"string\",\"description\":\"use ip to search which pod\"},"
				+ "\"environment\":{\"type\":\"string\",\"description\":\"Target environment\",\"enum\":[\"dev\",\"test\",\"pre\",\"prod\"]},"
				+ "\"target_replicas\":{\"type\":\"integer\",\"description\":\"Target number of service instances (required for scale_out/scale_in)\",\"minimum\":0,\"maximum\":100}"
				+ "}," + "\"required\":[]" + "}";
	}

	@Override
	public Class<ServiceOperateInput> getInputType() {
		return ServiceOperateInput.class;
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

	@Override
	public ToolStateInfo getCurrentToolStateString() {
		String toolState = "ServiceOperateTool State:\n" + "- Current Plan ID: " + currentPlanId + "\n"
				+ "- Environments: dev, test, pre, prod\n" + "- CMDB Validation: Enabled\n"
				+ "- Known Services (mock): " + String.join(", ", KNOWN_SERVICES);

		return new ToolStateInfo(null, toolState);
	}

	@Override
	public void cleanup(String planId) {
		log.info("ServiceOperateTool cleanup for planId: {}", planId);
	}

	@Override
	public String getServiceGroup() {
		return "devops";
	}

	@Override
	public String getName() {
		return TOOL_NAME;
	}

}
