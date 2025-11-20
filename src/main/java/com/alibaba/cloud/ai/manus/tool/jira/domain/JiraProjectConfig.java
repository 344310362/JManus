package com.alibaba.cloud.ai.manus.tool.jira.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JiraProjectConfig {
    private String jiraIssueTypeId;
    private String jiraStoryTypeId;
    private boolean thirdPartTemplate;
    private String jiraKey;
}
