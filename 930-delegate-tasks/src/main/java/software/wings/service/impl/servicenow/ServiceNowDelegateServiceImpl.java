/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.servicenow.ServiceNowTaskNgHelper.handleResponse;
import static io.harness.eraro.ErrorCode.SERVICENOW_ERROR;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ServiceNowException;
import io.harness.exception.WingsException;
import io.harness.network.Http;
import io.harness.servicenow.ServiceNowUtils;

import software.wings.api.ServiceNowExecutionData;
import software.wings.beans.ServiceNowConfig;
import software.wings.beans.approval.ServiceNowApprovalParams;
import software.wings.beans.servicenow.ServiceNowFields;
import software.wings.beans.servicenow.ServiceNowMetaDTO;
import software.wings.beans.servicenow.ServiceNowTaskParameters;
import software.wings.beans.servicenow.ServiceNowTicketType;
import software.wings.helpers.ext.servicenow.ServiceNowRestClient;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.servicenow.ServiceNowDelegateService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.text.WordUtils;
import retrofit2.Call;
import retrofit2.Converter;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@BreakDependencyOn("software.wings.service.impl.servicenow.ServiceNowServiceImpl")
public class ServiceNowDelegateServiceImpl implements ServiceNowDelegateService {
  private static final String LABEL = "label";
  private static final String STATE = "state";
  private static final String VALUE = "value";
  @Inject private EncryptionService encryptionService;
  private static final String NO_ACCESS_SYS_CHOICE =
      "Can not read field: %s. User might not have explicit read access to sys_choice table";
  static final long TIME_OUT = 60;

  @Override
  public boolean validateConnector(ServiceNowTaskParameters taskParameters) {
    ServiceNowConfig config = taskParameters.getServiceNowConfig();
    final Call<JsonNode> request;
    if (config.getPassword() == null) {
      throw new ServiceNowException("Could not decrypt password. Secret might be deleted", SERVICENOW_ERROR, USER);
    }
    request = getRestClient(taskParameters)
                  .validateConnection(Credentials.basic(config.getUsername(), new String(config.getPassword())));
    Response<JsonNode> response = null;
    try {
      response = request.execute();
      handleResponse(response, "Failed to validate ServiceNow credentials");
      return true;
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to authenticate to servicenow. ");
      if (e instanceof SocketTimeoutException) {
        throw new WingsException(ErrorCode.INVALID_TICKETING_SERVER, USER, e)
            .addParam("message",
                e.getMessage() + "."
                    + "SocketTimeout: ServiceNow server may not be running");
      }
      throw new ServiceNowException(ExceptionUtils.getMessage(e), SERVICENOW_ERROR, USER, e);
    }
  }

  @Override
  public List<ServiceNowMetaDTO> getStates(ServiceNowTaskParameters taskParameters) {
    final Call<JsonNode> request;
    ServiceNowConfig config = taskParameters.getServiceNowConfig();
    switch (taskParameters.getTicketType()) {
      case INCIDENT:
        request = getRestClient(taskParameters)
                      .getIncidentStates(Credentials.basic(config.getUsername(), new String(config.getPassword())));
        break;
      case PROBLEM:
        request = getRestClient(taskParameters)
                      .getProblemStates(Credentials.basic(config.getUsername(), new String(config.getPassword())));
        break;
      case CHANGE_REQUEST:
        request =
            getRestClient(taskParameters)
                .getChangeRequestStates(Credentials.basic(config.getUsername(), new String(config.getPassword())));
        break;
      case CHANGE_TASK:
        request = getRestClient(taskParameters)
                      .getChangeTaskStates(Credentials.basic(config.getUsername(), new String(config.getPassword())));
        break;
      default:
        throw new ServiceNowException(
            "Invalid ticket type : " + taskParameters.getTicketType(), SERVICENOW_ERROR, USER);
    }

    return executeRequest(request, taskParameters.getTicketType().name());
  }

  @Override
  public List<ServiceNowMetaDTO> getApprovalStates(ServiceNowTaskParameters taskParameters) {
    if (taskParameters.getTicketType() != ServiceNowTicketType.CHANGE_REQUEST) {
      throw new InvalidRequestException("Approval states are only valid for issue type Change");
    }
    final Call<JsonNode> request;
    request = getRestClient(taskParameters)
                  .getChangeApprovalTypes(Credentials.basic(taskParameters.getServiceNowConfig().getUsername(),
                      new String(taskParameters.getServiceNowConfig().getPassword())));
    return executeRequest(request, taskParameters.getTicketType().name());
  }

