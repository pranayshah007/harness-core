/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.network.Http.getOkHttpClientBuilder;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.HttpResponseException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.JiraClientException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.network.Http;
import io.harness.network.SafeHttpCall;
import io.harness.validation.Validator;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONArray;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.hibernate.validator.constraints.NotBlank;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@OwnedBy(CDC)
@Slf4j
public class JiraClient {
  private static final int MAX_FIELD_RESULTS = 200;
  private static final int CONNECT_TIMEOUT = 60;
  private static final int READ_TIMEOUT = 60;

  // Comment fields are added to create and update metadata by jira client so that users can add comment as if it was
  // any other jira field. When jira issue is actually created/updated these fields are handled in s special way.
  private static final String COMMENT_FIELD_KEY = "comment";
  private static final String COMMENT_FIELD_NAME = "Comment";
  private static final JiraFieldNG COMMENT_FIELD =
      JiraFieldNG.builder()
          .key(COMMENT_FIELD_KEY)
          .name(COMMENT_FIELD_NAME)
          .schema(JiraFieldSchemaNG.builder().type(JiraFieldTypeNG.STRING).build())
          .build();
  private static final String REPORTER_FIELD_NAME = "Reporter";
  private static final String ISSUE_TYPE_FIELD_NAME = "Issue Type";

  private final JiraInternalConfig config;
  private final JiraRestClient restClient;

  /**
   * Create a new jira client instance.
   *
   * @param config the url and credentials for the jira instance
   */
  public JiraClient(JiraInternalConfig config) {
    this.config = config;
    this.restClient = createRestClient();
  }

  public JiraInstanceData getInstanceData() {
    JiraInstanceData instanceData = executeCall(restClient.getInstanceData(), "fetching jira instance data");
    log.info("Jira instance of type: {} - Version: {}", instanceData.getDeploymentType(), instanceData.getVersion());
    return instanceData;
  }
  public JiraUserData getUser(String userKey) {
    return executeCall(restClient.getUser(userKey), "getting user");
  }

  public List<JiraUserData> getUsers(String userQuery, String accountId, String startAt) {
    JiraInstanceData jiraInstanceData = getInstanceData();
    switch (jiraInstanceData.deploymentType) {
      case SERVER:
        return executeCall(
            restClient.getUsersForJiraServer(userQuery.equals("") ? "\"\"" : userQuery, accountId, "10", startAt),
            "fetching users");
      default:
        return executeCall(restClient.getUsers(userQuery, accountId, "10", startAt), "fetching users");
    }
  }

  /**
   * Get all projects for the jira instance.
   *
   * @return the list of projects
   */
  public List<JiraProjectBasicNG> getProjects() {
    List<JiraProjectBasicNG> projects = executeCall(restClient.getProjects(), "fetching projects");
    Validator.notEmptyCheck("Project list is empty", projects);
    return projects;
  }

