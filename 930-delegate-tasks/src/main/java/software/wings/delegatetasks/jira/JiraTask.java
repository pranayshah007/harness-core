/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.jira;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.JiraClientException;
import io.harness.exception.WingsException;
import io.harness.jira.JiraAction;
import io.harness.jira.JiraCreateMetaResponse;
import io.harness.jira.JiraCustomFieldValue;
import io.harness.jira.JiraField;
import io.harness.jira.JiraInstanceData;
import io.harness.jira.JiraInstanceData.JiraDeploymentType;
import io.harness.jira.JiraInternalConfig;
import io.harness.jira.JiraIssueCreateMetadataNG;
import io.harness.jira.JiraIssueNG;
import io.harness.jira.JiraUserData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.network.Http;

import software.wings.api.jira.JiraExecutionData;
import software.wings.api.jira.JiraExecutionData.JiraIssueData;
import software.wings.beans.JiraConfig;
import software.wings.beans.jira.JiraTaskParameters;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.net.ssl.SSLHandshakeException;
import lombok.extern.slf4j.Slf4j;
import net.rcarz.jiraclient.BasicCredentials;
import net.rcarz.jiraclient.Field;
import net.rcarz.jiraclient.Field.ValueTuple;
import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.Issue.FluentUpdate;
import net.rcarz.jiraclient.Issue.SearchResult;
import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.Resource;
import net.rcarz.jiraclient.RestException;
import net.rcarz.jiraclient.TimeTracking;
import net.rcarz.jiraclient.Transition;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import okhttp3.Credentials;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class JiraTask extends AbstractDelegateRunnableTask {
  public static final String RESOLUTION = "resolution";
  @Inject private EncryptionService encryptionService;
  @Inject private DelegateLogService logService;

  private static final String JIRA_APPROVAL_FIELD_KEY = "name";
  private static final String WEBHOOK_CREATION_URL = "/rest/webhooks/1.0/webhook/";
  private static final String DISABLE_ISSUETYPE_OPTIMIZATION = "DISABLE_ISSUETYPE_OPTIMIZATION";

  public JiraTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    return run((JiraTaskParameters) parameters[0]);
  }

  public DelegateResponseData run(JiraTaskParameters parameters) {
    JiraAction jiraAction = parameters.getJiraAction();

    DelegateResponseData responseData = null;

    log.info("Executing JiraTask. Action: {}", jiraAction);

    switch (jiraAction) {
      case AUTH:
        responseData = validateCredentials(parameters);
        break;

      case UPDATE_TICKET:
        responseData = updateTicket(parameters);
        break;

      case UPDATE_TICKET_NG:
        responseData = updateTicketNG(parameters);
        break;

      case CREATE_TICKET:
        responseData = createTicket(parameters);
        break;

      case CREATE_TICKET_NG:
        responseData = createTicketNG(parameters);
        break;

      case FETCH_ISSUE:
        responseData = fetchIssue(parameters);
        break;

      case GET_PROJECTS:
        responseData = getProjects(parameters);
        break;

      case GET_FIELDS_OPTIONS:
        responseData = getFieldsAndOptions(parameters);
        break;

      case GET_STATUSES:
        responseData = getStatuses(parameters);
        break;

      case GET_CREATE_METADATA:
        responseData = getCreateMetadata(parameters);
        break;

      case CHECK_APPROVAL:
        responseData = checkJiraApproval(parameters);
        break;

      case SEARCH_USER:
        responseData = getUserListInfo(parameters);
        break;

      default:
        break;
    }

    if (responseData != null) {
      log.info("Done executing JiraTask. Action: {},  ExecutionStatus: {}", jiraAction,
          ((JiraExecutionData) responseData).getExecutionStatus());
    } else {
      log.error("JiraTask Action: {}. null response.", jiraAction);
    }

    return responseData;
  }

  @VisibleForTesting
  protected DelegateResponseData validateCredentials(JiraTaskParameters parameters) {
    try {
      JiraClient jiraClient = getJiraClient(parameters);
      jiraClient.getProjects();
    } catch (JiraException e) {
      String errorMessage = "Failed to fetch projects during credential validation.";
      if (e.getCause() != null) {
        if (e.getCause() instanceof RestException && ((RestException) e.getCause()).getHttpStatusCode() == 407) {
          // Proxy Authentication required
          errorMessage += " Reason: "
              + "Proxy Authentication Required. Error Code: " + ((RestException) e.getCause()).getHttpStatusCode();
          log.error(errorMessage, e);
          return JiraExecutionData.builder().executionStatus(ExecutionStatus.FAILED).errorMessage(errorMessage).build();
        }
        errorMessage += " Reason: " + e.getCause().getMessage();
      }
      log.error(errorMessage, e);
      return JiraExecutionData.builder().errorMessage(errorMessage).executionStatus(ExecutionStatus.FAILED).build();
    }

    return JiraExecutionData.builder().executionStatus(ExecutionStatus.SUCCESS).build();
  }

  private DelegateResponseData getUserListInfo(JiraTaskParameters parameters) {
    try {
      io.harness.jira.JiraClient jiraClient = getNGJiraClient(parameters);

      List<JiraUserData> jiraUserDataList =
          jiraClient.getUsers(parameters.getUserQuery(), null, parameters.getUserQueryOffset());

      return JiraExecutionData.builder()
          .executionStatus(ExecutionStatus.SUCCESS)
          .userSearchList(jiraUserDataList)
          .build();
    } catch (JiraClientException e) {
      String uriString = Resource.getBaseUri() == null ? "" : Resource.getBaseUri() + "user/search";
      String errorMessage = String.format(
          "Failed to fetch issue metadata from Jira server, Uri for GET_CREATE_METADATA - %s ", uriString);
      log.error(errorMessage, e);
      return JiraExecutionData.builder().errorMessage(errorMessage).executionStatus(ExecutionStatus.FAILED).build();
    }
  }

  boolean getDisableOptimizationFlag() {
    String flag = System.getenv(DISABLE_ISSUETYPE_OPTIMIZATION);
    boolean disableOptimization = Boolean.parseBoolean(flag);

    log.info("Jira optimization disabled: {}", disableOptimization);

    return disableOptimization;
  }

  private DelegateResponseData getCreateMetadata(JiraTaskParameters parameters) {
    URI uri = null;
    if (parameters.isUseNewMeta() && checkJiraServer(parameters)) {
      return convertNewMetadataServer(parameters);
    }
    try {
      log.info("Getting decrypted jira client configs for GET_CREATE_METADATA");
      JiraClient jiraClient = getJiraClient(parameters);

      boolean disableOptimization = getDisableOptimizationFlag();

      log.info("Building URI for GET_CREATE_METADATA");
      Map<String, String> queryParams = new HashMap<>();
      if (EmptyPredicate.isNotEmpty(parameters.getCreatemetaExpandParam())) {
        queryParams.put("expand", parameters.getCreatemetaExpandParam());
      } else {
        queryParams.put("expand", "projects.issuetypes.fields");
      }

      if (!disableOptimization && EmptyPredicate.isNotEmpty(parameters.getIssueType())) {
        queryParams.put("issuetypeNames", parameters.getIssueType());
      }

      if (EmptyPredicate.isNotEmpty(parameters.getProject())) {
        queryParams.put("projectKeys", parameters.getProject());
      }
      uri = jiraClient.getRestClient().buildURI(Resource.getBaseUri() + "issue/createmeta", queryParams);

      log.info(" Fetching metadata from jira for GET_CREATE_METADATA");
      JSON response = jiraClient.getRestClient().get(uri);

      log.info(" Response received from jira for GET_CREATE_METADATA");
      JiraCreateMetaResponse jiraCreateMetaResponse = new JiraCreateMetaResponse((JSONObject) response);

      log.info(" Fetching resolutions from jira for GET_CREATE_METADATA");
      URI resolutionUri = jiraClient.getRestClient().buildURI(Resource.getBaseUri() + RESOLUTION);
      JSONArray resolutions = (JSONArray) jiraClient.getRestClient().get(resolutionUri);
      insertResolutionsInCreateMeta(resolutions, jiraCreateMetaResponse);

      log.info(" Returning response to manager for GET_CREATE_METADATA");
      return JiraExecutionData.builder()
          .executionStatus(ExecutionStatus.SUCCESS)
          .createMetadata(jiraCreateMetaResponse)
          .build();
    } catch (URISyntaxException | RestException | IOException | JiraException | RuntimeException e) {
      String uriString = Resource.getBaseUri() == null ? "" : Resource.getBaseUri();
      if (uri == null) {
        uriString = uriString + "issue/createmeta";
      }
      String errorMessage =
          String.format("Failed to fetch issue metadata from Jira server, Uri for GET_CREATE_METADATA - %s ",
              uri == null ? uriString : uri);
      log.error(errorMessage, e);
      return JiraExecutionData.builder().errorMessage(errorMessage).executionStatus(ExecutionStatus.FAILED).build();
    }
  }

  private DelegateResponseData convertNewMetadataServer(JiraTaskParameters parameters) {
    try {
      io.harness.jira.JiraClient jira = getNGJiraClient(parameters);
      JiraIssueCreateMetadataNG jiraIssueCreateMetadataNG =
          jira.getIssueCreateMetadata(parameters.getProject(), parameters.getIssueType(),
              parameters.getCreatemetaExpandParam(), false, false, parameters.isUseNewMeta(), true);
      JiraCreateMetaResponse jiraCreateMetaResponse = new JiraCreateMetaResponse(jiraIssueCreateMetadataNG);
      if (parameters.getIssueType() != null) {
        JSONArray resolutions = jira.getResolution();
        insertResolutionsInCreateMeta(resolutions, jiraCreateMetaResponse);
      }
      return JiraExecutionData.builder()
          .executionStatus(ExecutionStatus.SUCCESS)
          .createMetadata(jiraCreateMetaResponse)
          .build();
    } catch (JiraClientException e) {
      return JiraExecutionData.builder()
          .executionStatus(ExecutionStatus.FAILED)
          .errorMessage(
              "Unable to fetch createMetadata. " + ExceptionUtils.getMessage(e) + " " + extractResponseMessage(e))
          .jiraServerResponse(extractResponseMessage(e))
          .build();
    }
  }

  private boolean checkJiraServer(JiraTaskParameters parameters) {
    try {
      io.harness.jira.JiraClient jira = getNGJiraClient(parameters);
      JiraInstanceData jiraInstanceData = jira.getInstanceData();
      if (jiraInstanceData.deploymentType == JiraInstanceData.JiraDeploymentType.SERVER) {
        return true;
      } else {
        return false;
      }
    } catch (JiraClientException e) {
      log.error("Unable to fetch jira Instance", e);
      return false;
    }
  }

  private void insertResolutionsInCreateMeta(JSONArray resolutions, JiraCreateMetaResponse jiraCreateMetaResponse) {
    Map<String, Object> resolutionProperties = new HashMap<>();
    Map<String, String> schema = new HashMap<>();
    schema.put("type", RESOLUTION);
    schema.put("system", RESOLUTION);
    resolutionProperties.put("schema", schema);
    resolutionProperties.put("required", "false");
    resolutionProperties.put("key", RESOLUTION);
    resolutionProperties.put("name", "Resolution");
    resolutionProperties.put("allowedValues", resolutions);

    JSONObject resolutionObject = JSONObject.fromObject(resolutionProperties);

    JiraField resolution = JiraField.getNewField(resolutionObject, RESOLUTION);

    jiraCreateMetaResponse.getProjects().forEach(jiraProjectData
        -> jiraProjectData.getIssueTypes().forEach(
            jiraIssueType -> jiraIssueType.getJiraFields().put(RESOLUTION, resolution)));
  }

  private DelegateResponseData getStatuses(JiraTaskParameters parameters) {
    try {
      JiraClient jiraClient = getJiraClient(parameters);
      URI uri = jiraClient.getRestClient().buildURI(
          Resource.getBaseUri() + "project/" + parameters.getProject() + "/statuses");
      JSON response = jiraClient.getRestClient().get(uri);

      return JiraExecutionData.builder()
          .executionStatus(ExecutionStatus.SUCCESS)
          .statuses((JSONArray) response)
          .build();
    } catch (URISyntaxException | RestException | IOException | JiraException | RuntimeException e) {
      String errorMessage = "Failed to fetch statuses from Jira server.";
      log.error(errorMessage, e);
      return JiraExecutionData.builder().errorMessage(errorMessage).executionStatus(ExecutionStatus.FAILED).build();
    }
  }

  private DelegateResponseData getFieldsAndOptions(JiraTaskParameters parameters) {
    URI uri;
    SearchResult issues;
    JiraClient jiraClient;
    try {
      jiraClient = getJiraClient(parameters);
      String jqlQuery = "project = " + parameters.getProject();
      issues = jiraClient.searchIssues(jqlQuery, 1);
    } catch (JiraException | RuntimeException e) {
      String errorMessage = "Failed to fetch issues from Jira server for project - " + parameters.getProject();
      log.error(errorMessage, e);
      return JiraExecutionData.builder().errorMessage(errorMessage).executionStatus(ExecutionStatus.FAILED).build();
    }

    Issue issue = null;
    if (CollectionUtils.isNotEmpty(issues.issues)) {
      issue = issues.issues.get(0);
    }
    String issueKey = (issue == null) ? (parameters.getProject() + "-1") : issue.getKey();

    try {
      uri = jiraClient.getRestClient().buildURI(Resource.getBaseUri() + "issue/" + issueKey + "/editmeta");
      JSON response = jiraClient.getRestClient().get(uri);

      return JiraExecutionData.builder().fields((JSONObject) response).executionStatus(ExecutionStatus.SUCCESS).build();
    } catch (URISyntaxException | IOException | RestException | RuntimeException e) {
      String errorMessage = "Failed to fetch editmeta from Jira server. Issue - " + issueKey;
      log.error(errorMessage, e);
      return JiraExecutionData.builder().errorMessage(errorMessage).executionStatus(ExecutionStatus.FAILED).build();
    }
  }

  @VisibleForTesting
  protected DelegateResponseData getProjects(JiraTaskParameters parameters) {
    URI uri = null;
    try {
      JiraClient jira = getJiraClient(parameters);
      uri = jira.getRestClient().buildURI(Resource.getBaseUri() + "project");
      JSON response = jira.getRestClient().get(uri);
      JSONArray projectsArray = JSONArray.fromObject(response);
      return JiraExecutionData.builder().projects(projectsArray).executionStatus(ExecutionStatus.SUCCESS).build();
    } catch (URISyntaxException | IOException | RestException | JiraException | RuntimeException e) {
      String uriString = Resource.getBaseUri() == null ? "" : Resource.getBaseUri();
      if (uri == null) {
        uriString = uriString + "project";
      }
      String errorMessage = String.format(
          "Failed to fetch projects from Jira server, Uri for GET PROJECTS - %s ", uri == null ? uriString : uri);
      if (e instanceof RestException && ((RestException) e).getHttpStatusCode() == 407) {
        // Proxy Authentication required
        errorMessage += " Reason: "
            + "Proxy Authentication Required. Error Code: " + ((RestException) e).getHttpStatusCode();
        log.error(errorMessage, e);
        return JiraExecutionData.builder().executionStatus(ExecutionStatus.FAILED).errorMessage(errorMessage).build();
      }
      if (e.getCause() != null) {
        errorMessage += " Reason: " + e.getCause().getMessage();
      }
      log.error(errorMessage, e);
      return JiraExecutionData.builder().errorMessage(errorMessage).executionStatus(ExecutionStatus.FAILED).build();
    }
  }

  private DelegateResponseData updateTicketNG(JiraTaskParameters parameters) {
    io.harness.jira.JiraClient jiraNGClient;

    jiraNGClient = getNGJiraClient(parameters);

    List<String> issueKeys = new ArrayList<>();
    List<String> issueUrls = new ArrayList<>();
    JiraIssueData firstIssueInListData = null;
    Map<String, String> userTypeFields = null;

    if (EmptyPredicate.isNotEmpty(parameters.getCustomFields())) {
      userTypeFields = parameters.getCustomFields()
                           .entrySet()
                           .stream()
                           .filter(map -> map.getValue().getFieldType().equals("user"))
                           .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().getFieldValue()));

      setUserTypeCustomFieldsIfPresent(jiraNGClient, userTypeFields);
    }

    Map<String, String> fieldsMap = extractFieldsFromCGParameters(parameters, userTypeFields);

    for (String issueId : parameters.getUpdateIssueIds()) {
      try {
        JiraIssueNG issue = jiraNGClient.getIssue(issueId);
        if (issue == null) {
          return JiraExecutionData.builder()
              .executionStatus(ExecutionStatus.FAILED)
              .errorMessage(String.format(
                  "Wasn't able to find issue with provided issue identifier: \"%s\". Please, provide valid key or id.",
                  issueId))
              .build();
        }
        String issueProject = issue.getFields().get("Project Key").toString();

        if (!issueProject.equals(parameters.getProject())) {
          return JiraExecutionData.builder()
              .executionStatus(ExecutionStatus.FAILED)
              .errorMessage(String.format(
                  "Provided issue identifier: \"%s\" does not correspond to Project: \"%s\". Please, provide valid key or id.",
                  issueId, parameters.getProject()))
              .build();
        }

        jiraNGClient.updateIssue(issue.getKey(), parameters.getStatus(), null, fieldsMap);

        log.info("Successfully updated ticket : " + issueId);
        issueKeys.add(issue.getKey());
        issueUrls.add(getIssueUrl(parameters.getJiraConfig(), issue.getKey()));

        if (firstIssueInListData == null) {
          firstIssueInListData = JiraIssueData.builder().description(parameters.getDescription()).build();
        }
      } catch (JiraClientException j) {
        String errorMessage = "Failed to update Jira Issue for Id: " + issueId + ". " + extractResponseMessage(j);
        log.error(errorMessage, j);
        return JiraExecutionData.builder()
            .executionStatus(ExecutionStatus.FAILED)
            .errorMessage(errorMessage)
            .jiraServerResponse(extractResponseMessage(j))
            .build();
      } catch (WingsException we) {
        return JiraExecutionData.builder()
            .executionStatus(ExecutionStatus.FAILED)
            .errorMessage(ExceptionUtils.getMessage(we))
            .build();
      }
    }

    return JiraExecutionData.builder()
        .executionStatus(ExecutionStatus.SUCCESS)
        .errorMessage("Updated Jira ticket " + issueKeys.toString().replaceAll("[\\[\\]]", ""))
        .issueUrl(issueUrls.toString().replaceAll("[\\[\\]]", ""))
        .issueId(parameters.getUpdateIssueIds().get(0))
        .issueKey(issueKeys.get(0))
        .jiraIssueData(firstIssueInListData)
        .build();
  }

  private DelegateResponseData updateTicket(JiraTaskParameters parameters) {
    JiraClient jiraClient;
    try {
      jiraClient = getJiraClient(parameters);
    } catch (JiraException j) {
      String errorMessage = "Failed to create jira client while trying to update : " + parameters.getUpdateIssueIds();
      log.error(errorMessage, j);
      return JiraExecutionData.builder()
          .executionStatus(ExecutionStatus.FAILED)
          .errorMessage(errorMessage)
          .jiraServerResponse(extractResponseMessage(j))
          .build();
    }

    List<String> issueKeys = new ArrayList<>();
    List<String> issueUrls = new ArrayList<>();
    JiraIssueData firstIssueInListData = null;

    for (String issueId : parameters.getUpdateIssueIds()) {
      try {
        Issue issue = jiraClient.getIssue(issueId);

        if (!issue.getProject().getKey().equals(parameters.getProject())) {
          return JiraExecutionData.builder()
              .executionStatus(ExecutionStatus.FAILED)
              .errorMessage(String.format(
                  "Provided issue identifier: \"%s\" does not correspond to Project: \"%s\". Please, provide valid key or id.",
                  issueId, parameters.getProject()))
              .build();
        }

        boolean fieldsUpdated = false;
        FluentUpdate update = issue.update();

        if (EmptyPredicate.isNotEmpty(parameters.getSummary())) {
          update.field(Field.SUMMARY, parameters.getSummary());
          fieldsUpdated = true;
        }

        if (EmptyPredicate.isNotEmpty(parameters.getPriority())) {
          update.field(Field.PRIORITY, parameters.getPriority());
          fieldsUpdated = true;
        }

        if (EmptyPredicate.isNotEmpty(parameters.getDescription())) {
          update.field(Field.DESCRIPTION, parameters.getDescription());
          fieldsUpdated = true;
        }

        if (EmptyPredicate.isNotEmpty(parameters.getLabels())) {
          update.field(Field.LABELS, parameters.getLabels());
          fieldsUpdated = true;
        }

        if (EmptyPredicate.isNotEmpty(parameters.getCustomFields())) {
          setCustomFieldsOnUpdate(parameters, update);
          fieldsUpdated = true;
        }

        if (fieldsUpdated) {
          update.execute();
        }

        if (EmptyPredicate.isNotEmpty(parameters.getComment())) {
          issue.addComment(parameters.getComment());
        }

        if (EmptyPredicate.isNotEmpty(parameters.getStatus())) {
          updateStatus(issue, parameters.getStatus());
        }

        log.info("Successfully updated ticket : " + issueId);
        issueKeys.add(issue.getKey());
        issueUrls.add(getIssueUrl(parameters.getJiraConfig(), issue.getKey()));

        if (firstIssueInListData == null) {
          firstIssueInListData = JiraIssueData.builder().description(parameters.getDescription()).build();
        }
      } catch (JiraException j) {
        String errorMessage = "Failed to update Jira Issue for Id: " + issueId + ". " + extractResponseMessage(j);
        log.error(errorMessage, j);
        return JiraExecutionData.builder()
            .executionStatus(ExecutionStatus.FAILED)
            .errorMessage(errorMessage)
            .jiraServerResponse(extractResponseMessage(j))
            .build();
      } catch (WingsException we) {
        return JiraExecutionData.builder()
            .executionStatus(ExecutionStatus.FAILED)
            .errorMessage(ExceptionUtils.getMessage(we))
            .build();
      }
    }

    return JiraExecutionData.builder()
        .executionStatus(ExecutionStatus.SUCCESS)
        .errorMessage("Updated Jira ticket " + issueKeys.toString().replaceAll("[\\[\\]]", ""))
        .issueUrl(issueUrls.toString().replaceAll("[\\[\\]]", ""))
        .issueId(parameters.getUpdateIssueIds().get(0))
        .issueKey(issueKeys.get(0))
        .jiraIssueData(firstIssueInListData)
        .build();
  }

  void setUserTypeCustomFieldsIfPresent(io.harness.jira.JiraClient jiraNGClient, Map<String, String> userTypeFields) {
    if (!userTypeFields.isEmpty()) {
      List<JiraUserData> userDataList = new ArrayList<>();
      for (Entry<String, String> userField : userTypeFields.entrySet()) {
        if (userField.getValue().startsWith("JIRAUSER")) {
          JiraUserData userData = jiraNGClient.getUser(userField.getValue());
          userTypeFields.put(userField.getKey(), userData.getName());
          continue;
        }

        JiraInstanceData jiraInstanceData = jiraNGClient.getInstanceData();
        if (JiraDeploymentType.CLOUD == jiraInstanceData.getDeploymentType()) {
          userDataList = jiraNGClient.getUsers(null, userField.getValue(), null);
          if (userDataList.isEmpty()) {
            userDataList = jiraNGClient.getUsers(userField.getValue(), null, null);
          }
        } else {
          userDataList = jiraNGClient.getUsers(userField.getValue(), null, null);
        }
        if (userDataList.size() != 1) {
          throw new InvalidRequestException(
              "Found " + userDataList.size() + " jira users with this query. Should be exactly 1.");
        }
        if (userDataList.get(0).getAccountId().startsWith("JIRAUSER")) {
          userTypeFields.put(userField.getKey(), userDataList.get(0).getName());
        } else {
          userTypeFields.put(userField.getKey(), userDataList.get(0).getAccountId());
        }
      }
    }
  }

  void setCustomFieldsOnUpdate(JiraTaskParameters parameters, FluentUpdate update) {
    TimeTracking timeTracking = new TimeTracking();
    for (Entry<String, JiraCustomFieldValue> customField : parameters.getCustomFields().entrySet()) {
      if (customField.getValue().getFieldType().equals("user")) {
        continue;
      }
      if (customField.getKey().equals("TimeTracking:OriginalEstimate")) {
        timeTracking.setOriginalEstimate((String) getCustomFieldValue(customField));
      } else if (customField.getKey().equals("TimeTracking:RemainingEstimate")) {
        timeTracking.setRemainingEstimate((String) getCustomFieldValue(customField));
      } else {
        update.field(customField.getKey(), getCustomFieldValue(customField));
      }
    }
    if (timeTracking.getOriginalEstimate() != null || timeTracking.getRemainingEstimate() != null) {
      update.field(Field.TIME_TRACKING, timeTracking);
    }
  }

  public void updateStatus(Issue issue, String status) throws JiraException {
    List<Transition> allTransitions = null;
    try {
      allTransitions = issue.getTransitions(); // gives all transitions available for that issue
    } catch (JiraException e) {
      log.error("Failed to get all transitions from the Jira");
      throw e;
    }

    Transition transition =
        allTransitions.stream().filter(t -> t.getToStatus().getName().equalsIgnoreCase(status)).findAny().orElse(null);
    if (transition != null) {
      try {
        issue.transition().execute(transition);
      } catch (JiraException e) {
        log.error("Exception while trying to update status to {}", status);
        throw e;
      }
    } else {
      log.error("No transition found from {} to {}", issue.getStatus(), status);
      throw new JiraException("No transition found from [" + issue.getStatus().getName() + "] to [" + status + "]");
    }
  }

  /**
   * Example error message :
   *
   * {400 : {"errorMessages":[],"errors":{"labels":"The label 'Test Application' contains spaces which is invalid."}}}
   *
   * @param e
   * @return
   */
  private String extractResponseMessage(Exception e) {
    if (e.getCause() != null && e.getCause() instanceof RestException) {
      org.json.JSONObject jsonObject;

      try {
        jsonObject = new org.json.JSONObject(((RestException) e.getCause()).getHttpResult());
        org.json.JSONArray jsonArray = (org.json.JSONArray) jsonObject.get("errorMessages");
        if (jsonArray.length() > 0) {
          return (String) jsonArray.get(0);
        }

        org.json.JSONObject errors = (org.json.JSONObject) jsonObject.get("errors");
        Object[] errorsKeys = errors.keySet().toArray();

        String errorsKey = (String) errorsKeys[0];
        return errorsKey + " : " + (String) errors.get((String) errorsKey);
      } catch (Exception ex) {
        log.error("Failed to parse json response from Jira", ex);
        return "Failed to parse json response from Jira: " + ExceptionUtils.getMessage(e.getCause());
      }
    }

    return e.getMessage();
  }

  private DelegateResponseData createTicketNG(JiraTaskParameters parameters) {
    io.harness.jira.JiraClient jira = getNGJiraClient(parameters);
    Map<String, String> userTypeFields = null;

    if (EmptyPredicate.isNotEmpty(parameters.getCustomFields())) {
      userTypeFields = parameters.getCustomFields()
                           .entrySet()
                           .stream()
                           .filter(map -> map.getValue().getFieldType().equals("user"))
                           .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().getFieldValue()));

      setUserTypeCustomFieldsIfPresent(jira, userTypeFields);
    }

    try {
      Map<String, String> fields = extractFieldsFromCGParameters(parameters, userTypeFields);
      JiraIssueNG issue = jira.createIssue(
          parameters.getProject(), parameters.getIssueType(), fields, false, parameters.isUseNewMeta(), true);
      log.info("Script execution finished with status SUCCESS");

      return JiraExecutionData.builder()
          .executionStatus(ExecutionStatus.SUCCESS)
          .jiraAction(JiraAction.CREATE_TICKET_NG)
          .errorMessage("Created Jira ticket " + issue.getKey())
          .issueId(issue.getId())
          .issueKey(issue.getKey())
          .issueUrl(getIssueUrl(parameters.getJiraConfig(), issue.getKey()))
          .jiraIssueData(
              JiraIssueData.builder().description(issue.getFields().getOrDefault("Description", "").toString()).build())
          .build();
    } catch (JiraClientException e) {
      log.error("Unable to create a new Jira ticket", e);
      return JiraExecutionData.builder()
          .executionStatus(ExecutionStatus.FAILED)
          .errorMessage(
              "Unable to create a new Jira ticket. " + ExceptionUtils.getMessage(e) + " " + extractResponseMessage(e))
          .jiraServerResponse(extractResponseMessage(e))
          .build();
    }
  }

  private DelegateResponseData createTicket(JiraTaskParameters parameters) {
    CommandExecutionStatus commandExecutionStatus;
    try {
      JiraClient jira = getJiraClient(parameters);
      Issue.FluentCreate fluentCreate = jira.createIssue(parameters.getProject(), parameters.getIssueType())
                                            .field(Field.SUMMARY, parameters.getSummary());

      if (EmptyPredicate.isNotEmpty(parameters.getPriority())) {
        fluentCreate.field(Field.PRIORITY, parameters.getPriority());
      }

      if (EmptyPredicate.isNotEmpty(parameters.getDescription())) {
        fluentCreate.field(Field.DESCRIPTION, parameters.getDescription());
      }

      if (EmptyPredicate.isNotEmpty(parameters.getLabels())) {
        fluentCreate.field(Field.LABELS, parameters.getLabels());
      }

      if (EmptyPredicate.isNotEmpty(parameters.getCustomFields())) {
        setCustomFieldsOnCreate(parameters, fluentCreate);
      }

      Issue issue = fluentCreate.execute();

      log.info("Script execution finished with status SUCCESS");

      return JiraExecutionData.builder()
          .executionStatus(ExecutionStatus.SUCCESS)
          .jiraAction(JiraAction.CREATE_TICKET)
          .errorMessage("Created Jira ticket " + issue.getKey())
          .issueId(issue.getId())
          .issueKey(issue.getKey())
          .issueUrl(getIssueUrl(parameters.getJiraConfig(), issue.getKey()))
          .jiraIssueData(JiraIssueData.builder().description(issue.getDescription()).build())
          .build();
    } catch (JiraException e) {
      log.error("Unable to create a new Jira ticket", e);
      return JiraExecutionData.builder()
          .executionStatus(ExecutionStatus.FAILED)
          .errorMessage(
              "Unable to create a new Jira ticket. " + ExceptionUtils.getMessage(e) + " " + extractResponseMessage(e))
          .jiraServerResponse(extractResponseMessage(e))
          .build();
    } catch (WingsException e) {
      return JiraExecutionData.builder()
          .executionStatus(ExecutionStatus.FAILED)
          .errorMessage(ExceptionUtils.getMessage(e))
          .build();
    }
  }

  void setCustomFieldsOnCreate(JiraTaskParameters parameters, Issue.FluentCreate fluentCreate) {
    TimeTracking timeTracking = new TimeTracking();
    for (Entry<String, JiraCustomFieldValue> customField : parameters.getCustomFields().entrySet()) {
      if (customField.getKey().equals("TimeTracking:OriginalEstimate")) {
        timeTracking.setOriginalEstimate((String) getCustomFieldValue(customField));
      } else if (customField.getKey().equals("TimeTracking:RemainingEstimate")) {
        timeTracking.setRemainingEstimate((String) getCustomFieldValue(customField));
      } else {
        fluentCreate.field(customField.getKey(), getCustomFieldValue(customField));
      }
    }
    if (timeTracking.getOriginalEstimate() != null || timeTracking.getRemainingEstimate() != null) {
      fluentCreate.field(Field.TIME_TRACKING, timeTracking);
    }
  }

  private Object getCustomFieldValue(Entry<String, JiraCustomFieldValue> customFieldValueEntry) {
    String fieldName = customFieldValueEntry.getKey();
    String type = customFieldValueEntry.getValue().getFieldType();
    String fieldValue = customFieldValueEntry.getValue().getFieldValue();

    switch (type) {
      case "option":
      case RESOLUTION: {
        return new ValueTuple("id", fieldValue);
      }
      case "number":
        return Double.parseDouble(fieldValue);
      case "date":
      case "string":
      case "any":
        return fieldValue;
      case "timetracking":
        return fieldValue.replace(" ", "").replace("w", "w ").replace("d", "d ").replace("h", "h ").trim();
      case "datetime":
        return new Timestamp(Long.parseLong(fieldValue));
      case "multiselect":
        List<ValueTuple> valueTuples = new ArrayList<>();
        List<String> valueList = Arrays.asList(fieldValue.split(","));
        for (String value : valueList) {
          ValueTuple valueTuple = new ValueTuple("id", value);
          valueTuples.add(valueTuple);
        }
        return valueTuples;
      case "array":
        return Arrays.asList(fieldValue.split(" "));

      default:
        throw new InvalidRequestException("FieldType " + type + "not supported in Harness for " + fieldName);
    }
  }

  @VisibleForTesting
  protected DelegateResponseData checkJiraApproval(JiraTaskParameters parameters) {
    Issue issue;
    log.info("Checking approval for IssueId = {}", parameters.getIssueId());
    try {
      JiraClient jira = getJiraClient(parameters);
      issue = jira.getIssue(parameters.getIssueId());
    } catch (JiraException e) {
      String errorMessage = "Encoutered a problem while fetching Jira Issue " + parameters.getIssueId();
      if (e.getCause() != null) {
        errorMessage += " Reason: " + e.getCause().getMessage();
        if (e.getCause() instanceof RestException && ((RestException) e.getCause()).getHttpStatusCode() == 404) {
          // Fail if the jira issue is not returned
          log.error(errorMessage, e);
          return JiraExecutionData.builder().executionStatus(ExecutionStatus.FAILED).errorMessage(errorMessage).build();
        }
      }
      if (isWarningException(e)) {
        log.warn(errorMessage);
      } else {
        log.error(errorMessage, e);
      }
      return JiraExecutionData.builder().executionStatus(ExecutionStatus.PAUSED).errorMessage(errorMessage).build();
    }

    log.info("Issue fetched successfully for {}", parameters.getIssueId());
    String approvalFieldValue = null;
    if (EmptyPredicate.isNotEmpty(parameters.getApprovalField())) {
      Map<String, String> fieldMap = (Map<String, String>) issue.getField(parameters.getApprovalField());
      approvalFieldValue = fieldMap.get(JIRA_APPROVAL_FIELD_KEY);
    }

    String rejectionFieldValue = null;
    if (EmptyPredicate.isNotEmpty(parameters.getRejectionField())) {
      Map<String, String> fieldMap = (Map<String, String>) issue.getField(parameters.getRejectionField());
      rejectionFieldValue = fieldMap.get(JIRA_APPROVAL_FIELD_KEY);
    }

    log.info("IssueId: {}, approvalField: {}, approvalFieldValue: {}, rejectionField: {}, rejectionFieldValue: {}",
        parameters.getIssueId(), parameters.getApprovalField(), approvalFieldValue, parameters.getRejectionField(),
        rejectionFieldValue);

    if (EmptyPredicate.isNotEmpty(approvalFieldValue)
        && StringUtils.equalsIgnoreCase(approvalFieldValue, parameters.getApprovalValue())) {
      log.info("IssueId: {} Approved", parameters.getIssueId());
      return JiraExecutionData.builder()
          .currentStatus(approvalFieldValue)
          .executionStatus(ExecutionStatus.SUCCESS)
          .build();
    }

    if (EmptyPredicate.isNotEmpty(rejectionFieldValue)
        && StringUtils.equalsIgnoreCase(rejectionFieldValue, parameters.getRejectionValue())) {
      log.info("IssueId: {} Rejected", parameters.getIssueId());
      return JiraExecutionData.builder()
          .currentStatus(rejectionFieldValue)
          .executionStatus(ExecutionStatus.REJECTED)
          .build();
    }

    return JiraExecutionData.builder()
        .currentStatus(approvalFieldValue)
        .executionStatus(ExecutionStatus.PAUSED)
        .build();
  }

  // IDENTIFY WHICH EXCEPTION SHOULD BE LOGGED AS WARNING INSTEAD OF ERROR
  @VisibleForTesting
  boolean isWarningException(JiraException e) {
    return e.getCause() instanceof SSLHandshakeException;
  }

  public String getIssueUrl(JiraConfig jiraConfig, String issueKey) {
    try {
      URL issueUrl =
          new URL(jiraConfig.getBaseUrl() + (jiraConfig.getBaseUrl().endsWith("/") ? "" : "/") + "browse/" + issueKey);
      return issueUrl.toString();
    } catch (MalformedURLException e) {
      log.error("Incorrect url: " + e.getMessage(), e);
    }

    return null;
  }

  @VisibleForTesting
  protected JiraClient getJiraClient(JiraTaskParameters parameters) throws JiraException {
    JiraConfig jiraConfig = parameters.getJiraConfig();
    encryptionService.decrypt(jiraConfig, parameters.getEncryptionDetails(), false);
    BasicCredentials creds = new BasicCredentials(jiraConfig.getUsername(), new String(jiraConfig.getPassword()));
    String baseUrl =
        jiraConfig.getBaseUrl().endsWith("/") ? jiraConfig.getBaseUrl() : jiraConfig.getBaseUrl().concat("/");
    log.info(" Getting Jira Client from:  " + baseUrl);
    if (Http.getProxyHostName() != null && !Http.shouldUseNonProxy(baseUrl)) {
      log.info("Get Proxy enabled jira client", baseUrl);
      return new JiraClient(getProxyEnabledHttpClientForJira(), baseUrl, creds);
    } else {
      return new JiraClient(baseUrl, creds);
    }
  }

  protected io.harness.jira.JiraClient getNGJiraClient(JiraTaskParameters parameters) {
    JiraConfig jiraConfig = parameters.getJiraConfig();
    encryptionService.decrypt(jiraConfig, parameters.getEncryptionDetails(), false);
    String baseUrl =
        jiraConfig.getBaseUrl().endsWith("/") ? jiraConfig.getBaseUrl() : jiraConfig.getBaseUrl().concat("/");
    log.info(" Getting Jira Client from:  " + baseUrl);
    JiraInternalConfig jiraNGConfig =
        JiraInternalConfig.builder()
            .jiraUrl(baseUrl)
            .username(jiraConfig.getUsername())
            .password(new String(jiraConfig.getPassword()))
            .authToken(Credentials.basic(jiraConfig.getUsername(), new String(jiraConfig.getPassword())))
            .build();
    return new io.harness.jira.JiraClient(jiraNGConfig);
  }

  private HttpClient getProxyEnabledHttpClientForJira() {
    HttpHost proxyHost =
        new HttpHost(Http.getProxyHostName(), Integer.parseInt(Http.getProxyPort()), Http.getProxyScheme());

    if (Http.getProxyUserName() != null && Http.getProxyPassword() != null) {
      CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(
          new AuthScope(proxyHost), new UsernamePasswordCredentials(Http.getProxyUserName(), Http.getProxyPassword()));

      log.info("Proxy enable jira client with authentication");
      return HttpClientBuilder.create().setProxy(proxyHost).setDefaultCredentialsProvider(credentialsProvider).build();
    }
    return HttpClientBuilder.create().setProxy(proxyHost).build();
  }

  private DelegateResponseData fetchIssue(JiraTaskParameters parameters) {
    JiraConfig jiraConfig = parameters.getJiraConfig();
    encryptionService.decrypt(jiraConfig, parameters.getEncryptionDetails(), false);
    JiraClient jira;
    Issue issue;
    String approvalFieldValue = null;
    try {
      jira = getJiraClient(parameters);
      issue = jira.getIssue(parameters.getIssueId());
      String message = "Waiting for Approval on ticket: " + issue.getKey();
      if (EmptyPredicate.isNotEmpty(parameters.getApprovalField())) {
        Map<String, String> fieldMap = (Map<String, String>) issue.getField(parameters.getApprovalField());
        approvalFieldValue = fieldMap.get(JIRA_APPROVAL_FIELD_KEY);
      }

      return JiraExecutionData.builder()
          .executionStatus(ExecutionStatus.PAUSED)
          .issueUrl(getIssueUrl(jiraConfig, issue.getKey()))
          .issueKey(issue.getKey())
          .currentStatus(approvalFieldValue)
          .errorMessage(message)
          .jiraIssueData(JiraIssueData.builder().description(issue.getDescription()).build())
          .build();
    } catch (JiraException e) {
      String error = "Unable to fetch Jira Issue for Id: " + parameters.getIssueId() + "  " + extractResponseMessage(e);
      log.error(error, e);
      return JiraExecutionData.builder().executionStatus(ExecutionStatus.FAILED).errorMessage(error).build();
    }
  }

  @VisibleForTesting
  Map<String, String> extractFieldsFromCGParameters(JiraTaskParameters parameters, Map<String, String> userTypeFields) {
    Map<String, String> fields = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(parameters.getSummary())) {
      fields.put("Summary", parameters.getSummary());
    }
    if (EmptyPredicate.isNotEmpty(parameters.getPriority())) {
      fields.put("Priority", parameters.getPriority());
    }
    if (EmptyPredicate.isNotEmpty(parameters.getDescription())) {
      fields.put("Description", parameters.getDescription());
    }
    if (EmptyPredicate.isNotEmpty(parameters.getLabels())) {
      String labels = Joiner.on(",").join(parameters.getLabels());
      fields.put("Labels", labels);
    }
    if (EmptyPredicate.isNotEmpty(parameters.getCustomFields())) {
      for (Map.Entry<String, JiraCustomFieldValue> field : parameters.getCustomFields().entrySet()) {
        if ("TimeTracking:OriginalEstimate".equals(field.getKey())) {
          fields.put("Original Estimate", field.getValue().getFieldValue());
        } else if ("TimeTracking:RemainingEstimate".equals(field.getKey())) {
          fields.put("Remaining Estimate", field.getValue().getFieldValue());
        } else {
          fields.put(field.getKey(), field.getValue().getFieldValue());
        }
      }
    }
    if (EmptyPredicate.isNotEmpty(parameters.getComment())) {
      fields.put("Comment", parameters.getComment());
    }
    if (EmptyPredicate.isNotEmpty(userTypeFields)) {
      for (Map.Entry<String, String> userField : userTypeFields.entrySet()) {
        fields.put(userField.getKey(), userField.getValue());
      }
    }
    return fields;
  }
}