  private List<ServiceNowMetaDTO> executeRequest(Call<JsonNode> request, String ticketType) {
    Response<JsonNode> response = null;
    try {
      response = request.execute();
      log.info("Response received from serviceNow: {}", response);
      handleResponse(response, "Failed to fetch States from serviceNow");
      List<ServiceNowMetaDTO> responseStates = new ArrayList<>();

      JsonNode responseObj = response.body().get("result");
      if (responseObj != null && responseObj.isArray()) {
        for (JsonNode stateObj : responseObj) {
          ServiceNowMetaDTO serviceNowMetaDTO = ServiceNowMetaDTO.builder()
                                                    .displayName(getTextValue(stateObj, LABEL, STATE))
                                                    .id(getTextValue(stateObj, VALUE, STATE))
                                                    .build();
          responseStates.add(serviceNowMetaDTO);
        }
      } else {
        // todo: Check this error message with srinivas
        throw new WingsException(SERVICENOW_ERROR, USER).addParam("message", "");
      }
      log.info("States for ticketType {}: {}", ticketType, responseStates);
      return responseStates;
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      String errorMsg = "Error in fetching states from serviceNow";
      throw new ServiceNowException(errorMsg + ExceptionUtils.getMessage(e), SERVICENOW_ERROR, USER, e);
    }
  }

  @VisibleForTesting
  String getTextValue(JsonNode element, String property, String field) {
    if (element.get(property) != null) {
      return element.get(property).textValue();
    } else {
      throw new ServiceNowException(String.format(NO_ACCESS_SYS_CHOICE, field), SERVICENOW_ERROR, USER);
    }
  }

  @Override
  public Map<String, List<ServiceNowMetaDTO>> getCreateMeta(ServiceNowTaskParameters taskParameters) {
    Response<JsonNode> response = null;
    Map<String, List<ServiceNowMetaDTO>> responseMap = new HashMap<>();
    switch (taskParameters.getTicketType()) {
      case INCIDENT:
        responseMap.put("impact", getImpacts(taskParameters));
        responseMap.put("urgency", getUrgency(taskParameters));
        return responseMap;
      case PROBLEM:
        return new HashMap<>();
      case CHANGE_REQUEST:
        responseMap.put("impact", getImpacts(taskParameters));
        responseMap.put("priority", getPriority(taskParameters));
        responseMap.put("risk", getRisk(taskParameters));
        responseMap.put("changeRequestType", getChangeRequestType(taskParameters));
        return responseMap;
      case CHANGE_TASK:
        responseMap.put("changeRequestNumber", new ArrayList<>());
        responseMap.put("changeTaskType", getChangeTaskType(taskParameters));
        return responseMap;
      default:
        throw new ServiceNowException(
            "Invalid ticket type : " + taskParameters.getTicketType(), SERVICENOW_ERROR, USER);
    }
  }

  @Override
  public List<ServiceNowMetaDTO> getAdditionalFields(ServiceNowTaskParameters taskParameters) {
    ServiceNowConfig config = taskParameters.getServiceNowConfig();
    List<String> alreadySupportedFieldNames =
        Arrays.stream(ServiceNowFields.values()).map(ServiceNowFields::getJsonBodyName).collect(Collectors.toList());

    Call<JsonNode> request =
        getRestClient(taskParameters)
            .getAdditionalFields(Credentials.basic(config.getUsername(), new String(config.getPassword())),
                taskParameters.getTicketType().toString().toLowerCase());
    Response<JsonNode> response = null;
    List<ServiceNowMetaDTO> fields = new ArrayList<>();
    try {
      response = request.execute();
      log.info("Response received from serviceNow: {}", response);
      handleResponse(response,
          "Failed to fetch Additional fields for ticketType : " + taskParameters.getTicketType() + " from serviceNow");
      JsonNode responseObj = response.body().get("result");
      if (responseObj.isArray()) {
        for (JsonNode fieldObj : responseObj) {
          if (!alreadySupportedFieldNames.contains(fieldObj.get("name").textValue())) {
            if (taskParameters.getTypeFilter() == null) {
              ServiceNowMetaDTO field = ServiceNowMetaDTO.builder()
                                            .displayName(WordUtils.capitalizeFully(fieldObj.get("label").textValue()))
                                            .id(fieldObj.get("name").textValue())
                                            .build();
              fields.add(field);
            } else {
              if (taskParameters.getTypeFilter().getSnowInternalTypes().contains(
                      fieldObj.get("internalType").textValue())) {
                ServiceNowMetaDTO field = ServiceNowMetaDTO.builder()
                                              .displayName(WordUtils.capitalizeFully(fieldObj.get("label").textValue()))
                                              .id(fieldObj.get("name").textValue())
                                              .build();
                fields.add(field);
              }
            }
          }
        }
        return fields;
      } else {
        throw new ServiceNowException("Failed to fetch additional fields for ticket type "
                + taskParameters.getTicketType() + " response: " + response,
            SERVICENOW_ERROR, USER);
      }
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      String errorMsg = "Failed to fetch additional fields for ticket type " + taskParameters.getTicketType();
      throw new ServiceNowException(errorMsg + " " + ExceptionUtils.getMessage(e), SERVICENOW_ERROR, USER, e);
    }
  }

