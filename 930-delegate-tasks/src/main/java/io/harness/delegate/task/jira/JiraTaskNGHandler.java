/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.jira;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.jira.mappers.JiraRequestResponseMapper;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.jira.JiraClient;
import io.harness.jira.JiraIssueCreateMetadataNG;
import io.harness.jira.JiraIssueNG;
import io.harness.jira.JiraIssueTransitionNG;
import io.harness.jira.JiraIssueTransitionsNG;
import io.harness.jira.JiraIssueUpdateMetadataNG;
import io.harness.jira.JiraProjectBasicNG;
import io.harness.jira.JiraStatusNG;
import io.harness.jira.JiraUserData;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
@Singleton
@Slf4j
public class JiraTaskNGHandler {
  public JiraTaskNGResponse validateCredentials(JiraTaskNGParameters params) {
    try {
      JiraClient jiraClient = getJiraClient(params);
      jiraClient.getProjects();
      return JiraTaskNGResponse.builder().build();
    } catch (Exception ex) {
      if (ex.getMessage().equals("Project list is empty")) {
        log.error("Fetched Project list is empty", ex);
        throw NestedExceptionUtils.hintWithExplanationException(
            "Check if the Jira credentials are correct and you have necessary permissions to Jira Projects",
            "Either the credentials provided are invalid or the user does not have necessary permissions to Jira Projects",
            new InvalidArtifactServerException("Unable to fetch projects", USER));
      }
      String errorMessage = "Failed to fetch projects during credential validation";
      log.error(errorMessage, ex);
      throw NestedExceptionUtils.hintWithExplanationException(
          "Check if the Jira URL & Jira credentials are correct. Jira URLs are different for different credentials",
          "The Jira URL or credentials for the ConnectorDisconnectHandler is incorrect",
          new InvalidArtifactServerException("Invalid Jira ConnectorDisconnectHandler details", USER));
    }
  }

  public JiraTaskNGResponse getProjects(JiraTaskNGParameters params) {
    JiraClient jiraClient = getJiraClient(params);
    List<JiraProjectBasicNG> projects = jiraClient.getProjects();
    return JiraTaskNGResponse.builder().projects(projects).build();
  }

  public JiraTaskNGResponse getStatuses(JiraTaskNGParameters params) {
    JiraClient jiraClient = getJiraClient(params);
    List<JiraStatusNG> statuses;
    if (isBlank(params.getIssueKey())) {
      statuses = jiraClient.getStatuses(params.getProjectKey(), params.getIssueType());
    } else {
      statuses = jiraClient.getStatuses(params.getIssueKey());
    }

    return JiraTaskNGResponse.builder().statuses(statuses).build();
  }

  public JiraTaskNGResponse getIssue(JiraTaskNGParameters params) {
    JiraClient jiraClient = getJiraClient(params);
    JiraIssueNG issue = jiraClient.getIssue(params.getIssueKey(), params.getFilterFields());
    return JiraTaskNGResponse.builder().issue(issue).build();
  }

  public JiraTaskNGResponse getIssueCreateMetadata(JiraTaskNGParameters params) {
    JiraClient jiraClient = getJiraClient(params);
    JiraIssueCreateMetadataNG createMetadata =
        jiraClient.getIssueCreateMetadata(params.getProjectKey(), params.getIssueType(), params.getExpand(),
            params.isFetchStatus(), params.isIgnoreComment(), params.isNewMetadata(), false);

    return JiraTaskNGResponse.builder().issueCreateMetadata(createMetadata).build();
  }

  public JiraTaskNGResponse getIssueUpdateMetadata(JiraTaskNGParameters params) {
    JiraClient jiraClient = getJiraClient(params);
    JiraIssueUpdateMetadataNG updateMetadata = jiraClient.getIssueUpdateMetadata(params.getIssueKey());
    return JiraTaskNGResponse.builder().issueUpdateMetadata(updateMetadata).build();
  }

  public JiraTaskNGResponse createIssue(JiraTaskNGParameters params) {
    JiraClient jiraClient = getJiraClient(params);

    JiraIssueNG issue = jiraClient.createIssue(
        params.getProjectKey(), params.getIssueType(), params.getFields(), true, params.isNewMetadata(), false, true);
    return JiraTaskNGResponse.builder().issue(issue).build();
  }

  public JiraTaskNGResponse updateIssue(JiraTaskNGParameters params) {
    JiraClient jiraClient = getJiraClient(params);

    JiraIssueNG issue = jiraClient.updateIssue(
        params.getIssueKey(), params.getTransitionToStatus(), params.getTransitionName(), params.getFields(), true);
    return JiraTaskNGResponse.builder().issue(issue).build();
  }

  public JiraTaskNGResponse searchUser(JiraTaskNGParameters params) {
    JiraClient jiraClient = getJiraClient(params);
    List<JiraUserData> jiraUserDataList = jiraClient.getUsers(
        params.getJiraSearchUserParams().getUserQuery(), null, params.getJiraSearchUserParams().getStartAt());
    return JiraTaskNGResponse.builder()
        .jiraSearchUserData(JiraSearchUserData.builder().jiraUserDataList(jiraUserDataList).build())
        .build();
  }

  private JiraClient getJiraClient(JiraTaskNGParameters parameters) {
    return new JiraClient(JiraRequestResponseMapper.toJiraInternalConfig(parameters.getJiraConnectorDTO()));
  }

  public JiraTaskNGResponse getIssueTransitions(JiraTaskNGParameters params) {
    JiraClient jiraClient = getJiraClient(params);
    JiraIssueTransitionsNG jiraIssueTransitionsNG = null;
    if (isNotBlank(params.getIssueKey())) {
      jiraIssueTransitionsNG = jiraClient.getIssueTransitions(params.getIssueKey());
    }
    List<JiraIssueTransitionNG> jiraIssueTransitionNGList =
        jiraIssueTransitionsNG.getTransitions() == null ? new ArrayList<>() : jiraIssueTransitionsNG.getTransitions();
    return JiraTaskNGResponse.builder().jiraIssueTransitionsNG(jiraIssueTransitionNGList).build();
  }
}