  /**
   * Get all statuses. Optionally filter by projectKey and issueType
   *
   * @return the list of projects
   */
  public List<JiraStatusNG> getStatuses(String projectKey, String issueType) {
    if (StringUtils.isBlank(projectKey)) {
      return getStatuses();
    }

    List<JiraIssueTypeNG> issueTypes = getProjectStatuses(projectKey);
    if (EmptyPredicate.isEmpty(issueTypes)) {
      return Collections.emptyList();
    }
    if (StringUtils.isNotBlank(issueType)) {
      issueTypes = issueTypes.stream().filter(it -> it.getName().equals(issueType)).collect(Collectors.toList());
      if (EmptyPredicate.isEmpty(issueTypes)) {
        return Collections.emptyList();
      }
    }

    // Collect all the statuses from all issue types and deduplicate based on status name.
    return issueTypes.stream()
        .flatMap(it -> it.getStatuses().stream())
        .collect(Collectors.collectingAndThen(
            Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(JiraStatusNG::getName))), ArrayList::new));
  }

  /**
   * Get an issue by issue key. If issue key is invalid, null is returned.
   *
   * There is special handling for these fields:
   * - project: returns 3 field - "Project Key", "Project Name", "__project" (whole object for internal use)
   * - issuetype: returns 2 fields - "Issue Type", "__issuetype" (whole object for internal use)
   * - status: returns 2 fields - "Status", "__status" (whole object for internal use)
   * - timetracking: returns 2 fields - "Original Estimate", "Remaining Estimate"
   * - Fields treated as OPTION type fields:
   *   - resolution
   *   - component
   *   - priority
   *   - version
   *
   * @param issueKey the issue key
   * @return the issue with the given key
   */
  public JiraIssueNG getIssue(@NotBlank String issueKey) {
    return getIssue(issueKey, false);
  }

  private JiraIssueNG getIssue(@NotBlank String issueKey, boolean throwOnInvalidKey) {
    try {
      JiraIssueNG issue = executeCall(restClient.getIssue(issueKey, "names,schema"), "fetching issue");
      if (issue != null) {
        issue.updateJiraBaseUrl(config.getJiraUrl());
      }
      return issue;
    } catch (Exception ex) {
      if (!throwOnInvalidKey && is404StatusCode(ex)) {
        return null;
      }
      throw ex;
    }
  }

  /**
   * Get the issue create metadata information - schema and other information for all project(s), issue type(s) and
   * fields.
   *
   * There is special handling for these fields:
   * - project and issue type are not part of the fields (they are already passed as parameters)
   * - status (if fetchStatus is true): returned  as an OPTION field with possible status values. Is projectKey is not
   *   provided, we fetch all the statuses across the jira instance and add it to all issue types
   * - timetracking: returned as 2 string fields - "Original Estimate", "Remaining Estimate"
   * - Fields treated as OPTION type fields:
   *   - resolution
   *   - component
   *   - priority
   *   - version
   * - comment: added as a string field
   *
   * @param projectKey    the project key - can be null if not known
   * @param issueType     the issue type - can be null if not known
   * @param expand        the expand query parameter - if null a default value of `projects.issuetypes.fields` is used
   * @param fetchStatus   should also fetch status
   * @param ignoreComment should not fetch comment
   * @return the issue create metadata
   */
  public JiraIssueCreateMetadataNG getIssueCreateMetadata(String projectKey, String issueType, String expand,
      boolean fetchStatus, boolean ignoreComment, boolean ffEnabled, boolean fromCG) {
    JiraIssueCreateMetadataNG createMetadata = new JiraIssueCreateMetadataNG();
    JiraInstanceData jiraInstanceData = getInstanceData();

    if (jiraInstanceData.deploymentType == JiraInstanceData.JiraDeploymentType.SERVER && ffEnabled) {
      if (issueType == null) {
        JiraIssueCreateMetadataNGIssueTypes createMetadataNGIssueTypes = executeCall(
            restClient.getIssueCreateMetadataIssueTypes(EmptyPredicate.isEmpty(projectKey) ? null : projectKey),
            "fetching create metadata V2");
        originalMetadataFromNewIssueTypeMetadata(projectKey, createMetadata, createMetadataNGIssueTypes);
      } else {
        JiraIssueTypeNG issueTypeNG = getIssueTypeFromName(issueType, projectKey);
        JiraIssueCreateMetadataNGFields createMetadataNGFields = executeCall(
            restClient.getIssueCreateMetadataFields(EmptyPredicate.isEmpty(projectKey) ? null : projectKey,
                EmptyPredicate.isEmpty(issueTypeNG.getId()) ? null : issueTypeNG.getId(), MAX_FIELD_RESULTS),
            "fetching create metadata");
        if (!ignoreComment) {
          createMetadataNGFields.addField(COMMENT_FIELD);
        }
        if (!fromCG) {
          createMetadataNGFields.removeField(REPORTER_FIELD_NAME);
        }
        createMetadataNGFields.removeField(ISSUE_TYPE_FIELD_NAME);
        originalMetadataFromNewFieldsMetadata(projectKey, createMetadata, issueTypeNG, createMetadataNGFields);
      }
    } else {
      createMetadata =
          executeCall(restClient.getIssueCreateMetadata(EmptyPredicate.isEmpty(projectKey) ? null : projectKey,
                          EmptyPredicate.isEmpty(issueType) ? null : issueType,
                          EmptyPredicate.isEmpty(expand) ? "projects.issuetypes.fields" : expand),
              "fetching create metadata");
      if (!ignoreComment) {
        createMetadata.addField(COMMENT_FIELD);
      }
      createMetadata.removeField(ISSUE_TYPE_FIELD_NAME);
    }

    if (fetchStatus) {
      if (EmptyPredicate.isEmpty(projectKey)) {
        // If project key is not present, we get all possible statuses for the jira instance and add them to each issue
        // type. This means in the ui dropdown we might show statuses which at runtime will fail because they are not
        // part of the runtime project and issue type.
        List<JiraStatusNG> statuses = getStatuses();
        createMetadata.updateStatuses(statuses);
      } else {
        List<JiraIssueTypeNG> projectStatuses = getProjectStatuses(projectKey);
        createMetadata.updateProjectStatuses(projectKey, projectStatuses);
      }
    } else {
      // After deserialization to JiraIssueCreateMetadataNG, by default, we receive a status field with no allowed
      // values in all issue types. If fetchStatus is false, remove that field from issue types so that ui doesn't show
      // it as one the fields.
      createMetadata.removeField(JiraConstantsNG.STATUS_NAME);
    }

    return createMetadata;
  }

  @VisibleForTesting
  void originalMetadataFromNewIssueTypeMetadata(String projectKey, JiraIssueCreateMetadataNG createMetadata,
      JiraIssueCreateMetadataNGIssueTypes createMetadataNGIssueTypes) {
    JiraProjectNG jiraProjectNG = new JiraProjectNG(createMetadataNGIssueTypes);
    jiraProjectNG.setKey(projectKey);
    Map<String, JiraProjectNG> projects = new HashMap<>();
    projects.put(projectKey, jiraProjectNG);
    createMetadata.setProjects(projects);
  }

  @VisibleForTesting
  void originalMetadataFromNewFieldsMetadata(String projectKey, JiraIssueCreateMetadataNG createMetadata,
      JiraIssueTypeNG issueTypeNG, JiraIssueCreateMetadataNGFields createMetadataNGFields) {
    JiraIssueTypeNG jiraIssueTypeNG = new JiraIssueTypeNG(createMetadataNGFields);
    jiraIssueTypeNG.setId(issueTypeNG.getId());
    jiraIssueTypeNG.setName(issueTypeNG.getName());
    jiraIssueTypeNG.setDescription(issueTypeNG.getDescription());
    jiraIssueTypeNG.setSubTask(issueTypeNG.isSubTask());
    Map<String, JiraIssueTypeNG> issueTypes = new HashMap<>();
    issueTypes.put(jiraIssueTypeNG.getName(), jiraIssueTypeNG);
    JiraProjectNG jiraProjectNG = new JiraProjectNG();
    jiraProjectNG.setKey(projectKey);
    jiraProjectNG.setIssueTypes(issueTypes);
    Map<String, JiraProjectNG> projects = new HashMap<>();
    projects.put(projectKey, jiraProjectNG);
    createMetadata.setProjects(projects);
  }

  /**
   * Get the issue update metadata information - schema information for the issue with the given key.
   *
   * There is special handling for these fields:
   * - project and status are not part of the fields
   * - issuetype is a part of fields
   * - timetracking: returned as 2 string fields - "Original Estimate", "Remaining Estimate"
   * - Fields treated as OPTION type fields:
   *   - resolution
   *   - component
   *   - priority
   *   - version
   * - comment: added as a string field
   *
   * @param issueKey  the key of the issue
   * @return the issue update metadata
   */
  public JiraIssueUpdateMetadataNG getIssueUpdateMetadata(@NotBlank String issueKey) {
    JiraIssueUpdateMetadataNG issueUpdateMetadata =
        executeCall(restClient.getIssueUpdateMetadata(issueKey), "fetching update metadata");
    issueUpdateMetadata.getFields().put(COMMENT_FIELD_NAME, COMMENT_FIELD);
    return issueUpdateMetadata;
  }

  private List<JiraStatusNG> getStatuses() {
    return executeCall(restClient.getStatuses(), "fetching statuses");
  }

  private List<JiraIssueTypeNG> getProjectStatuses(@NotBlank String projectKey) {
    return executeCall(restClient.getProjectStatuses(projectKey), "fetching project statuses");
  }

  public JSONArray getResolution() {
    return executeCall(restClient.getResolution(), "fetching Resolutions");
  }

  /**
   * Create an issue in the given project and issue type.
   *
   * There is special handling for these fields:
   * - project: sent as { key: [projectKey] }
   * - issuetype: sent as { id: [issueTypeId] }
   * - status is not part of the fields sent to jira (default status is assigned)
   * - timetracking: sent as { originalEstimate: [originalEstimate], remainingEstimate: [remainingEstimate] }
   * - OPTION type fields are sent as { id: [optionId], name: [optionName], value: [optionValue] } - null fields are
   *   ignored
   * - Fields treated as OPTION type fields:
   *   - resolution
   *   - component
   *   - priority
   *   - version
   *
   * @param projectKey    the project key
   * @param issueTypeName the issue type
   * @param fields        the issue fields - list/array type are represented as comma separated strings - if an array
   *                      element has comma itself, it should be wrapped in quotes
   * @return the created issue
   */
  public JiraIssueNG createIssue(@NotBlank String projectKey, @NotBlank String issueTypeName,
      Map<String, String> fields, boolean checkRequiredFields, boolean ffEnabled, boolean fromCG) {
    JiraIssueCreateMetadataNG createMetadata;

    try {
      createMetadata = getIssueCreateMetadata(projectKey, issueTypeName, null, false, false, ffEnabled, fromCG);
    } catch (Exception ex) {
      log.warn("Failed to fetch createMetadata while creating the issue", ex);
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check if project key and issue type provided are correct",
          "Failed to fetch create metadata while creating the issue",
          new JiraClientException(String.format("Failed to fetch create metadata while creating the issue: %s",
                                      ExceptionUtils.getMessage(ex)),
              ex));
    }

    JiraProjectNG project = createMetadata.getProjects().get(projectKey);
    if (project == null) {
      throw new InvalidRequestException(String.format("Invalid project: %s", projectKey));
    }

    JiraIssueTypeNG issueType = project.getIssueTypes().get(issueTypeName);
    if (issueType == null) {
      throw new InvalidRequestException(
          String.format("Invalid issue type in project %s: %s", projectKey, issueTypeName));
    }

    ImmutablePair<Map<String, String>, String> pair = extractCommentField(fields);
    fields = pair.getLeft();
    String comment = pair.getRight();

    // Create issue with all non-comment fields.
    JiraInstanceData jiraInstanceData = getInstanceData();
    JiraCreateIssueRequestNG createIssueRequest = new JiraCreateIssueRequestNG(
        project, issueType, fields, checkRequiredFields, jiraInstanceData.getDeploymentType());
    JiraIssueNG issue = executeCall(restClient.createIssue(createIssueRequest), "creating issue");

    // Add comment.
    if (EmptyPredicate.isNotEmpty(comment)) {
      executeCall(restClient.addIssueComment(issue.getKey(), new JiraAddIssueCommentRequestNG(comment)),
          "adding issue comment");
    }
    return getIssue(issue.getKey(), true);
  }

  /**
   * Update an issue with the given key.
   *
   * There is special handling for these fields:
   * - status is not part of the fields sent to jira - in case status needs to updated, pass transition arguments
   * - timetracking: sent as { originalEstimate: [originalEstimate], remainingEstimate: [remainingEstimate] }
   * - OPTION type fields are sent as { id: [optionId], name: [optionName], value: [optionValue] } - null fields are
   *   ignored
   * - Fields treated as OPTION type fields:
   *   - resolution
   *   - component
   *   - priority
   *   - version
   * - to update issue type: sent as { Issue Type : name of the target issue type } for ex {Issue Type: Story}
   * @param issueKey           the key of the issue to be updated
   * @param transitionToStatus the status to transition to
   * @param transitionName     the transition name to choose in case multiple transitions have same to status
   * @param fields             the issue fields - list/array type are represented as comma separated strings - if an
   *                           array element has comma itself, it should be wrapped in quotes
   * @return the updated issue
   */
  public JiraIssueNG updateIssue(
      @NotBlank String issueKey, String transitionToStatus, String transitionName, Map<String, String> fields) {
    JiraIssueUpdateMetadataNG updateMetadata;
    try {
      updateMetadata = getIssueUpdateMetadata(issueKey);
    } catch (Exception ex) {
      if (is404StatusCode(ex)) {
        throw new JiraClientException(String.format("Invalid jira issue key: %s", issueKey));
      }
      throw ex;
    }

    String transitionId = findIssueTransition(issueKey, transitionToStatus, transitionName);
    ImmutablePair<Map<String, String>, String> pair = extractCommentField(fields);
    fields = pair.getLeft();
    String comment = pair.getRight();
    JiraInstanceData jiraInstanceData = getInstanceData();

    // Update all non-comment fields.
    if (EmptyPredicate.isNotEmpty(fields)) {
      JiraUpdateIssueRequestNG updateIssueRequest =
          new JiraUpdateIssueRequestNG(updateMetadata, null, fields, jiraInstanceData.getDeploymentType());
      executeCall(restClient.updateIssue(issueKey, updateIssueRequest), "updating issue fields");
    }
    // Add comment field.
    if (EmptyPredicate.isNotEmpty(comment)) {
      executeCall(
          restClient.addIssueComment(issueKey, new JiraAddIssueCommentRequestNG(comment)), "adding issue comment");
    }
    // Do status transition.
    if (EmptyPredicate.isNotEmpty(transitionId)) {
      JiraUpdateIssueRequestNG updateIssueRequest =
          new JiraUpdateIssueRequestNG(updateMetadata, transitionId, null, jiraInstanceData.getDeploymentType());
      executeCall(restClient.transitionIssue(issueKey, updateIssueRequest), "updating issue status");
    }
    return getIssue(issueKey, true);
  }

  /**
   * Split fields into non-comment fields and comment field.
   *
   * @param fields the issue fields
   * @return the pair of non-comment fields and comment field
   */
  private ImmutablePair<Map<String, String>, String> extractCommentField(Map<String, String> fields) {
    if (EmptyPredicate.isEmpty(fields)) {
      return ImmutablePair.of(null, null);
    }

    // fields map can be an immutable map so making sure it is mutable.
    fields = new HashMap<>(fields);
    String comment = fields.get(COMMENT_FIELD_NAME);
    fields.remove(COMMENT_FIELD_NAME);
    return ImmutablePair.of(fields, comment);
  }

  private String findIssueTransition(@NotBlank String issueKey, String transitionToStatus, String transitionName) {
    // If status and transitionName are both empty, nothing to do.
    if (EmptyPredicate.isEmpty(transitionName) && EmptyPredicate.isEmpty(transitionToStatus)) {
      return null;
    }

    JiraIssueTransitionsNG transitions =
        executeCall(restClient.getIssueTransitions(issueKey), "fetching issue transitions");
    if (EmptyPredicate.isNotEmpty(transitionName)) {
      // If transitionName is given, find first transition with the given and toStatus, else throw error.
      return transitions.getTransitions()
          .stream()
          .filter(t -> t.getTo().getName().equalsIgnoreCase(transitionToStatus) && t.getName().equals(transitionName))
          .findFirst()
          .map(JiraIssueTransitionNG::getId)
          .orElseThrow(() -> new JiraClientException(String.format("Invalid transition name: %s", transitionName)));
    } else {
      // If transitionName is not given, find first transition with the toStatus, else throw error.
      return transitions.getTransitions()
          .stream()
          .filter(t -> t.getTo().getName().equalsIgnoreCase(transitionToStatus))
          .findFirst()
          .map(JiraIssueTransitionNG::getId)
          .orElseThrow(
              () -> new JiraClientException(String.format("Invalid transition to status: %s", transitionToStatus)));
    }
  }

  private <T> T executeCall(Call<T> call, String action) {
    try {
      log.info("Sending request for: {}", action);
      T resp = SafeHttpCall.executeWithExceptions(call);
      log.info("Response received from: {}", action);
      return resp;
    } catch (IOException | HttpResponseException ex) {
      throw new JiraClientException(String.format("Error %s at url [%s]", action, config.getJiraUrl()), ex);
    }
  }

  private boolean is404StatusCode(Exception ex) {
    HttpResponseException httpResponseException = null;
    if (ex instanceof HttpResponseException) {
      httpResponseException = (HttpResponseException) ex;
    } else if (ex.getCause() instanceof HttpResponseException) {
      httpResponseException = (HttpResponseException) ex.getCause();
    }
    return httpResponseException != null && httpResponseException.getStatusCode() == 404;
  }

  private JiraRestClient createRestClient() {
    String url = config.getJiraUrl() + "rest/api/2/";
    OkHttpClient okHttpClient = getOkHttpClientBuilder()
                                    .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                                    .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                                    .proxy(Http.checkAndGetNonProxyIfApplicable(url))
                                    .addInterceptor(chain -> {
                                      Request newRequest =
                                          chain.request()
                                              .newBuilder()
                                              // if auth token not present , use older deprecated fields
                                              .addHeader("Authorization",
                                                  StringUtils.isBlank(config.getAuthToken())
                                                      ? Credentials.basic(config.getUsername(), config.getPassword())
                                                      : config.getAuthToken())
                                              .build();
                                      return chain.proceed(newRequest);
                                    })
                                    .build();
    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(url)
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(JiraRestClient.class);
  }

  private JiraIssueTypeNG getIssueTypeFromName(String issueTypeName, String projectKey) {
    JiraIssueCreateMetadataNGIssueTypes createMetadataIssueTypes =
        executeCall(restClient.getIssueCreateMetadataIssueTypes(EmptyPredicate.isEmpty(projectKey) ? null : projectKey),
            "fetching create metadata V2");
    JiraIssueTypeNG issueType = createMetadataIssueTypes.getIssueTypes().get(issueTypeName);
    if (issueType == null) {
      throw new InvalidRequestException(
          String.format("Invalid issue type in project %s: %s", projectKey, issueTypeName));
    }
    return issueType;
  }
}