  private List<ServiceNowMetaDTO> getImpacts(ServiceNowTaskParameters taskParameters) {
    ServiceNowConfig config = taskParameters.getServiceNowConfig();
    Call<JsonNode> request = getRestClient(taskParameters)
                                 .getImpact(Credentials.basic(config.getUsername(), new String(config.getPassword())));
    return handleGetCallForFields(request, "impact");
  }

  private List<ServiceNowMetaDTO> getChangeRequestType(ServiceNowTaskParameters taskParameters) {
    ServiceNowConfig config = taskParameters.getServiceNowConfig();
    Call<JsonNode> request =
        getRestClient(taskParameters)
            .getChangeRequestTypes(Credentials.basic(config.getUsername(), new String(config.getPassword())));
    return handleGetCallForFields(request, "changeRequestType");
  }

  private List<ServiceNowMetaDTO> getChangeTaskType(ServiceNowTaskParameters taskParameters) {
    ServiceNowConfig config = taskParameters.getServiceNowConfig();
    Call<JsonNode> request =
        getRestClient(taskParameters)
            .getChangeTaskTypes(Credentials.basic(config.getUsername(), new String(config.getPassword())));
    return handleGetCallForFields(request, "changeTaskType");
  }

  private List<ServiceNowMetaDTO> getPriority(ServiceNowTaskParameters taskParameters) {
    ServiceNowConfig config = taskParameters.getServiceNowConfig();
    Call<JsonNode> request =
        getRestClient(taskParameters)
            .getPriority(Credentials.basic(config.getUsername(), new String(config.getPassword())));
    return handleGetCallForFields(request, "priority");
  }

  private List<ServiceNowMetaDTO> getRisk(ServiceNowTaskParameters taskParameters) {
    ServiceNowConfig config = taskParameters.getServiceNowConfig();
    Call<JsonNode> request = getRestClient(taskParameters)
                                 .getRisk(Credentials.basic(config.getUsername(), new String(config.getPassword())));
    return handleGetCallForFields(request, "risk");
  }

  private List<ServiceNowMetaDTO> getUrgency(ServiceNowTaskParameters taskParameters) {
    ServiceNowConfig config = taskParameters.getServiceNowConfig();
    Call<JsonNode> request = getRestClient(taskParameters)
                                 .getUrgency(Credentials.basic(config.getUsername(), new String(config.getPassword())));
    return handleGetCallForFields(request, "urgency");
  }

  private List<ServiceNowMetaDTO> handleGetCallForFields(Call<JsonNode> request, String field) {
    Response<JsonNode> response;
    try {
      response = request.execute();
      handleResponse(response, "Failed to fetch " + field + " from serviceNow");
      JsonNode responseObj = response.body().get("result");
      List<ServiceNowMetaDTO> fields = new ArrayList<>();
      if (responseObj.isArray()) {
        for (JsonNode fieldObj : responseObj) {
          ServiceNowMetaDTO serviceNowMetaDTO = ServiceNowMetaDTO.builder()
                                                    .displayName(getTextValue(fieldObj, LABEL, field))
                                                    .id(getTextValue(fieldObj, VALUE, field))
                                                    .build();
          fields.add(serviceNowMetaDTO);
        }
        return fields;
      } else {
        throw new ServiceNowException(
            "Response for fetching " + field + " is not an array. Response: " + response, SERVICENOW_ERROR, USER);
      }
    } catch (WingsException we) {
      throw we;
    } catch (Exception e) {
      String errorMsg = "Error in fetching " + field + " from serviceNow. ";
      throw new ServiceNowException(errorMsg + ExceptionUtils.getMessage(e), SERVICENOW_ERROR, USER, e);
    }
  }

