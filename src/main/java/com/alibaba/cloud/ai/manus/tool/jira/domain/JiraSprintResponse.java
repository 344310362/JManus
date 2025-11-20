package com.alibaba.cloud.ai.manus.tool.jira.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class JiraSprintResponse {
    private List<JiraSprint> suggestions;
    private List<JiraSprint> allMatches;
}
