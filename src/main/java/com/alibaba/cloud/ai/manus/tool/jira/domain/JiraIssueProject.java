package com.alibaba.cloud.ai.manus.tool.jira.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class JiraIssueProject {
    private String id;
    private String name;
    private String key;
    private List<JiraIssueType> issueTypes;
}