  private JsonNode getIssue(ServiceNowTaskParameters taskParameters) {
    final Call<JsonNode> request;
    ServiceNowConfig config = taskParameters.getServiceNowConfig();
    String query = "number=" + taskParameters.getIssueNumber();
    request = getRestClient(taskParameters)
                  .getIssue(Credentials.basic(config.getUsername(), new String(config.getPassword())),
                      taskParameters.getTicketType().toString().toLowerCase(), query, "all");

    Response<JsonNode> response = null;
    try {
      response = request.execute();
      log.info("Response received from serviceNow: {}", response);
      handleResponse(response, "Failed to fetch IssueId : " + taskParameters.getIssueNumber() + " from serviceNow");
      JsonNode responseObj = response.body().get("result");
      if (responseObj.isArray()) {
        JsonNode issueObj = responseObj.get(0);
        if (issueObj != null) {
          return issueObj;
        } else {
          String errorMsg = "Error in fetching issue " + taskParameters.getIssueNumber() + " .Issue does not exist";
          throw new WingsException(SERVICENOW_ERROR, USER).addParam("message", errorMsg);
        }

      } else {
        throw new ServiceNowException(
            "Failed to fetch issueNumber " + taskParameters.getIssueNumber() + "response: " + response,
            SERVICENOW_ERROR, USER);
      }
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      String errorMsg = "Error in fetching issueNumber " + taskParameters.getIssueNumber();
      throw new ServiceNowException(errorMsg + " " + ExceptionUtils.getMessage(e), SERVICENOW_ERROR, USER, e);
    }
  }

  @Override
  @SuppressWarnings("PMD")
  public ServiceNowExecutionData getIssueUrl(
      ServiceNowTaskParameters taskParameters, ServiceNowApprovalParams approvalParams) {
    JsonNode issueObj = getIssue(taskParameters);
    String issueId = issueObj.get("sys_id").get("display_value").asText();
    String issueUrl = ServiceNowUtils.prepareTicketUrlFromTicketId(
        getBaseUrl(taskParameters.getServiceNowConfig()), issueId, taskParameters.getTicketType().toString());

    Set<String> serviceNowFields = approvalParams.getAllCriteriaFields();

    if (EmptyPredicate.isNotEmpty(serviceNowFields)) {
      Map<String, String> issueStatus = serviceNowFields.stream().collect(
          Collectors.toMap(field -> field, field -> issueObj.get(field).get("display_value").asText()));
      try {
        issueStatus.putAll(getIssueValues(issueObj, approvalParams.getChangeWindowTimeFields()));
      } catch (NullPointerException npe) {
        throw new ServiceNowException("Time Window fields given are invalid", SERVICENOW_ERROR, USER, npe);
      }
      return ServiceNowExecutionData.builder()
          .issueUrl(issueUrl)
          .currentState(extractCurrentStatusFromIssue(issueObj, approvalParams))
          .message(approvalParams.isChangeWindowPresent() ? "Start time: "
                      + issueObj.get(approvalParams.getChangeWindowStartField()).get("display_value").asText()
                                                          : "")
          .currentStatus(issueStatus)
          .build();
    }

    return ServiceNowExecutionData.builder()
        .issueUrl(issueUrl)
        .message("Approval and Rejection criteria empty in Approval State")
        .build();
  }

  // For fields we need values instead of display values
  @Override
  public Map<String, String> getIssueValues(JsonNode issueObj, Set<String> timeFields) {
    return timeFields.stream().collect(
        Collectors.toMap(field -> field, field -> issueObj.get(field).get("value").asText()));
  }

  private String extractCurrentStatusFromIssue(JsonNode issueObj, ServiceNowApprovalParams approvalParams) {
    Set<String> criteriaFields = approvalParams.getAllCriteriaFields();
    if (EmptyPredicate.isNotEmpty(criteriaFields)) {
      return criteriaFields.stream()
          .map(field -> StringUtils.capitalize(field) + " is " + issueObj.get(field).get("display_value").asText())
          .collect(Collectors.joining(",\n"));
    }
    return null;
  }

  public ServiceNowRestClient getRestClient(ServiceNowTaskParameters taskParameters) {
    ServiceNowConfig config = taskParameters.getServiceNowConfig();
    encryptionService.decrypt(config, taskParameters.getEncryptionDetails(), false);
    return getRetrofit(config, JacksonConverterFactory.create()).create(ServiceNowRestClient.class);
  }

  public static Retrofit getRetrofit(ServiceNowConfig config, Converter.Factory converterFactory) {
    String baseUrl = getBaseUrl(config);
    return new Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(converterFactory)
        .client(getHttpClientWithIncreasedTimeout(baseUrl, config.isCertValidationRequired()))
        .build();
  }

  public static OkHttpClient getHttpClientWithIncreasedTimeout(String baseUrl, boolean certValidationRequired) {
    return Http.getOkHttpClient(baseUrl, certValidationRequired)
        .newBuilder()
        .connectTimeout(TIME_OUT, TimeUnit.SECONDS)
        .readTimeout(TIME_OUT, TimeUnit.SECONDS)
        .build();
  }

  public static String getBaseUrl(ServiceNowConfig snowConfig) {
    String baseUrl = snowConfig.getBaseUrl().trim();
    if (!baseUrl.endsWith("/")) {
      baseUrl += "/";
    }
    return baseUrl;
  }
}
