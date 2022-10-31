/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.servicenow.ServiceNowTaskNgHelper.handleResponse;
import static io.harness.eraro.ErrorCode.SERVICENOW_ERROR;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.servicenow.ServiceNowTicketType.CHANGE_TASK;
import static software.wings.service.impl.servicenow.ServiceNowDelegateServiceImpl.getBaseUrl;
import static software.wings.service.impl.servicenow.ServiceNowDelegateServiceImpl.getRetrofit;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ServiceNowException;
import io.harness.exception.WingsException;
import io.harness.servicenow.ServiceNowUtils;

import software.wings.api.ServiceNowExecutionData;
import software.wings.api.ServiceNowImportSetResponse;
import software.wings.api.ServiceNowImportSetResult;
import software.wings.beans.ServiceNowConfig;
import software.wings.beans.servicenow.ServiceNowFields;
import software.wings.beans.servicenow.ServiceNowTaskParameters;
import software.wings.helpers.ext.servicenow.ServiceNowRestClient;
import software.wings.service.intfc.security.EncryptionService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.converter.jackson.JacksonConverterFactory;

@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ServicenowTask extends AbstractDelegateRunnableTask {
  @Inject private EncryptionService encryptionService;

  public ServicenowTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    return run((ServiceNowTaskParameters) parameters);
  }

  private DelegateResponseData run(ServiceNowTaskParameters parameters) {
    ServiceNowAction action = parameters.getAction();

    DelegateResponseData responseData = null;

    log.info("Executing ServiceNowTask. Action: {}, IssueNumber: {}", action, parameters.getIssueNumber());

    switch (parameters.getAction()) {
      case CREATE:
        return createServiceNowTicket(parameters);

      case UPDATE:
        return updateServiceNowTicket(parameters);
      case IMPORT_SET:
        return createImportSets(parameters);
      default:
        String errorMsg = "Invalid ServiceNow delegate Task Action " + parameters.getAction();
        log.error(errorMsg);
        throw new InvalidRequestException(errorMsg, ErrorCode.SERVICENOW_ERROR, WingsException.USER);
    }
  }

  private DelegateResponseData createServiceNowTicket(ServiceNowTaskParameters parameters) {
    ServiceNowConfig config = parameters.getServiceNowConfig();

    Map<String, String> body = new HashMap<>();
    parameters.getFields().forEach((key, value) -> {
      if (EmptyPredicate.isNotEmpty(value)) {
        body.put(key.getJsonBodyName(), value);
      }
    });
    parameters.getAdditionalFields().forEach((key, value) -> {
      if (EmptyPredicate.isNotEmpty(value)) {
        body.put(key, value);
      }
    });

    final Call<JsonNode> request;
    request = getRestClient(parameters)
                  .createTicket(Credentials.basic(config.getUsername(), new String(config.getPassword())),
                      parameters.getTicketType().toString().toLowerCase(), "all", "number,sys_id", body);

    Response<JsonNode> response = null;
    try {
      log.info("Body of the request made to the ServiceNow server: {}", body);
      response = request.execute();
      log.info("Response received from serviceNow: {}", response);
      handleResponse(response, "Failed to create ServiceNow ticket");
      JsonNode responseObj = response.body().get("result");
      String issueNumber = responseObj.get("number").get("display_value").asText();
      String issueId = responseObj.get("sys_id").get("display_value").asText();
      String issueUrl = ServiceNowUtils.prepareTicketUrlFromTicketId(
          getBaseUrl(parameters.getServiceNowConfig()), issueId, parameters.getTicketType().toString());
      String responseMsg = "Created ServiceNow ticket: " + issueNumber;
      return ServiceNowExecutionData.builder()
          .issueNumber(issueNumber)
          .issueId(issueId)
          .ticketType(parameters.getTicketType())
          .issueUrl(issueUrl)
          .responseMsg(responseMsg)
          .executionStatus(ExecutionStatus.SUCCESS)
          .build();
    } catch (WingsException we) {
      throw we;
    } catch (Exception e) {
      String errorMsg = "Error while creating serviceNow ticket: ";
      throw new ServiceNowException(errorMsg + ExceptionUtils.getMessage(e), SERVICENOW_ERROR, USER, e);
    }
  }

  private DelegateResponseData createImportSets(ServiceNowTaskParameters parameters) {
    Gson gson = new Gson();
    Map body = gson.fromJson(parameters.getJsonBody(), HashMap.class);
    ServiceNowConfig config = parameters.getServiceNowConfig();
    final Call<JsonNode> request;
    request = getRestClient(parameters)
                  .createImportSet(Credentials.basic(config.getUsername(), new String(config.getPassword())),
                      parameters.getImportSetTableName(), "all", body);

    Response<JsonNode> apiResponse = null;
    try {
      apiResponse = request.execute();
      log.info("Response received from serviceNow: {}", apiResponse);
      handleResponse(apiResponse, "Failed to create ServiceNow ticket");
      String importSetNumber = apiResponse.body().get("import_set").textValue();
      ObjectMapper mapper = new ObjectMapper();

      ServiceNowImportSetResponse response =
          mapper.readValue(apiResponse.body().toString(), ServiceNowImportSetResponse.class);
      List<String> transformationValues = new ArrayList<>();

      for (ServiceNowImportSetResult result : response.getResult()) {
        transformationValues.add(result.getDisplayValue());
      }
      Collections.sort(transformationValues);

      String responseMsg = "Created import Set : " + importSetNumber;
      return ServiceNowExecutionData.builder()
          .ticketType(parameters.getTicketType())
          .transformationDetails(response)
          .transformationValues(transformationValues)
          .responseMsg(responseMsg)
          .executionStatus(ExecutionStatus.SUCCESS)
          .build();
    } catch (WingsException we) {
      throw we;
    } catch (Exception e) {
      String errorMsg = "Error while creating import set: ";
      throw new ServiceNowException(errorMsg + ExceptionUtils.getMessage(e), SERVICENOW_ERROR, USER, e);
    }
  }

  private DelegateResponseData updateServiceNowTicket(ServiceNowTaskParameters parameters) {
    if (parameters.getTicketType() == CHANGE_TASK && parameters.isUpdateMultiple()) {
      return updateAllChangeTaskTickets(parameters);
    }

    ServiceNowConfig config = parameters.getServiceNowConfig();
    Map<String, String> body = new HashMap<>();

    parameters.getFields().forEach((key, value) -> {
      if (EmptyPredicate.isNotEmpty(value)) {
        body.put(key.getJsonBodyName(), value);
      }
    });
    parameters.getAdditionalFields().forEach((key, value) -> {
      if (EmptyPredicate.isNotEmpty(value)) {
        body.put(key, value);
      }
    });

    if (parameters.getIssueId() == null && parameters.getIssueNumber() != null) {
      setIssueIdFromIssueNumber(parameters, config);
    }

    final Call<JsonNode> request;
    request = getRestClient(parameters)
                  .updateTicket(Credentials.basic(config.getUsername(), new String(config.getPassword())),
                      parameters.getTicketType().toString().toLowerCase(), parameters.getIssueId(), "all",
                      "number,sys_id", body);

    Response<JsonNode> response = null;
    try {
      response = request.execute();
      log.info("Response received from serviceNow: {}", response);
      handleResponse(response, "Failed to update ServiceNow ticket");
      JsonNode responseObj = response.body().get("result");
      String issueNumber = responseObj.get("number").get("display_value").asText();
      String issueId = responseObj.get("sys_id").get("display_value").asText();
      String issueUrl = ServiceNowUtils.prepareTicketUrlFromTicketId(
          getBaseUrl(parameters.getServiceNowConfig()), issueId, parameters.getTicketType().toString());
      String responseMsg = "Updated ServiceNow ticket: " + issueNumber;
      return ServiceNowExecutionData.builder()
          .issueNumber(issueNumber)
          .issueId(issueId)
          .issueUrl(issueUrl)
          .ticketType(parameters.getTicketType())
          .responseMsg(responseMsg)
          .executionStatus(ExecutionStatus.SUCCESS)
          .build();
    } catch (WingsException we) {
      throw we;
    } catch (Exception e) {
      String errorMsg = "Error while updating serviceNow ticket: " + parameters.getIssueNumber();
      throw new ServiceNowException(errorMsg + ExceptionUtils.getMessage(e), SERVICENOW_ERROR, USER, e);
    }
  }

  private DelegateResponseData updateAllChangeTaskTickets(ServiceNowTaskParameters parameters) {
    if (!parameters.getFields().containsKey(ServiceNowFields.CHANGE_REQUEST_NUMBER)
        || EmptyPredicate.isEmpty(parameters.getFields().get(ServiceNowFields.CHANGE_REQUEST_NUMBER))) {
      throw new InvalidRequestException(
          "Change Request Number is required to update change tasks", SERVICENOW_ERROR, USER);
    }

    List<String> changeTaskIdsToUpdate = fetchChangeTasksFromCR(parameters);
    String changeRequestNumber = parameters.getFields().get(ServiceNowFields.CHANGE_REQUEST_NUMBER);
    if (EmptyPredicate.isEmpty(changeTaskIdsToUpdate)) {
      return ServiceNowExecutionData.builder()
          .issueNumber(changeRequestNumber)
          .ticketType(parameters.getTicketType())
          .responseMsg("No change tasks to update for: " + changeRequestNumber)
          .executionStatus(ExecutionStatus.SUCCESS)
          .build();
    }
    Map<String, String> body = new HashMap<>();
    parameters.getFields().forEach((key, value) -> {
      if (EmptyPredicate.isNotEmpty(value)) {
        body.put(key.getJsonBodyName(), value);
      }
    });
    parameters.getAdditionalFields().forEach((key, value) -> {
      if (EmptyPredicate.isNotEmpty(value)) {
        body.put(key, value);
      }
    });
    body.remove(ServiceNowFields.CHANGE_REQUEST_NUMBER.getJsonBodyName());
    body.remove(ServiceNowFields.CHANGE_TASK_TYPE.getJsonBodyName());
    parameters.getAdditionalFields().forEach(body::put);

    ServiceNowConfig config = parameters.getServiceNowConfig();

    Response<JsonNode> response = null;
    List<String> updateChangeTaskNumbers = new ArrayList<>();
    List<String> updatedCjhangeTaskUrls = new ArrayList<>();
    Call<JsonNode> request;

    for (String changeTaskId : changeTaskIdsToUpdate) {
      try {
        request = getRestClient(parameters)
                      .updateTicket(Credentials.basic(config.getUsername(), new String(config.getPassword())),
                          CHANGE_TASK.toString().toLowerCase(), changeTaskId, "all", "number,sys_id", body);

        response = request.execute();
        log.info("Response received from serviceNow: {}", response);
        handleResponse(response, "Failed to update ServiceNow ticket");
        JsonNode responseObj = response.body().get("result");
        String issueNumber = responseObj.get("number").get("display_value").asText();
        String issueId = responseObj.get("sys_id").get("display_value").asText();
        String issueUrl = ServiceNowUtils.prepareTicketUrlFromTicketId(
            getBaseUrl(parameters.getServiceNowConfig()), issueId, parameters.getTicketType().toString());

        log.info("Successfully updated ticket : " + issueNumber);
        updateChangeTaskNumbers.add(issueNumber);
        updatedCjhangeTaskUrls.add(issueUrl);
      } catch (WingsException we) {
        throw we;
      } catch (Exception e) {
        String errorMsg = "Error while updating serviceNow task: " + changeTaskId;
        throw new ServiceNowException(errorMsg + ExceptionUtils.getMessage(e), SERVICENOW_ERROR, USER, e);
      }
    }
    return ServiceNowExecutionData.builder()
        .issueNumber(parameters.getFields().get(ServiceNowFields.CHANGE_REQUEST_NUMBER))
        .issueUrl(updatedCjhangeTaskUrls.toString().replaceAll("[\\[\\]]", ""))
        .ticketType(parameters.getTicketType())
        .responseMsg("Updated Service Now tasks " + updateChangeTaskNumbers.toString().replaceAll("[\\[\\]]", ""))
        .executionStatus(ExecutionStatus.SUCCESS)
        .build();
  }

  private List<String> fetchChangeTasksFromCR(ServiceNowTaskParameters parameters) {
    ServiceNowConfig config = parameters.getServiceNowConfig();
    final Call<JsonNode> request;
    List<String> changeTaskIds = new ArrayList<>();
    String changeRequestNumber = parameters.getFields().get(ServiceNowFields.CHANGE_REQUEST_NUMBER);

    request = getRestClient(parameters)
                  .fetchChangeTasksFromCR(Credentials.basic(config.getUsername(), new String(config.getPassword())),
                      parameters.getTicketType().toString().toLowerCase(), "number,sys_id,change_task_type",
                      "change_request.number=" + changeRequestNumber);

    Response<JsonNode> response = null;
    try {
      response = request.execute();
      log.info("Response received from serviceNow: {}", response);
      handleResponse(response, "Failed to update ServiceNow ticket");
      JsonNode responseObj = response.body().get("result");
      if (responseObj.isArray()) {
        for (JsonNode changeTaskObj : responseObj) {
          if (parameters.getFields().containsKey(ServiceNowFields.CHANGE_TASK_TYPE)
              && EmptyPredicate.isNotEmpty(parameters.getFields().get(ServiceNowFields.CHANGE_TASK_TYPE))) {
            if (parameters.getFields()
                    .get(ServiceNowFields.CHANGE_TASK_TYPE)
                    .equalsIgnoreCase(changeTaskObj.get("change_task_type").textValue())) {
              changeTaskIds.add(changeTaskObj.get("sys_id").textValue());
            }
          } else {
            // update all ticket types
            changeTaskIds.add(changeTaskObj.get("sys_id").textValue());
          }
        }
        return changeTaskIds;
      } else {
        throw new ServiceNowException(
            "Response for fetching changeTasks is not an array. Response: " + response, SERVICENOW_ERROR, USER);
      }

    } catch (WingsException we) {
      throw we;
    } catch (Exception e) {
      String errorMsg = "Error while fetching change tasks for : " + changeRequestNumber;
      throw new ServiceNowException(errorMsg + ExceptionUtils.getMessage(e), SERVICENOW_ERROR, USER, e);
    }
  }

  private void setIssueIdFromIssueNumber(ServiceNowTaskParameters parameters, ServiceNowConfig config) {
    String query = "number=" + parameters.getIssueNumber();
    final Call<JsonNode> request =
        getRestClient(parameters)
            .getIssue(Credentials.basic(config.getUsername(), new String(config.getPassword())),
                parameters.getTicketType().toString().toLowerCase(), query, "all");
    Response<JsonNode> response = null;
    try {
      response = request.execute();
      log.info("Response received from serviceNow: {}", response);
      handleResponse(response, "Failed to fetch IssueId : " + parameters.getIssueNumber() + " from serviceNow");
      JsonNode responseObj = response.body().get("result");
      if (responseObj.isArray()) {
        if (responseObj.size() > 1) {
          String errorMsg =
              "Multiple issues found for " + parameters.getIssueNumber() + "Please enter unique issueNumber";
          throw new ServiceNowException(errorMsg, SERVICENOW_ERROR, USER);
        }
        JsonNode issueObj = responseObj.get(0);
        if (issueObj != null) {
          String issueId = issueObj.get("sys_id").get("display_value").asText();
          parameters.setIssueId(issueId);
        } else {
          String errorMsg = "Error in fetching issue " + parameters.getIssueNumber() + " .Issue does not exist";
          throw new ServiceNowException(errorMsg, SERVICENOW_ERROR, USER);
        }
      } else {
        throw new ServiceNowException(
            "Failed to fetch issueNumber " + parameters.getIssueNumber() + "response: " + response, SERVICENOW_ERROR,
            USER);
      }
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      String errorMsg = "Error in fetching issueNumber " + parameters.getIssueNumber();
      throw new ServiceNowException(errorMsg + ExceptionUtils.getMessage(e), SERVICENOW_ERROR, USER, e);
    }
  }

  protected ServiceNowRestClient getRestClient(ServiceNowTaskParameters taskParameters) {
    ServiceNowConfig config = taskParameters.getServiceNowConfig();
    encryptionService.decrypt(config, taskParameters.getEncryptionDetails(), false);
    return getRetrofit(config, JacksonConverterFactory.create()).create(ServiceNowRestClient.class);
  }
}
