/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.jira.mappers.JiraRequestResponseMapper;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.jira.JiraClient;
import io.harness.jira.JiraFieldTypeNG;
import io.harness.jira.JiraInstanceData;
import io.harness.jira.JiraInstanceData.JiraDeploymentType;
import io.harness.jira.JiraIssueCreateMetadataNG;
import io.harness.jira.JiraIssueNG;
import io.harness.jira.JiraIssueTypeNG;
import io.harness.jira.JiraIssueUpdateMetadataNG;
import io.harness.jira.JiraProjectBasicNG;
import io.harness.jira.JiraProjectNG;
import io.harness.jira.JiraStatusNG;
import io.harness.jira.JiraUserData;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

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
          "The Jira URL or credentials for the connector is incorrect",
          new InvalidArtifactServerException("Invalid Jira connector details", USER));
    }
  }

  public JiraTaskNGResponse getProjects(JiraTaskNGParameters params) {
    JiraClient jiraClient = getJiraClient(params);
    List<JiraProjectBasicNG> projects = jiraClient.getProjects();
    return JiraTaskNGResponse.builder().projects(projects).build();
  }

  public JiraTaskNGResponse getStatuses(JiraTaskNGParameters params) {
    JiraClient jiraClient = getJiraClient(params);
    List<JiraStatusNG> statuses = jiraClient.getStatuses(params.getProjectKey(), params.getIssueType());
    return JiraTaskNGResponse.builder().statuses(statuses).build();
  }

  public JiraTaskNGResponse getIssue(JiraTaskNGParameters params) {
    JiraClient jiraClient = getJiraClient(params);
    JiraIssueNG issue = jiraClient.getIssue(params.getIssueKey());
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
    Set<String> userTypeFields = new HashSet<>();
    if (EmptyPredicate.isNotEmpty(params.getFields())) {
      JiraIssueCreateMetadataNG createMetadata = null;
      try {
        createMetadata = jiraClient.getIssueCreateMetadata(
            params.getProjectKey(), params.getIssueType(), null, false, false, params.isNewMetadata(), false);
      } catch (Exception ex) {
        // skipping setting user fields if error occurred while fetching createMetadata.
        log.warn("Failed fetching createMetadata for setting user type fields during create issue", ex);
      }
      if (!isNull(createMetadata)) {
        JiraProjectNG project = createMetadata.getProjects().get(params.getProjectKey());
        if (project != null) {
          JiraIssueTypeNG issueType = project.getIssueTypes().get(params.getIssueType());
          if (issueType != null) {
            issueType.getFields().entrySet().forEach(e -> {
              if (e.getValue().getSchema().getType().equals(JiraFieldTypeNG.USER)) {
                userTypeFields.add(e.getKey());
              }
            });
            setUserTypeCustomFieldsIfPresent(jiraClient, userTypeFields, params);
          }
        }
      }
    }
    JiraIssueNG issue = jiraClient.createIssue(
        params.getProjectKey(), params.getIssueType(), params.getFields(), true, params.isNewMetadata(), false);
    return JiraTaskNGResponse.builder().issue(issue).build();
  }

  public JiraTaskNGResponse updateIssue(JiraTaskNGParameters params) {
    JiraClient jiraClient = getJiraClient(params);

    if (EmptyPredicate.isNotEmpty(params.getFields())) {
      JiraIssueUpdateMetadataNG updateMetadata = null;

      try {
        updateMetadata = jiraClient.getIssueUpdateMetadata(params.getIssueKey());
      } catch (Exception ex) {
        // skipping setting user fields if error occurred while fetching updateMetadata.
        log.warn("Failed fetching updateMetadata for setting user type fields during update issue", ex);
      }

      if (updateMetadata != null) {
        Set<String> userTypeFields = updateMetadata.getFields()
                                         .entrySet()
                                         .stream()
                                         .filter(e -> e.getValue().getSchema().getType().equals(JiraFieldTypeNG.USER))
                                         .map(Map.Entry::getKey)
                                         .collect(Collectors.toSet());
        setUserTypeCustomFieldsIfPresent(jiraClient, userTypeFields, params);
      }
    }
    JiraIssueNG issue = jiraClient.updateIssue(
        params.getIssueKey(), params.getTransitionToStatus(), params.getTransitionName(), params.getFields());
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

  private void setUserTypeCustomFieldsIfPresent(
      JiraClient jiraClient, Set<String> userTypeFields, JiraTaskNGParameters params) {
    params.getFields().forEach((key, value) -> {
      List<JiraUserData> userDataList = new ArrayList<>();

      if (userTypeFields.contains(key)) {
        if (value != null && !value.equals("")) {
          if (value.startsWith("JIRAUSER")) {
            JiraUserData userData = jiraClient.getUser(value);
            params.getFields().put(key, userData.getName());
            return;
          }

          JiraInstanceData jiraInstanceData = jiraClient.getInstanceData();
          if (jiraInstanceData.getDeploymentType() == JiraDeploymentType.CLOUD) {
            userDataList = jiraClient.getUsers(null, value, null);
            if (userDataList.isEmpty()) {
              userDataList = jiraClient.getUsers(value, null, null);
            }
          } else {
            userDataList = jiraClient.getUsers(value, null, null);
          }
          if (userDataList.size() != 1) {
            throw new InvalidRequestException(
                "Found " + userDataList.size() + " jira users with this query. Should be exactly 1.");
          }
          if (userDataList.get(0).getAccountId().startsWith("JIRAUSER")) {
            params.getFields().put(key, userDataList.get(0).getName());
          } else {
            params.getFields().put(key, userDataList.get(0).getAccountId());
          }
        }
      }
    });
  }

  private JiraClient getJiraClient(JiraTaskNGParameters parameters) {
    return new JiraClient(JiraRequestResponseMapper.toJiraInternalConfig(parameters.getJiraConnectorDTO()));
  }
}
