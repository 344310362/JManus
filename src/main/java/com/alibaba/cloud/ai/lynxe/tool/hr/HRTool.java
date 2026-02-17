package com.alibaba.cloud.ai.lynxe.tool.hr;

import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.ToolStateInfo;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class HRTool extends AbstractBaseTool<HRTool.HrToolInput> {

	private static final Logger log = LoggerFactory.getLogger(HRTool.class);

	private static final String TOOL_NAME = "hr_assistant";

	// ===== 模拟数据 =====

	private static final Map<String, Employee> MOCK_EMPLOYEES = new HashMap<>();

	private static final List<RecruitmentRequest> MOCK_RECRUITMENT_REQUESTS = new ArrayList<>();

	private static final Map<String, List<String>> ONBOARDING_GUIDES = new HashMap<>();

	static {
		// 初始化员工
		MOCK_EMPLOYEES.put("E1001", new Employee("E1001", "张三", "研发部", "高级Java工程师", "zhangsan@company.com", "active"));
		MOCK_EMPLOYEES.put("E1002", new Employee("E1002", "李四", "HR部", "HRBP", "lisi@company.com", "active"));
		MOCK_EMPLOYEES.put("E1003", new Employee("E1003", "王五", "产品部", "产品经理", "wangwu@company.com", "active"));

		// 初始化招聘需求
		MOCK_RECRUITMENT_REQUESTS.add(new RecruitmentRequest("REQ-001", "研发部", "后端工程师", 2, "业务扩张", "approved"));
		MOCK_RECRUITMENT_REQUESTS.add(new RecruitmentRequest("REQ-002", "产品部", "产品经理", 1, "人员离职补充", "in_review"));

		// 初始化入职指南
		ONBOARDING_GUIDES.put("default",
				Arrays.asList("1. 签署劳动合同", "2. 办理入职手续", "3. 领取办公设备", "4. 加入公司通讯群", "5. 完成新人培训"));
		ONBOARDING_GUIDES.put("研发部", Arrays.asList("1. 签署劳动合同与保密协议", "2. 领取 MacBook / 高配PC",
				"3. 配置 Git / Jenkins / 内部系统权限", "4. 加入研发钉钉群 & 代码库", "5. 完成安全与编码规范培训"));
		ONBOARDING_GUIDES.put("产品部", Arrays.asList("1. 签署劳动合同", "2. 领取办公设备", "3. 配置 Jira / Confluence / 数据看板权限",
				"4. 参加产品方法论培训", "5. 对接业务方负责人"));
	}

	// ===== Action 枚举（用于校验）=====
	private static final Set<String> SUPPORTED_ACTIONS = Set.of("query_employee", "add_employee", "remove_employee",
			"get_onboarding_guide", "check_onboarding_status", "check_offboarding_status", "create_recruitment_request",
			"query_recruitment_requests", "query_recruitment_progress", "get_interview_schedule", "query_team_members",
			"query_org_chart", "find_hr_contact", "query_holiday_calendar");

	@Override
	public ToolExecuteResult run(HrToolInput input) {
		if (input == null) {
			return new ToolExecuteResult("❌ Input is null. Please provide valid HR operation parameters.");
		}

		String action = input.getAction();
		if (action == null || action.trim().isEmpty()) {
			return new ToolExecuteResult("❌ 'action' is required.");
		}
		action = action.trim().toLowerCase();

		if (!SUPPORTED_ACTIONS.contains(action)) {
			return new ToolExecuteResult("❌ Unsupported action: '" + action + "'. " + "Supported actions: "
					+ String.join(", ", SUPPORTED_ACTIONS));
		}

		// 根据 action 分发
		return switch (action) {
			case "query_employee" -> handleQueryEmployee(input);
			case "add_employee" -> handleAddEmployee(input);
			case "remove_employee" -> handleRemoveEmployee(input);
			case "get_onboarding_guide" -> handleGetOnboardingGuide(input);
			case "check_onboarding_status" -> handleCheckOnboardingStatus(input);
			case "check_offboarding_status" -> handleCheckOffboardingStatus(input);
			case "create_recruitment_request" -> handleCreateRecruitmentRequest(input);
			case "query_recruitment_requests" -> handleQueryRecruitmentRequests(input);
			case "query_recruitment_progress" -> handleQueryRecruitmentProgress(input);
			case "get_interview_schedule" -> handleGetInterviewSchedule(input);
			case "query_team_members" -> handleQueryTeamMembers(input);
			case "query_org_chart" -> handleQueryOrgChart();
			case "find_hr_contact" -> handleFindHrContact(input);
			case "query_holiday_calendar" -> handleQueryHolidayCalendar();
			default -> new ToolExecuteResult("❌ Unexpected action: " + action);
		};
	}

	// ===== Action Handlers =====

	private ToolExecuteResult handleQueryEmployee(HrToolInput input) {
		String id = input.getEmployeeId();
		String name = input.getName();

		if (id == null && name == null) {
			return new ToolExecuteResult("❌ Please provide 'employee_id' or 'name' to query employee.");
		}

		List<Employee> matches = new ArrayList<>();
		if (id != null) {
			Employee emp = MOCK_EMPLOYEES.get(id);
			if (emp != null)
				matches.add(emp);
		}
		else if (name != null) {
			matches = MOCK_EMPLOYEES.values().stream().filter(e -> e.name.contains(name)).collect(Collectors.toList());
		}

		if (matches.isEmpty()) {
			return new ToolExecuteResult("✅ No employee found matching your query.");
		}

		StringBuilder sb = new StringBuilder("✅ Found employee(s):\n");
		for (Employee e : matches) {
			sb.append("- ID: ")
				.append(e.id)
				.append(", Name: ")
				.append(e.name)
				.append(", Dept: ")
				.append(e.department)
				.append(", Position: ")
				.append(e.position)
				.append(", Email: ")
				.append(e.email)
				.append("\n");
		}
		return new ToolExecuteResult(sb.toString().trim());
	}

	private ToolExecuteResult handleAddEmployee(HrToolInput input) {
		String name = input.getName();
		String dept = input.getDepartment();
		String position = input.getPosition();
		String startDate = input.getStartDate();

		if (name == null || name.trim().isEmpty()) {
			return new ToolExecuteResult("❌ 'name' is required for adding an employee.");
		}
		if (dept == null || dept.trim().isEmpty()) {
			return new ToolExecuteResult("❌ 'department' is required.");
		}
		if (position == null || position.trim().isEmpty()) {
			return new ToolExecuteResult("❌ 'position' is required.");
		}
		if (startDate == null || startDate.trim().isEmpty()) {
			return new ToolExecuteResult("❌ 'start_date' (YYYY-MM-DD) is required.");
		}

		// 模拟：生成草稿，不真实创建
		String newId = "E" + (MOCK_EMPLOYEES.size() + 1000);
		log.info("📝 Simulated add_employee: name={}, dept={}, position={}, start_date={}", name, dept, position,
				startDate);
		return new ToolExecuteResult("✅ Simulated employee addition draft created:\n" + "- Name: " + name + "\n"
				+ "- Department: " + dept + "\n" + "- Position: " + position + "\n" + "- Start Date: " + startDate
				+ "\n" + "- Proposed ID: " + newId + "\n"
				+ "🔒 This is a simulation. Actual onboarding requires HR approval and system entry.");
	}

	private ToolExecuteResult handleRemoveEmployee(HrToolInput input) {
		String id = input.getEmployeeId();
		String lastDay = input.getLastWorkingDay();

		if (id == null || id.trim().isEmpty()) {
			return new ToolExecuteResult("❌ 'employee_id' is required for offboarding.");
		}
		if (lastDay == null || lastDay.trim().isEmpty()) {
			return new ToolExecuteResult("❌ 'last_working_day' (YYYY-MM-DD) is required.");
		}

		Employee emp = MOCK_EMPLOYEES.get(id);
		if (emp == null) {
			return new ToolExecuteResult("❌ Employee with ID '" + id + "' not found.");
		}

		log.warn("📝 Simulated remove_employee: id={}, last_day={}", id, lastDay);
		return new ToolExecuteResult("⚠️ Simulated offboarding initiated for:\n" + "- Name: " + emp.name + "\n"
				+ "- ID: " + id + "\n" + "- Last Working Day: " + lastDay + "\n"
				+ "🔒 This is a simulation. Actual offboarding requires manager approval and IT/HR workflow.");
	}

	private ToolExecuteResult handleGetOnboardingGuide(HrToolInput input) {
		String dept = input.getDepartment();
		List<String> guide = ONBOARDING_GUIDES.getOrDefault(dept, ONBOARDING_GUIDES.get("default"));

		StringBuilder sb = new StringBuilder("✅ Onboarding Guide");
		if (dept != null)
			sb.append(" for ").append(dept);
		sb.append(":\n");
		for (int i = 0; i < guide.size(); i++) {
			sb.append(guide.get(i)).append("\n");
		}
		return new ToolExecuteResult(sb.toString().trim());
	}

	private ToolExecuteResult handleCheckOnboardingStatus(HrToolInput input) {
		String id = input.getEmployeeId();
		if (id == null) {
			return new ToolExecuteResult("❌ 'employee_id' is required.");
		}
		Employee emp = MOCK_EMPLOYEES.get(id);
		if (emp == null) {
			return new ToolExecuteResult("❌ Employee not found.");
		}
		// 模拟状态
		return new ToolExecuteResult("✅ Onboarding status for " + emp.name + " (" + id + "):\n"
				+ "- Contract Signed: ✅\n" + "- IT Equipment Assigned: ✅\n" + "- System Accounts Created: ✅\n"
				+ "- Orientation Completed: ⏳ In Progress\n" + "- Buddy Assigned: ✅ (李四 - E1002)");
	}

	private ToolExecuteResult handleCheckOffboardingStatus(HrToolInput input) {
		String id = input.getEmployeeId();
		if (id == null) {
			return new ToolExecuteResult("❌ 'employee_id' is required.");
		}
		Employee emp = MOCK_EMPLOYEES.get(id);
		if (emp == null) {
			return new ToolExecuteResult("❌ Employee not found.");
		}
		return new ToolExecuteResult("✅ Offboarding status for " + emp.name + " (" + id + "):\n"
				+ "- Exit Interview: ✅\n" + "- Asset Return: ⏳ Pending\n" + "- Access Revoked: ❌ Not Started\n"
				+ "- Final Payroll: ⏳ Scheduled");
	}

	private ToolExecuteResult handleCreateRecruitmentRequest(HrToolInput input) {
		String dept = input.getDepartment();
		String position = input.getPosition();
		Integer headcount = input.getHeadcount();
		String reason = input.getReason();

		if (dept == null || dept.isEmpty())
			return new ToolExecuteResult("❌ 'department' is required.");
		if (position == null || position.isEmpty())
			return new ToolExecuteResult("❌ 'position' is required.");
		if (headcount == null || headcount <= 0)
			return new ToolExecuteResult("❌ 'headcount' must be a positive integer.");
		if (reason == null || reason.isEmpty())
			return new ToolExecuteResult("❌ 'reason' is required.");

		String reqId = "REQ-" + (MOCK_RECRUITMENT_REQUESTS.size() + 100);
		log.info("📝 Simulated recruitment request: dept={}, pos={}, hc={}, reason={}", dept, position, headcount,
				reason);
		return new ToolExecuteResult("✅ Simulated recruitment request draft created:\n" + "- Request ID: " + reqId
				+ "\n" + "- Department: " + dept + "\n" + "- Position: " + position + "\n" + "- Headcount: " + headcount
				+ "\n" + "- Reason: " + reason + "\n"
				+ "🔒 This is a simulation. Actual request requires HRBP and finance approval.");
	}

	private ToolExecuteResult handleQueryRecruitmentRequests(HrToolInput input) {
		String dept = input.getDepartment();
		List<RecruitmentRequest> filtered = MOCK_RECRUITMENT_REQUESTS;
		if (dept != null && !dept.isEmpty()) {
			filtered = MOCK_RECRUITMENT_REQUESTS.stream()
				.filter(r -> r.department.equals(dept))
				.collect(Collectors.toList());
		}

		if (filtered.isEmpty()) {
			return new ToolExecuteResult("✅ No recruitment requests found.");
		}

		StringBuilder sb = new StringBuilder("✅ Recruitment Requests:\n");
		for (RecruitmentRequest r : filtered) {
			sb.append("- ID: ")
				.append(r.id)
				.append(", Dept: ")
				.append(r.department)
				.append(", Position: ")
				.append(r.position)
				.append(", HC: ")
				.append(r.headcount)
				.append(", Status: ")
				.append(r.status)
				.append("\n");
		}
		return new ToolExecuteResult(sb.toString().trim());
	}

	private ToolExecuteResult handleQueryRecruitmentProgress(HrToolInput input) {
		String dept = input.getDepartment();
		String position = input.getPosition();

		if (position == null || position.isEmpty()) {
			return new ToolExecuteResult("❌ 'position' is required to query recruitment progress.");
		}

		// 模拟进度
		return new ToolExecuteResult("✅ Recruitment progress for '" + position + "'"
				+ (dept != null ? " in " + dept : "") + ":\n" + "- Total Requests: 1\n" + "- Resumes Received: 42\n"
				+ "- Phone Screens: 15\n" + "- Tech Interviews: 8\n" + "- Final Interviews: 3\n"
				+ "- Offers Extended: 1\n" + "- Expected Hire Date: 2025-11-01");
	}

	private ToolExecuteResult handleGetInterviewSchedule(HrToolInput input) {
		String candidateId = input.getCandidateId();
		String position = input.getPosition();

		if (candidateId == null && position == null) {
			return new ToolExecuteResult("❌ Please provide 'candidate_id' or 'position'.");
		}

		return new ToolExecuteResult("✅ Interview schedule (simulated):\n" + "- Candidate: 张小明\n"
				+ "- Position: 后端工程师\n" + "- Round 1 (Tech): 2025-10-18 10:00 AM (Online)\n"
				+ "- Round 2 (System Design): 2025-10-20 2:00 PM (Onsite)\n"
				+ "- Round 3 (HR): 2025-10-22 11:00 AM (Online)");
	}

	private ToolExecuteResult handleQueryTeamMembers(HrToolInput input) {
		String dept = input.getDepartment();
		String team = input.getTeamName();

		if (dept == null && team == null) {
			return new ToolExecuteResult("❌ Please provide 'department' or 'team_name'.");
		}

		String target = dept != null ? dept : team;
		List<Employee> members = MOCK_EMPLOYEES.values()
			.stream()
			.filter(e -> e.department.equals(target))
			.collect(Collectors.toList());

		if (members.isEmpty()) {
			return new ToolExecuteResult("✅ No members found in " + target + ".");
		}

		StringBuilder sb = new StringBuilder("✅ Team members in " + target + ":\n");
		for (Employee e : members) {
			sb.append("- ").append(e.name).append(" (").append(e.position).append(")\n");
		}
		return new ToolExecuteResult(sb.toString().trim());
	}

	private ToolExecuteResult handleQueryOrgChart() {
		return new ToolExecuteResult("✅ Organizational Chart (simplified):\n" + "CEO\n" + "├── 研发部\n" + "│   ├── 后端团队\n"
				+ "│   └── 前端团队\n" + "├── 产品部\n" + "├── HR部\n" + "└── 财务部");
	}

	private ToolExecuteResult handleFindHrContact(HrToolInput input) {
		String dept = input.getDepartment();
		if (dept == null) {
			return new ToolExecuteResult("✅ General HR Contact: hr-support@company.com\n"
					+ "For department-specific HRBP, please provide 'department'.");
		}
		return new ToolExecuteResult("✅ HRBP for " + dept + ": 李四 (lisi@company.com, Ext: 8001)");
	}

	private ToolExecuteResult handleQueryHolidayCalendar() {
		return new ToolExecuteResult("✅ 2025 Company Holiday Calendar:\n" + "- 元旦: 2025-01-01\n"
				+ "- 春节: 2025-01-28 至 2025-02-03\n" + "- 清明节: 2025-04-04\n" + "- 劳动节: 2025-05-01 至 2025-05-03\n"
				+ "- 端午节: 2025-05-31\n" + "- 中秋节: 2025-10-06\n" + "- 国庆节: 2025-10-01 至 2025-10-07");
	}

	// ===== Helper Classes =====

	private static class Employee {

		String id, name, department, position, email, status;

		Employee(String id, String name, String department, String position, String email, String status) {
			this.id = id;
			this.name = name;
			this.department = department;
			this.position = position;
			this.email = email;
			this.status = status;
		}

	}

	private static class RecruitmentRequest {

		String id, department, position, reason, status;

		int headcount;

		RecruitmentRequest(String id, String department, String position, int headcount, String reason, String status) {
			this.id = id;
			this.department = department;
			this.position = position;
			this.headcount = headcount;
			this.reason = reason;
			this.status = status;
		}

	}

	// ===== Input Class =====

	public static class HrToolInput {

		@JsonProperty("action")
		private String action;

		@JsonProperty("employee_id")
		private String employeeId;

		@JsonProperty("name")
		private String name;

		@JsonProperty("department")
		private String department;

		@JsonProperty("team_name")
		private String teamName;

		@JsonProperty("position")
		private String position;

		@JsonProperty("start_date")
		private String startDate;

		@JsonProperty("last_working_day")
		private String lastWorkingDay;

		@JsonProperty("headcount")
		private Integer headcount;

		@JsonProperty("reason")
		private String reason;

		@JsonProperty("candidate_id")
		private String candidateId;

		@JsonProperty("leave_type")
		private String leaveType; // reserved for future

		// Getters
		public String getAction() {
			return action;
		}

		public String getEmployeeId() {
			return employeeId;
		}

		public String getName() {
			return name;
		}

		public String getDepartment() {
			return department;
		}

		public String getTeamName() {
			return teamName;
		}

		public String getPosition() {
			return position;
		}

		public String getStartDate() {
			return startDate;
		}

		public String getLastWorkingDay() {
			return lastWorkingDay;
		}

		public Integer getHeadcount() {
			return headcount;
		}

		public String getReason() {
			return reason;
		}

		public String getCandidateId() {
			return candidateId;
		}

		public String getLeaveType() {
			return leaveType;
		}

		// Setters (required by Jackson)
		public void setAction(String action) {
			this.action = action;
		}

		public void setEmployeeId(String employeeId) {
			this.employeeId = employeeId;
		}

		public void setName(String name) {
			this.name = name;
		}

		public void setDepartment(String department) {
			this.department = department;
		}

		public void setTeamName(String teamName) {
			this.teamName = teamName;
		}

		public void setPosition(String position) {
			this.position = position;
		}

		public void setStartDate(String startDate) {
			this.startDate = startDate;
		}

		public void setLastWorkingDay(String lastWorkingDay) {
			this.lastWorkingDay = lastWorkingDay;
		}

		public void setHeadcount(Integer headcount) {
			this.headcount = headcount;
		}

		public void setReason(String reason) {
			this.reason = reason;
		}

		public void setCandidateId(String candidateId) {
			this.candidateId = candidateId;
		}

		public void setLeaveType(String leaveType) {
			this.leaveType = leaveType;
		}

	}

	// ===== Overrides =====

	@Override
	public String getDescription() {
		return "HR Assistant Tool - Provides HR-related information and simulated workflows for employee lifecycle and recruitment. "
				+ "All write operations (e.g., add_employee, create_recruitment_request) are simulations only and require manual HR approval. "
				+ "Supported actions:\n"
				+ "- Employee: query_employee, add_employee (sim), remove_employee (sim), get_onboarding_guide, check_onboarding_status, check_offboarding_status\n"
				+ "- Recruitment: create_recruitment_request (sim), query_recruitment_requests, query_recruitment_progress, get_interview_schedule\n"
				+ "- Organization: query_team_members, query_org_chart, find_hr_contact, query_holiday_calendar\n"
				+ "Note:\n"
				+ "- Always specify required parameters (e.g., employee_id for employee actions, department/position for recruitment)\n"
				+ "- Sensitive data (salary, ID card) is never exposed\n"
				+ "- If any parameter is missing, ask the user to clarify before proceeding.";
	}

	@Override
	public String getParameters() {
		return "{" + "\"type\":\"object\"," + "\"properties\":{"
				+ "\"action\":{\"type\":\"string\",\"description\":\"HR operation to perform\",\"enum\":["
				+ "\"query_employee\",\"add_employee\",\"remove_employee\",\"get_onboarding_guide\","
				+ "\"check_onboarding_status\",\"check_offboarding_status\","
				+ "\"create_recruitment_request\",\"query_recruitment_requests\",\"query_recruitment_progress\","
				+ "\"get_interview_schedule\",\"query_team_members\",\"query_org_chart\","
				+ "\"find_hr_contact\",\"query_holiday_calendar\"]},"
				+ "\"employee_id\":{\"type\":\"string\",\"description\":\"Employee ID (e.g., E1001)\"},"
				+ "\"name\":{\"type\":\"string\",\"description\":\"Employee name (for query)\"},"
				+ "\"department\":{\"type\":\"string\",\"description\":\"Department name (e.g., 研发部)\"},"
				+ "\"team_name\":{\"type\":\"string\",\"description\":\"Team name\"},"
				+ "\"position\":{\"type\":\"string\",\"description\":\"Job position (e.g., Java工程师)\"},"
				+ "\"start_date\":{\"type\":\"string\",\"description\":\"Start date in YYYY-MM-DD format\"},"
				+ "\"last_working_day\":{\"type\":\"string\",\"description\":\"Last working day in YYYY-MM-DD format\"},"
				+ "\"headcount\":{\"type\":\"integer\",\"description\":\"Number of hires requested\",\"minimum\":1},"
				+ "\"reason\":{\"type\":\"string\",\"description\":\"Reason for recruitment\"},"
				+ "\"candidate_id\":{\"type\":\"string\",\"description\":\"Candidate ID\"}" + "},"
				+ "\"required\":[\"action\"]" + "}";
	}

	@Override
	public Class<HrToolInput> getInputType() {
		return HrToolInput.class;
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

	@Override
	public ToolStateInfo getCurrentToolStateString() {
		String toolState = "HrTool State:\n" + "- Current Plan ID: " + currentPlanId + "\n" + "- Mock Employees: "
				+ MOCK_EMPLOYEES.size() + "\n" + "- Mock Recruitment Requests: " + MOCK_RECRUITMENT_REQUESTS.size();
		return new ToolStateInfo(null, toolState);

	}

	@Override
	public void cleanup(String planId) {
		log.info("HrTool cleanup for planId: {}", planId);
	}

	@Override
	public String getServiceGroup() {
		return "hr";
	}

	@Override
	public String getName() {
		return TOOL_NAME;
	}

}
