/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.beans.connector.servicenow.ServiceNowConstants.CHANGE_TASK;
import static io.harness.delegate.beans.connector.servicenow.ServiceNowConstants.IGNOREFIELDS;
import static io.harness.delegate.beans.connector.servicenow.ServiceNowConstants.INVALID_SERVICE_NOW_CREDENTIALS;
import static io.harness.delegate.beans.connector.servicenow.ServiceNowConstants.ISSUE_NUMBER;
import static io.harness.delegate.beans.connector.servicenow.ServiceNowConstants.META;
import static io.harness.delegate.beans.connector.servicenow.ServiceNowConstants.NOT_FOUND;
import static io.harness.delegate.beans.connector.servicenow.ServiceNowConstants.QUERY_FOR_GETTING_CHANGE_TASK;
import static io.harness.delegate.beans.connector.servicenow.ServiceNowConstants.QUERY_FOR_GETTING_CHANGE_TASK_ALL;
import static io.harness.delegate.beans.connector.servicenow.ServiceNowConstants.RESULT;
import static io.harness.delegate.beans.connector.servicenow.ServiceNowConstants.RETURN_FIELDS;
import static io.harness.delegate.beans.connector.servicenow.ServiceNowConstants.SYS_ID;
import static io.harness.delegate.beans.connector.servicenow.ServiceNowConstants.SYS_NAME;
import static io.harness.delegate.beans.connector.servicenow.ServiceNowConstants.TEMPLATE;
import static io.harness.delegate.beans.connector.servicenow.ServiceNowConstants.TIME_OUT;
import static io.harness.delegate.beans.connector.servicenow.ServiceNowConstants.VALUE;
import static io.harness.delegate.task.servicenow.ServiceNowUtils.errorWhileUpdatingTicket;
import static io.harness.delegate.task.servicenow.ServiceNowUtils.failedToUpdateTicket;
import static io.harness.delegate.task.servicenow.ServiceNowUtils.isUnauthorizedError;
import static io.harness.eraro.ErrorCode.SERVICENOW_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.network.Http.getOkHttpClientBuilder;
import static io.harness.servicenow.ServiceNowUtils.getTicketUrlFromTicketIdOrNumber;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.ServiceNowException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.jackson.JsonNodeUtils;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.network.Http;
import io.harness.retry.RetryHelper;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serializer.JsonUtils;
import io.harness.servicenow.ChangeTaskUpdateMultiple;
import io.harness.servicenow.ServiceNowFieldAllowedValueNG;
import io.harness.servicenow.ServiceNowFieldNG;
import io.harness.servicenow.ServiceNowFieldNG.ServiceNowFieldNGBuilder;
import io.harness.servicenow.ServiceNowFieldSchemaNG;
import io.harness.servicenow.ServiceNowFieldTypeNG;
import io.harness.servicenow.ServiceNowFieldValueNG;
import io.harness.servicenow.ServiceNowImportSetResponseNG;
import io.harness.servicenow.ServiceNowImportSetResponseNG.ServiceNowImportSetResponseNGBuilder;
import io.harness.servicenow.ServiceNowImportSetTransformMapResult;
import io.harness.servicenow.ServiceNowStagingTable;
import io.harness.servicenow.ServiceNowTemplate;
import io.harness.servicenow.ServiceNowTicketNG;
import io.harness.servicenow.ServiceNowTicketNG.ServiceNowTicketNGBuilder;
import io.harness.servicenow.ServiceNowTicketTypeDTO;
import io.harness.servicenow.ServiceNowTicketTypeNG;
import io.harness.servicenow.ServiceNowUpdateMultipleTaskNode;
import io.harness.servicenow.ServiceNowUtils;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;
import software.wings.helpers.ext.servicenow.ServiceNowRestClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Joiner;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.github.resilience4j.retry.Retry;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.StreamResetException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
@Slf4j
@Singleton
public class ServiceNowTaskNgHelper {
  private final SecretDecryptionService secretDecryptionService;
  private final Retry retry = buildRetryAndRegisterListeners();

  @Inject @Named("serviceNowFetchTicketExecutor") public ExecutorService executorService;

  public static final String RESPONSE_MESSAGE_SERVICENOW = "Response received from serviceNow: {}";
  public static final String FAILURE_MESSAGE_SERVICENOW_STANDARD_TEMPLATE =
      "Failed to get ServiceNow Standard template";
  public static final String FAILURE_MESSAGE_SERVICENOW_CREATE_STANDARD_TEMPLATE =
      "Failed to create ServiceNow ticket from standard template";
  public static final String ERROR_OCCURRED_MESSAGE_SERVICENOW_CREATE_STANDARD_TEMPLATE =
      "Error occurred while creating serviceNow ticket from standard template: %s";

  @Inject
  public ServiceNowTaskNgHelper(SecretDecryptionService secretDecryptionService) {
    this.secretDecryptionService = secretDecryptionService;
  }

  public ServiceNowTaskNGResponse getServiceNowResponse(
      ServiceNowTaskNGParameters serviceNowTaskNGParameters, ILogStreamingTaskClient logStreamingTaskClient) {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    LogCallback executionLogCallback = null;
    if (!isNull(logStreamingTaskClient)) {
      executionLogCallback = new NGDelegateLogCallback(logStreamingTaskClient, "Execute", false, commandUnitsProgress);
    }
    decryptRequestDTOs(serviceNowTaskNGParameters);
    switch (serviceNowTaskNGParameters.getAction()) {
      case VALIDATE_CREDENTIALS:
        return validateCredentials(serviceNowTaskNGParameters);
      case GET_TICKET_CREATE_METADATA:
        return getIssueCreateMetaData(serviceNowTaskNGParameters);
      case GET_TICKET:
        return getTicket(serviceNowTaskNGParameters);
      case CREATE_TICKET:
        return createTicket(serviceNowTaskNGParameters);
      case CREATE_TICKET_USING_STANDARD_TEMPLATE:
        return createTicketUsingServiceNowStandardTemplate(serviceNowTaskNGParameters);
      case UPDATE_TICKET:
        return updateTicket(serviceNowTaskNGParameters);
      case GET_METADATA:
        return getMetadata(serviceNowTaskNGParameters);
      case GET_TEMPLATE:
        return getTemplateList(serviceNowTaskNGParameters);
      case GET_STANDARD_TEMPLATE:
        return getStandardTemplate(serviceNowTaskNGParameters);
      case IMPORT_SET:
        return createImportSet(serviceNowTaskNGParameters, executionLogCallback);
      case GET_IMPORT_SET_STAGING_TABLES:
        return getStagingTableList(serviceNowTaskNGParameters);
      case GET_TICKET_TYPES:
        return getTicketTypes(serviceNowTaskNGParameters);
      case GET_METADATA_V2:
        return getMetadataV2(serviceNowTaskNGParameters);
      case GET_STANDARD_TEMPLATES_READONLY_FIELDS:
        return getReadOnlyFieldsForStandardTemplate(serviceNowTaskNGParameters);
      default:
        throw new InvalidRequestException(
            String.format("Invalid servicenow task action: %s", serviceNowTaskNGParameters.getAction()));
    }
  }

  private void validateServiceNowTaskInputs(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    String ticketType = serviceNowTaskNGParameters.getTicketType();
    // allowing custom tables too , hence ticketType is not validated to be in ServiceNowTicketTypeNG
    if (StringUtils.isBlank(ticketType)) {
      throw new InvalidRequestException("Blank ticketType provided for ServiceNow");
    }
  }

  private ServiceNowTaskNGResponse createTicket(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    validateServiceNowTaskInputs(serviceNowTaskNGParameters);
    if (!serviceNowTaskNGParameters.isUseServiceNowTemplate()) {
      return createTicketWithoutTemplate(serviceNowTaskNGParameters);
    } else {
      return createTicketUsingServiceNowTemplate(serviceNowTaskNGParameters);
    }
  }

  private ServiceNowTaskNGResponse createTicketWithoutTemplate(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());

    Map<String, String> body = new HashMap<>();
    // todo: process servicenow fields before client uses these
    serviceNowTaskNGParameters.getFields().forEach((key, value) -> {
      if (EmptyPredicate.isNotEmpty(value)) {
        body.put(key, value);
      }
    });

    final Call<JsonNode> request =
        serviceNowRestClient.createTicket(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO),
            serviceNowTaskNGParameters.getTicketType().toLowerCase(), "all", null, body);
    Response<JsonNode> response = null;

    try {
      log.info("Body of the create issue request made to the ServiceNow server: {}", body);
      response = request.execute();
      log.info("Response received from serviceNow: {}", response);
      handleResponse(response, "Failed to create ServiceNow ticket");
      JsonNode responseObj = response.body().get("result");

      ServiceNowTicketNGBuilder serviceNowTicketNGBuilder = parseFromServiceNowTicketResponse(responseObj);
      ServiceNowTicketNG ticketNg = serviceNowTicketNGBuilder.build();
      log.info("ticketNumber created for ServiceNow: {}", ticketNg.getNumber());

      String ticketUrlFromTicketId = "";
      ticketUrlFromTicketId = getTicketUrlFromTicketIdOrNumber(
          serviceNowTaskNGParameters.getTicketType(), serviceNowConnectorDTO.getServiceNowUrl(), ticketNg);
      ticketNg.setUrl(ticketUrlFromTicketId);
      return ServiceNowTaskNGResponse.builder().ticket(ticketNg).build();
    } catch (ServiceNowException e) {
      log.error("Failed to create ServiceNow ticket: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    } catch (Exception ex) {
      log.error("Failed to create ServiceNow ticket: {}", ExceptionUtils.getMessage(ex), ex);
      throw new ServiceNowException(
          String.format("Error occurred while creating serviceNow ticket: %s", ExceptionUtils.getMessage(ex)),
          SERVICENOW_ERROR, USER, ex);
    }
  }

  private ServiceNowTaskNGResponse createTicketUsingServiceNowTemplate(
      ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    if (serviceNowTaskNGParameters.getTemplateName() == null) {
      throw new ServiceNowException("templateName can not be empty", SERVICENOW_ERROR, USER);
    }
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());

    final Call<JsonNode> request =
        serviceNowRestClient.createUsingTemplate(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO),
            serviceNowTaskNGParameters.getTicketType().toLowerCase(), serviceNowTaskNGParameters.getTemplateName());
    Response<JsonNode> response = null;

    try {
      log.info("createUsingTemplate called for ticketType: {}, templateName: {}",
          serviceNowTaskNGParameters.getTicketType(), serviceNowTaskNGParameters.getTemplateName());
      response = request.execute();
      log.info("Response received for createUsingTemplate: {}", response);
      handleResponse(response, "Failed to create ServiceNow ticket");
      JsonNode responseObj = response.body().get("result");
      String ticketNumber = responseObj.get("record_number").asText();
      String ticketSysId = responseObj.get("record_sys_id").asText();

      ServiceNowTicketNGBuilder serviceNowTicketNGBuilder = fetchServiceNowTicketUsingSysId(
          ticketSysId, serviceNowTaskNGParameters.getTicketType(), serviceNowTaskNGParameters);
      log.info(String.format("Created ticket with number: %s, sys_id: %s", ticketNumber, ticketSysId));
      // get url from sys_id instead of ticket number
      String ticketUrlFromSysId = ServiceNowUtils.prepareTicketUrlFromTicketId(
          serviceNowConnectorDTO.getServiceNowUrl(), ticketSysId, serviceNowTaskNGParameters.getTicketType());
      return ServiceNowTaskNGResponse.builder()
          .ticket(serviceNowTicketNGBuilder.url(ticketUrlFromSysId).build())
          .build();
    } catch (ServiceNowException e) {
      log.error("Failed to create ServiceNow ticket: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    } catch (Exception ex) {
      log.error("Failed to create ServiceNow ticket: {}", ExceptionUtils.getMessage(ex), ex);
      throw new ServiceNowException(
          String.format("Error occurred while creating serviceNow ticket: %s", ExceptionUtils.getMessage(ex)),
          SERVICENOW_ERROR, USER, ex);
    }
  }
  private ServiceNowTaskNGParameters getTaskNGParametersForFetchingStandardTemplate(
      ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    return ServiceNowTaskNGParameters.builder()
        .serviceNowConnectorDTO(serviceNowTaskNGParameters.getServiceNowConnectorDTO())
        .templateName(serviceNowTaskNGParameters.getTemplateName())
        .templateListOffset(0)
        .templateListLimit(1)
        .build();
  }

  private ServiceNowTaskNGResponse createTicketUsingServiceNowStandardTemplate(
      ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    if (StringUtils.isBlank(serviceNowTaskNGParameters.getTemplateName())) {
      throw new ServiceNowException("templateName cannot be empty", SERVICENOW_ERROR, USER);
    }

    String sys_id = "";
    try {
      ServiceNowTaskNGResponse standardTemplate =
          getStandardTemplate(getTaskNGParametersForFetchingStandardTemplate(serviceNowTaskNGParameters));
      sys_id = standardTemplate.getServiceNowTemplateList().get(0).getSys_id();
    } catch (Exception ex) {
      log.error(String.format(FAILURE_MESSAGE_SERVICENOW_STANDARD_TEMPLATE, ExceptionUtils.getMessage(ex)), ex);
      throw new ServiceNowException(
          String.format(FAILURE_MESSAGE_SERVICENOW_STANDARD_TEMPLATE, ExceptionUtils.getMessage(ex)), SERVICENOW_ERROR,
          USER, ex);
    }
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());

    final Call<JsonNode> request = serviceNowRestClient.createTicketUsingStandardTemplate(
        ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO), sys_id, serviceNowTaskNGParameters.getFields());
    Response<JsonNode> response = null;

    try {
      log.info("createUsingStandardChangeTemplate called for ticketType: {}, templateName: {}",
          serviceNowTaskNGParameters.getTicketType(), serviceNowTaskNGParameters.getTemplateName());
      response = request.execute();
      log.info("Response received for createUsingStandardChangeTemplate: {}", response);
      handleResponse(response, FAILURE_MESSAGE_SERVICENOW_CREATE_STANDARD_TEMPLATE);
      JsonNode responseObj = response.body().get(RESULT);
      ServiceNowTicketNGBuilder serviceNowTicketNGBuilder = parseFromServiceNowTicketResponse(responseObj, true);
      ServiceNowTicketNG ticketNg = serviceNowTicketNGBuilder.build();
      log.info("ticketNumber created for ServiceNow ticket: {} , templateName: {}", ticketNg.getNumber(),
          serviceNowTaskNGParameters.getTemplateName());
      String ticketUrlFromTicketId = ServiceNowUtils.getTicketUrlFromTicketIdOrNumber(
          serviceNowTaskNGParameters.getTicketType(), serviceNowConnectorDTO.getServiceNowUrl(), ticketNg);

      ticketNg.setUrl(ticketUrlFromTicketId);
      return ServiceNowTaskNGResponse.builder().ticket(ticketNg).build();
    } catch (ServiceNowException e) {
      log.error(FAILURE_MESSAGE_SERVICENOW_CREATE_STANDARD_TEMPLATE + ": {}", ExceptionUtils.getMessage(e), e);
      throw e;
    } catch (Exception ex) {
      log.error(FAILURE_MESSAGE_SERVICENOW_CREATE_STANDARD_TEMPLATE + ": {}", ExceptionUtils.getMessage(ex), ex);
      throw new ServiceNowException(
          String.format(ERROR_OCCURRED_MESSAGE_SERVICENOW_CREATE_STANDARD_TEMPLATE, ExceptionUtils.getMessage(ex)),
          SERVICENOW_ERROR, USER, ex);
    }
  }

  private List<String> fetchChangeTasksFromCR(
      String changeRequestNumber, String changeTaskType, ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());

    List<String> changeTaskIds = new ArrayList<>();

    String query = changeTaskType == null
        ? String.format(QUERY_FOR_GETTING_CHANGE_TASK_ALL, changeRequestNumber)
        : String.format(QUERY_FOR_GETTING_CHANGE_TASK, changeRequestNumber, changeTaskType);

    Call<JsonNode> request = serviceNowRestClient.fetchChangeTasksFromCR(
        ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO), CHANGE_TASK, RETURN_FIELDS, query);

    try {
      Response<JsonNode> response = Retry.decorateCallable(retry, () -> request.clone().execute()).call();
      log.info("Response received from serviceNow: {}", response);
      handleResponse(response, "Failed to fetch tasks for service now change request ticket");
      JsonNode responseObj = response.body().get("result");
      if (responseObj.isArray()) {
        for (JsonNode changeTaskObj : responseObj) {
          if (serviceNowTaskNGParameters.isUseServiceNowTemplate()) {
            changeTaskIds.add(changeTaskObj.get(ISSUE_NUMBER).textValue());
          } else {
            changeTaskIds.add(changeTaskObj.get(SYS_ID).textValue());
          }
        }
        return changeTaskIds;
      } else {
        throw new ServiceNowException(
            "Response for fetching changeTasks is not an array. Response: " + response, SERVICENOW_ERROR, USER);
      }
    } catch (ServiceNowException e) {
      log.error("Failed to fetch service now tasks {}", ExceptionUtils.getMessage(e), e);
      throw e;
    } catch (Exception ex) {
      log.error("Failed to fetch service now tasks {}", ExceptionUtils.getMessage(ex), ex);
      throw new ServiceNowException(errorWhileUpdatingTicket(ex), SERVICENOW_ERROR, USER, ex);
    }
  }

  public ServiceNowTaskNGResponse updateMultipleTickets(
      ServiceNowTaskNGParameters serviceNowTaskNGParameters, String ticketId) {
    if (CHANGE_TASK.equalsIgnoreCase(serviceNowTaskNGParameters.getUpdateMultiple().getType())) {
      ChangeTaskUpdateMultiple changeRequestUpdateMultiple =
          (ChangeTaskUpdateMultiple) serviceNowTaskNGParameters.getUpdateMultiple().getSpec();

      List<String> changeTaskIdsToUpdate =
          fetchChangeTasksFromCR(ticketId, changeRequestUpdateMultiple.getChangeTaskType(), serviceNowTaskNGParameters);

      Map<String, String> fieldsBody = serviceNowTaskNGParameters.getFields()
                                           .entrySet()
                                           .stream()
                                           .filter(entry -> !StringUtils.isEmpty(entry.getValue()))
                                           .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

      List<ServiceNowTicketNG> updatedTickets = new ArrayList<>();

      List<Future<ServiceNowTaskNGResponse>> futures = new ArrayList<>();

      log.info("Body of the update issue request made to the ServiceNow server: {}", fieldsBody);
      for (String changeTaskId : changeTaskIdsToUpdate) {
        Callable<ServiceNowTaskNGResponse> task = () -> updateSingleTicket(serviceNowTaskNGParameters, changeTaskId);

        Future<ServiceNowTaskNGResponse> future = executorService.submit(task);
        futures.add(future);
      }

      for (Future<ServiceNowTaskNGResponse> future : futures) {
        try {
          ServiceNowTaskNGResponse partialResult = future.get(TIME_OUT, TimeUnit.SECONDS);
          updatedTickets.add(partialResult.getTicket());
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
          log.error("Failed to update ticket in updateMultipleTask", e);
          throw new ServiceNowException(
              "Failed to update ticket in updateMultipleTask, please contact support", SERVICENOW_ERROR, USER);
        }
      }

      return ServiceNowTaskNGResponse.builder().tickets(updatedTickets).build();
    }

    log.error("Failed to update multiple in ServiceNow type: {} is not supported",
        serviceNowTaskNGParameters.getUpdateMultiple().getType());
    throw new ServiceNowException(
        String.format("Error occurred while updating multiple in serviceNow for ticket type {}",
            serviceNowTaskNGParameters.getUpdateMultiple().getType()),
        SERVICENOW_ERROR, USER);
  }

  private ServiceNowTaskNGResponse updateTicket(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    String ticketId = "";

    if (StringUtils.isNotBlank(serviceNowTaskNGParameters.getTicketNumber())) {
      ticketId = serviceNowTaskNGParameters.getTicketNumber();
    } else {
      ticketId = getTicketNumberFromUpdateMultiple(serviceNowTaskNGParameters);
    }

    if (serviceNowTaskNGParameters.getUpdateMultiple() != null) {
      return updateMultipleTickets(serviceNowTaskNGParameters, ticketId);
    }
    return updateSingleTicket(serviceNowTaskNGParameters, ticketId);
  }

  private ServiceNowTaskNGResponse updateSingleTicket(
      ServiceNowTaskNGParameters serviceNowTaskNGParameters, String ticketId) {
    if (!serviceNowTaskNGParameters.isUseServiceNowTemplate()) {
      return updateTicketWithoutTemplate(serviceNowTaskNGParameters, ticketId);
    } else {
      return updateTicketWithTemplate(serviceNowTaskNGParameters, ticketId);
    }
  }

  private ServiceNowTaskNGResponse updateTicketWithTemplate(
      ServiceNowTaskNGParameters serviceNowTaskNGParameters, String ticketId) {
    if (serviceNowTaskNGParameters.getTemplateName() == null) {
      throw new ServiceNowException("templateName can not be empty", SERVICENOW_ERROR, USER);
    }
    String ticketType = serviceNowTaskNGParameters.getTicketType().toLowerCase();
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());

    final Call<JsonNode> request =
        serviceNowRestClient.updateUsingTemplate(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO),
            ticketType, serviceNowTaskNGParameters.getTemplateName(), ticketId);
    Response<JsonNode> response = null;

    try {
      log.info("updateUsingTemplate called for ticketType: {}, templateName: {}", ticketType,
          serviceNowTaskNGParameters.getTemplateName());
      response = Retry.decorateCallable(retry, () -> request.clone().execute()).call();
      log.info("Response received for updateUsingTemplate: {}", response);
      handleResponse(response, "Failed to update ServiceNow ticket");
      JsonNode responseObj = response.body().get("result");
      String ticketNumber = responseObj.get("record_number").asText();
      String ticketSysId = parseTicketSysIdFromResponse(responseObj);

      ServiceNowTicketNGBuilder serviceNowTicketNGBuilder =
          fetchServiceNowTicketUsingSysId(ticketSysId, ticketType, serviceNowTaskNGParameters);

      log.info(String.format("Updated ticket with number: %s, sys_id: %s", ticketNumber, ticketSysId));
      String ticketUrlFromSysId = ServiceNowUtils.prepareTicketUrlFromTicketId(
          serviceNowConnectorDTO.getServiceNowUrl(), ticketSysId, ticketType);
      return ServiceNowTaskNGResponse.builder()
          .ticket(serviceNowTicketNGBuilder.url(ticketUrlFromSysId).build())
          .build();
    } catch (ServiceNowException e) {
      log.error(failedToUpdateTicket(), ExceptionUtils.getMessage(e), e);
      throw e;
    } catch (Exception ex) {
      log.error(failedToUpdateTicket(), ExceptionUtils.getMessage(ex), ex);
      throw new ServiceNowException(errorWhileUpdatingTicket(ex), SERVICENOW_ERROR, USER, ex);
    }
  }

  private String parseTicketSysIdFromResponse(JsonNode responseObj) {
    JsonNode sysIdNode = responseObj.get("record_sys_id");
    if (sysIdNode != null) {
      return sysIdNode.asText();
    }
    String[] entries = responseObj.get("record_link").asText().split("[&=]");
    return entries[1];
  }

  private String getIssueIdFromIssueNumber(ServiceNowTaskNGParameters parameters) {
    String query = "number=" + parameters.getTicketNumber();
    ServiceNowConnectorDTO serviceNowConnectorDTO = parameters.getServiceNowConnectorDTO();

    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());

    final Call<JsonNode> request =
        serviceNowRestClient.getIssue(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO),
            parameters.getTicketType().toString().toLowerCase(), query, "all");
    Response<JsonNode> response = null;
    try {
      response = Retry.decorateCallable(retry, () -> request.clone().execute()).call();
      log.info("Response received from serviceNow: {}", response);
      handleResponse(response, "Failed to fetch ticketId : " + parameters.getTicketNumber() + " from serviceNow");
      JsonNode responseObj = response.body().get("result");
      if (responseObj.isArray()) {
        if (responseObj.size() > 1) {
          String errorMsg =
              "Multiple issues found for " + parameters.getTicketNumber() + "Please enter unique issueNumber";
          throw new ServiceNowException(errorMsg, SERVICENOW_ERROR, USER);
        }
        JsonNode issueObj = responseObj.get(0);
        if (issueObj != null) {
          return issueObj.get("sys_id").get("display_value").asText();
        } else {
          String errorMsg = "Error in fetching issue " + parameters.getTicketNumber() + " .Issue does not exist";
          throw new ServiceNowException(errorMsg, SERVICENOW_ERROR, USER);
        }
      } else {
        throw new ServiceNowException(
            "Failed to fetch issueNumber " + parameters.getTicketNumber() + "response: " + response, SERVICENOW_ERROR,
            USER);
      }
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      String errorMsg = "Error in fetching issueNumber " + parameters.getTicketNumber();
      throw new ServiceNowException(errorMsg + ExceptionUtils.getMessage(e), SERVICENOW_ERROR, USER, e);
    }
  }

  private ServiceNowTicketNGBuilder fetchServiceNowTicketUsingSysId(
      String ticketSysId, String ticketType, ServiceNowTaskNGParameters parameters) {
    String query = "sys_id=" + ticketSysId;
    ServiceNowConnectorDTO serviceNowConnectorDTO = parameters.getServiceNowConnectorDTO();

    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());

    final Call<JsonNode> request = serviceNowRestClient.getIssue(
        ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO), ticketType.toLowerCase(), query, "all");
    Response<JsonNode> response = null;
    try {
      response = Retry.decorateCallable(retry, () -> request.clone().execute()).call();
      log.info("Response received from serviceNow: {}", response);
      handleResponse(response, "Failed to fetch ticket with sys_id : " + ticketSysId + " from serviceNow");
      JsonNode responseObj = response.body().get("result");
      if (responseObj.isArray()) {
        if (responseObj.size() > 1) {
          String errorMsg = "Multiple issues found for sys_id " + ticketSysId;
          throw new ServiceNowException(errorMsg, SERVICENOW_ERROR, USER);
        }
        JsonNode issueObj = responseObj.get(0);
        if (issueObj != null) {
          return parseFromServiceNowTicketResponse(issueObj);
        } else {
          String errorMsg = "Error in fetching issue " + ticketSysId + " .Issue does not exist";
          throw new ServiceNowException(errorMsg, SERVICENOW_ERROR, USER);
        }
      } else {
        throw new ServiceNowException(
            "Failed to fetch issueNumber " + ticketSysId + "response: " + response, SERVICENOW_ERROR, USER);
      }
    } catch (WingsException e) {
      log.error("Error in fetching issue with sys_id: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    } catch (Exception e) {
      String errorMsg = "Error in fetching issue with sys_id " + ticketSysId;
      throw new ServiceNowException(errorMsg + ExceptionUtils.getMessage(e), SERVICENOW_ERROR, USER, e);
    }
  }
  private ServiceNowTicketNGBuilder parseFromServiceNowTicketResponse(JsonNode node) {
    return parseFromServiceNowTicketResponse(node, false);
  }
  private ServiceNowTicketNGBuilder parseFromServiceNowTicketResponse(JsonNode node, Boolean includeIgnoredFields) {
    Map<String, ServiceNowFieldValueNG> fields = new HashMap<>();

    for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
      Map.Entry<String, JsonNode> f = it.next();

      if (includeIgnoredFields && f.getKey().equals(META)) {
        List<String> ignoredFieldsAsList = new ArrayList<>();

        if (f.getValue() == null || f.getValue().get(IGNOREFIELDS) == null
            || !f.getValue().get(IGNOREFIELDS).isArray()) {
          continue;
        }
        for (JsonNode element : f.getValue().get(IGNOREFIELDS)) {
          // Append the element as a string followed by a comma
          ignoredFieldsAsList.add(element.asText());
        }

        if (ignoredFieldsAsList.isEmpty()) {
          continue;
        }
        String result = Joiner.on(", ").join(ignoredFieldsAsList);
        fields.put(IGNOREFIELDS, ServiceNowFieldValueNG.builder().displayValue(result).build());
        continue;
      }
      String displayValue = JsonNodeUtils.getString(f.getValue(), "display_value");
      if (EmptyPredicate.isNotEmpty(displayValue)) {
        fields.put(f.getKey().trim(), ServiceNowFieldValueNG.builder().displayValue(displayValue.trim()).build());
      }
    }

    if (fields.get("number") != null) {
      return ServiceNowTicketNG.builder().number(fields.get("number").getDisplayValue()).fields(fields);
    }

    return ServiceNowTicketNG.builder().fields(fields);
  }

  private ServiceNowTaskNGResponse updateTicketWithoutTemplate(
      ServiceNowTaskNGParameters serviceNowTaskNGParameters, String ticketId) {
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());

    String ticketType = serviceNowTaskNGParameters.getTicketType().toLowerCase();

    if (serviceNowTaskNGParameters.getTicketNumber() != null) {
      ticketId = getIssueIdFromIssueNumber(serviceNowTaskNGParameters);
    }

    Map<String, String> body = new HashMap<>();
    // todo: process servicenow fields before client uses these
    serviceNowTaskNGParameters.getFields().forEach((key, value) -> {
      if (EmptyPredicate.isNotEmpty(value)) {
        body.put(key, value);
      }
    });

    final Call<JsonNode> request =
        serviceNowRestClient.updateTicket(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO),
            ticketType.toLowerCase(), ticketId, "all", null, body);
    Response<JsonNode> response = null;

    try {
      log.info("Body of the update issue request made to the ServiceNow server: {}", body);
      response = Retry.decorateCallable(retry, () -> request.clone().execute()).call();
      log.info("Response received from serviceNow: {}", response);
      handleResponse(response, "Failed to update ServiceNow ticket");
      JsonNode responseObj = response.body().get("result");
      ServiceNowTicketNGBuilder serviceNowTicketNGBuilder = parseFromServiceNowTicketResponse(responseObj);
      ServiceNowTicketNG ticketNg = serviceNowTicketNGBuilder.build();
      log.info("ticketNumber updated for ServiceNow: {}", ticketNg.getNumber());
      String ticketUrlFromTicketId = ServiceNowUtils.prepareTicketUrlFromTicketNumber(
          serviceNowConnectorDTO.getServiceNowUrl(), ticketNg.getNumber(), ticketType);
      ticketNg.setUrl(ticketUrlFromTicketId);
      return ServiceNowTaskNGResponse.builder().ticket(ticketNg).build();
    } catch (ServiceNowException e) {
      log.error(failedToUpdateTicket(), ExceptionUtils.getMessage(e), e);
      throw e;
    } catch (Exception ex) {
      log.error(failedToUpdateTicket(), ExceptionUtils.getMessage(ex), ex);
      throw new ServiceNowException(errorWhileUpdatingTicket(ex), SERVICENOW_ERROR, USER, ex);
    }
  }
  private ServiceNowTaskNGResponse getStandardTemplate(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());

    StringBuilder sysparm_query_builder = new StringBuilder("active=true");
    if (!StringUtils.isBlank(serviceNowTaskNGParameters.getTemplateName())) {
      sysparm_query_builder.append(String.format("^sys_name=%s", serviceNowTaskNGParameters.getTemplateName()));
    }
    if (!StringUtils.isBlank(serviceNowTaskNGParameters.getSearchTerm())) {
      sysparm_query_builder.append(String.format("^sys_nameCONTAINS%s", serviceNowTaskNGParameters.getSearchTerm()));
    }
    sysparm_query_builder.append("^ORDERBYsys_created_on");
    String sysparm_query = sysparm_query_builder.toString();
    String sparm_fields = "template,sys_name,sys_id";
    final Call<JsonNode> request = serviceNowRestClient.getStandardTemplate(
        ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO), sysparm_query, sparm_fields,
        serviceNowTaskNGParameters.getTemplateListLimit(), serviceNowTaskNGParameters.getTemplateListOffset());
    Response<JsonNode> response = null;
    try {
      response = Retry.decorateCallable(retry, () -> request.clone().execute()).call();
      log.info(RESPONSE_MESSAGE_SERVICENOW, response);
      handleResponse(response, FAILURE_MESSAGE_SERVICENOW_STANDARD_TEMPLATE);
      JsonNode responseObj = response.body().get(RESULT);

      // Standard template fields are fetched only when template name is provided, Otherwise list of {sys_id,sys_name}
      // is fetched.
      if (!StringUtils.isBlank(serviceNowTaskNGParameters.getTemplateName())) {
        return getStandardTemplateFields(
            serviceNowTaskNGParameters, serviceNowRestClient, serviceNowConnectorDTO, responseObj);
      }

      if (responseObj != null) {
        JsonNode templateList = responseObj;
        List<ServiceNowTemplate> templateResponse = new ArrayList<>(templateList.size());
        for (JsonNode template : templateList) {
          String templateName = template.get(SYS_NAME).asText();
          templateResponse.add(
              ServiceNowTemplate.builder().sys_id(template.get(SYS_ID).asText()).name(templateName).build());
        }
        return ServiceNowTaskNGResponse.builder().serviceNowTemplateList(templateResponse).build();
      } else {
        throw new ServiceNowException("Failed to fetch standard templates for ticket type "
                + serviceNowTaskNGParameters.getTicketType() + " response: " + response,
            SERVICENOW_ERROR, USER);
      }
    } catch (ServiceNowException e) {
      log.error(FAILURE_MESSAGE_SERVICENOW_STANDARD_TEMPLATE + ": {}", ExceptionUtils.getMessage(e), e);
      throw e;
    } catch (Exception ex) {
      log.error(FAILURE_MESSAGE_SERVICENOW_STANDARD_TEMPLATE + ": {}", ExceptionUtils.getMessage(ex), ex);
      throw new ServiceNowException(String.format("Error occurred while fetching serviceNow Standard templates: %s",
                                        ExceptionUtils.getMessage(ex)),
          SERVICENOW_ERROR, USER, ex);
    }
  }

  private String getTicketNumberFromUpdateMultiple(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    ServiceNowUpdateMultipleTaskNode updateMultiple = serviceNowTaskNGParameters.getUpdateMultiple();

    if (updateMultiple != null) {
      ChangeTaskUpdateMultiple changeTaskUpdateMultiple = (ChangeTaskUpdateMultiple) updateMultiple.getSpec();
      return changeTaskUpdateMultiple != null ? changeTaskUpdateMultiple.getChangeRequestNumber() : "";
    }
    return "";
  }
  private ServiceNowTaskNGResponse getStandardTemplateFields(ServiceNowTaskNGParameters serviceNowTaskNGParameters,
      ServiceNowRestClient serviceNowRestClient, ServiceNowConnectorDTO serviceNowConnectorDTO, JsonNode responseObj) {
    if (responseObj.size() == 0) {
      throw new ServiceNowException(String.format("No ServiceNow Standard Template found with template name %s",
                                        serviceNowTaskNGParameters.getTemplateName()),
          SERVICENOW_ERROR, USER);
    }

    if (responseObj.size() > 1) {
      throw new ServiceNowException(
          String.format("More than one ServiceNow Standard Template found with template name %s",
              serviceNowTaskNGParameters.getTemplateName()),
          SERVICENOW_ERROR, USER);
    }

    JsonNode template = responseObj.get(0);

    if (!serviceNowTaskNGParameters.getTemplateName().equalsIgnoreCase(template.get(SYS_NAME).asText())) {
      throw new ServiceNowException(
          String.format("Standard Template fetched %s is not matched with template name %s provided",
              template.get(SYS_NAME).asText(), serviceNowTaskNGParameters.getTemplateName()),
          SERVICENOW_ERROR, USER);
    }

    String id = template.get(TEMPLATE).get(VALUE).asText();
    final Call<JsonNode> requestStandardTemplate =
        serviceNowRestClient.getStandardTemplate(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO), id);
    Response<JsonNode> standardTemplateResponse = null;
    try {
      standardTemplateResponse = Retry.decorateCallable(retry, () -> requestStandardTemplate.clone().execute()).call();
      log.info(RESPONSE_MESSAGE_SERVICENOW, standardTemplateResponse);
      handleResponse(standardTemplateResponse, FAILURE_MESSAGE_SERVICENOW_STANDARD_TEMPLATE);
      JsonNode responseObjStandardTemplate = standardTemplateResponse.body().get(RESULT);
      return ServiceNowTaskNGResponse.builder()
          .serviceNowFieldJsonNGListAsString(responseObjStandardTemplate.get(TEMPLATE).asText())
          .serviceNowTemplateList(Collections.singletonList(ServiceNowTemplate.builder()
                                                                .sys_id(template.get(SYS_ID).asText())
                                                                .name(template.get(SYS_NAME).asText())
                                                                .build()))
          .build();
    } catch (ServiceNowException e) {
      log.error(FAILURE_MESSAGE_SERVICENOW_STANDARD_TEMPLATE + "fields: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    } catch (Exception ex) {
      log.error(FAILURE_MESSAGE_SERVICENOW_STANDARD_TEMPLATE + "fields: {}", ExceptionUtils.getMessage(ex), ex);
      throw new ServiceNowException(
          String.format(
              "Error occurred while fetching serviceNow standard template fields: %s", ExceptionUtils.getMessage(ex)),
          SERVICENOW_ERROR, USER, ex);
    }
  }
  private ServiceNowTaskNGResponse getTemplateList(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());

    final Call<JsonNode> request =
        serviceNowRestClient.getTemplateList(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO),
            serviceNowTaskNGParameters.getTicketType().toLowerCase(), serviceNowTaskNGParameters.getTemplateListLimit(),
            serviceNowTaskNGParameters.getTemplateListOffset(), serviceNowTaskNGParameters.getTemplateName(),
            serviceNowTaskNGParameters.getSearchTerm());
    Response<JsonNode> response = null;
    try {
      response = Retry.decorateCallable(retry, () -> request.clone().execute()).call();
      log.info("Response received from serviceNow: {}", response);
      handleResponse(response, "Failed to get ServiceNow templates");
      JsonNode responseObj = response.body().get("result");
      if (responseObj != null && responseObj.get("queryResponse") != null) {
        JsonNode templateList = responseObj.get("queryResponse");
        List<ServiceNowTemplate> templateResponse = new ArrayList<>(templateList.size());
        for (JsonNode template : templateList) {
          String[] fieldList = template.get("readableValue").asText().split("\\.and\\.");
          String templateName = template.get("name").asText();
          Map<String, ServiceNowFieldValueNG> parsedFields = new HashMap<>();
          for (String field : fieldList) {
            String[] keyValue = field.split("=");
            // what about other types of fields
            if (keyValue.length == 2) {
              parsedFields.put(
                  keyValue[0].trim(), ServiceNowFieldValueNG.builder().displayValue(keyValue[1].trim()).build());
            }
          }
          templateResponse.add(ServiceNowTemplate.builder().name(templateName).fields(parsedFields).build());
        }
        return ServiceNowTaskNGResponse.builder().serviceNowTemplateList(templateResponse).build();
      } else {
        throw new ServiceNowException("Failed to fetch templates for ticket type "
                + serviceNowTaskNGParameters.getTicketType() + " response: " + response,
            SERVICENOW_ERROR, USER);
      }
    } catch (ServiceNowException e) {
      log.error("Failed to get ServiceNow templates: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    } catch (Exception ex) {
      log.error("Failed to get ServiceNow templates: {}", ExceptionUtils.getMessage(ex), ex);
      throw new ServiceNowException(
          String.format("Error occurred while fetching serviceNow templates: %s", ExceptionUtils.getMessage(ex)),
          SERVICENOW_ERROR, USER, ex);
    }
  }
  private ServiceNowTaskNGResponse getTicket(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());

    final Call<JsonNode> request = serviceNowRestClient.getIssueV2(
        ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO, true),
        serviceNowTaskNGParameters.getTicketType().toLowerCase(),
        "number=" + serviceNowTaskNGParameters.getTicketNumber(), "all", serviceNowTaskNGParameters.getQueryFields());
    Response<JsonNode> response = null;

    try {
      response = Retry.decorateCallable(retry, () -> request.clone().execute()).call();
      if (isUnauthorizedError(response)) {
        log.warn("Failed to get serviceNow ticket using cached auth token; trying with fresh token");
        Call<JsonNode> requestWithoutCache =
            serviceNowRestClient.getIssueV2(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO, false),
                serviceNowTaskNGParameters.getTicketType().toLowerCase(),
                "number=" + serviceNowTaskNGParameters.getTicketNumber(), "all",
                serviceNowTaskNGParameters.getQueryFields());
        response = Retry.decorateCallable(retry, () -> requestWithoutCache.clone().execute()).call();
      }
      handleResponse(response, "Failed to get serviceNow ticket");
      JsonNode responseObj = response.body().get("result");
      if (responseObj.isArray()) {
        Map<String, ServiceNowFieldValueNG> fieldValues = new HashMap<>();
        for (JsonNode fieldsObj : responseObj) {
          fieldsObj.fields().forEachRemaining(fieldObj
              -> fieldValues.put(fieldObj.getKey(),
                  ServiceNowFieldValueNG.builder()
                      .value(fieldObj.getValue().get("value").textValue())
                      .displayValue(fieldObj.getValue().get("display_value").textValue())
                      .build()));
        }
        return ServiceNowTaskNGResponse.builder()
            .ticket(ServiceNowTicketNG.builder()
                        .number(serviceNowTaskNGParameters.getTicketNumber())
                        .fields(fieldValues)
                        .url(ServiceNowUtils.prepareTicketUrlFromTicketNumber(serviceNowConnectorDTO.getServiceNowUrl(),
                            serviceNowTaskNGParameters.getTicketNumber(), serviceNowTaskNGParameters.getTicketType()))
                        .build())
            .build();
      } else {
        throw new ServiceNowException("Failed to fetch ticket for ticket type "
                + serviceNowTaskNGParameters.getTicketType() + " response: " + response,
            SERVICENOW_ERROR, USER);
      }
    } catch (Exception e) {
      log.error("Failed to get serviceNow ticket ");
      throw new ServiceNowException(
          String.format("Error occurred while fetching serviceNow ticket: %s", ExceptionUtils.getMessage(e)),
          SERVICENOW_ERROR, USER, e);
    }
  }

  private ServiceNowTaskNGResponse getIssueCreateMetaData(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());

    final Call<JsonNode> request =
        serviceNowRestClient.getAdditionalFields(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO),
            serviceNowTaskNGParameters.getTicketType().toLowerCase());
    Response<JsonNode> response = null;
    try {
      response = Retry.decorateCallable(retry, () -> request.clone().execute()).call();
      handleResponse(response, "Failed to get serviceNow fields");
      JsonNode responseObj = response.body().get("result");
      if (responseObj.isArray()) {
        List<ServiceNowFieldNG> fields = new ArrayList<>();
        for (JsonNode fieldObj : responseObj) {
          fields.add(ServiceNowFieldNG.builder()
                         .name(fieldObj.get("label").textValue())
                         .key(fieldObj.get("name").textValue())
                         .internalType(fieldObj.get("internalType").textValue())
                         .build());
        }
        return ServiceNowTaskNGResponse.builder().serviceNowFieldNGList(fields).build();
      } else {
        throw new ServiceNowException("Failed to fetch fields for ticket type "
                + serviceNowTaskNGParameters.getTicketType() + " response: " + response,
            SERVICENOW_ERROR, USER);
      }
    } catch (Exception e) {
      log.error("Failed to get serviceNow fields ");
      throw new ServiceNowException(
          String.format("Error occurred while fetching serviceNow fields: %s", ExceptionUtils.getMessage(e)),
          SERVICENOW_ERROR, USER, e);
    }
  }

  private ServiceNowTaskNGResponse getMetadata(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());

    final Call<JsonNode> request =
        serviceNowRestClient.getMetadata(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO),
            serviceNowTaskNGParameters.getTicketType().toLowerCase());
    Response<JsonNode> response = null;
    try {
      response = Retry.decorateCallable(retry, () -> request.clone().execute()).call();
      log.info("Response received from serviceNow for GET_METADATA: {}", response);
      handleResponse(response, "Failed to get serviceNow fields");
      JsonNode responseObj = response.body().get("result");
      if (responseObj != null && responseObj.get("columns") != null) {
        JsonNode columns = responseObj.get("columns");
        List<ServiceNowFieldNG> fields = new ArrayList<>();
        for (JsonNode fieldObj : columns) {
          List<ServiceNowFieldAllowedValueNG> allowedValues = buildAllowedValues(fieldObj.get("choices"));

          ServiceNowFieldNGBuilder fieldBuilder = ServiceNowFieldNG.builder()
                                                      .name(fieldObj.get("label").textValue())
                                                      .key(fieldObj.get("name").textValue())
                                                      .allowedValues(allowedValues);

          if (!allowedValues.isEmpty()) {
            fieldBuilder.schema(ServiceNowFieldSchemaNG.builder()
                                    .type(ServiceNowFieldTypeNG.OPTION)
                                    .typeStr("array")
                                    .array(true)
                                    .build());
          }

          fields.add(fieldBuilder.build());
        }
        return ServiceNowTaskNGResponse.builder().serviceNowFieldNGList(fields).build();
      } else {
        throw new ServiceNowException("Failed to fetch fields for ticket type "
                + serviceNowTaskNGParameters.getTicketType() + " response: " + response,
            SERVICENOW_ERROR, USER);
      }
    } catch (ServiceNowException e) {
      log.error("Failed to get ServiceNow fields: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    } catch (Exception ex) {
      log.error("Failed to get ServiceNow fields: {}", ExceptionUtils.getMessage(ex), ex);
      throw new ServiceNowException(
          String.format("Error occurred while getting serviceNow fields: %s", ExceptionUtils.getMessage(ex)),
          SERVICENOW_ERROR, USER, ex);
    }
  }

  private ServiceNowTaskNGResponse getMetadataV2(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());

    final Call<JsonNode> request =
        serviceNowRestClient.getMetadata(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO),
            serviceNowTaskNGParameters.getTicketType().toLowerCase());
    Response<JsonNode> response = null;
    try {
      response = Retry.decorateCallable(retry, () -> request.clone().execute()).call();
      log.info("Response received from serviceNow for GET_METADATA_V2: {}", response);
      handleResponse(response, "Failed to get serviceNow fields");
      JsonNode responseObj = response.body().get("result");
      if (responseObj != null && responseObj.get("columns") != null) {
        JsonNode columns = responseObj.get("columns");
        if (!isNull(columns)) {
          return ServiceNowTaskNGResponse.builder().serviceNowFieldJsonNGListAsString(columns.toString()).build();
        } else {
          throw new ServiceNowException("Failed to fetch fields for ticket type "
                  + serviceNowTaskNGParameters.getTicketType() + " response: " + response,
              SERVICENOW_ERROR, USER);
        }
      } else {
        throw new ServiceNowException("Failed to fetch fields for ticket type "
                + serviceNowTaskNGParameters.getTicketType() + " response: " + response,
            SERVICENOW_ERROR, USER);
      }
    } catch (ServiceNowException e) {
      log.error("Failed to get ServiceNow fields: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    } catch (Exception ex) {
      log.error("Failed to get ServiceNow fields: {}", ExceptionUtils.getMessage(ex), ex);
      throw new ServiceNowException(
          String.format("Error occurred while getting serviceNow fields: %s", ExceptionUtils.getMessage(ex)),
          SERVICENOW_ERROR, USER, ex);
    }
  }
  private ServiceNowTaskNGResponse getReadOnlyFieldsForStandardTemplate(
      ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());

    final Call<JsonNode> request = serviceNowRestClient.getReadOnlyFieldsForStandardTemplate(
        ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO));
    Response<JsonNode> response = null;
    try {
      response = Retry.decorateCallable(retry, () -> request.clone().execute()).call();
      log.info("Response received from serviceNow for GET_STANDARD_TEMPLATES_READONLY_FIELDS: {}", response);
      handleResponse(response, "Failed to get servicenow readonly metadata");
      JsonNode responseObj = response.body().get("result");
      if (responseObj != null) {
        if (responseObj.get(0) == null || responseObj.get(0).get("readonly_fields") == null) {
          log.info(
              "Response received from serviceNow for GET_STANDARD_TEMPLATES_READONLY_FIELDS is not having readonly_fields");
          return ServiceNowTaskNGResponse.builder().build();
        }
        String readonly_fields = responseObj.get(0).get("readonly_fields").asText();
        return ServiceNowTaskNGResponse.builder().serviceNowStandardTemplateReadOnlyFields(readonly_fields).build();
      } else {
        throw new ServiceNowException("Failed to fetch read only fields for standard templates "
                + " response: " + response,
            SERVICENOW_ERROR, USER);
      }
    } catch (ServiceNowException e) {
      log.error("Failed to get ServiceNow read only fields for standard template: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    } catch (Exception ex) {
      log.error(
          "Failed to get ServiceNow read only fields for standard template: {}", ExceptionUtils.getMessage(ex), ex);
      throw new ServiceNowException(
          String.format("Error occurred while getting serviceNow read only fields for standard template: %s",
              ExceptionUtils.getMessage(ex)),
          SERVICENOW_ERROR, USER, ex);
    }
  }
  private ServiceNowTaskNGResponse createImportSet(
      ServiceNowTaskNGParameters serviceNowTaskNGParameters, LogCallback executionLogCallback) {
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());
    saveLogs(executionLogCallback, "-----");
    saveLogs(executionLogCallback, "Initiating ServiceNow import set step");
    Map importDataJsonMap = null;
    if (!StringUtils.isBlank(serviceNowTaskNGParameters.getImportData())) {
      try {
        importDataJsonMap =
            JsonUtils.asObject(serviceNowTaskNGParameters.getImportData(), new TypeReference<Map<String, String>>() {});
      } catch (Exception ex) {
        Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
        log.error("Provided import data is not a valid json: {}", ExceptionUtils.getMessage(sanitizedException),
            sanitizedException);
        saveLogs(executionLogCallback, "Failed to create/execute import set: Provided import data is not a valid json",
            LogLevel.ERROR);
        throw new InvalidRequestException(String.format(
            "Provided import data is not a valid json: %s", ExceptionUtils.getMessage(sanitizedException)));
      }
    } else {
      importDataJsonMap = new HashMap<>();
    }
    saveLogs(executionLogCallback, "Executing import set .....");
    final Call<JsonNode> request =
        serviceNowRestClient.createImportSet(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO),
            serviceNowTaskNGParameters.getStagingTableName(), "all", importDataJsonMap);
    Response<JsonNode> response = null;
    try {
      response = request.execute();
      log.info("Response received from serviceNow for IMPORT_SET: {}", response);
      handleResponse(response, "Failed to create ServiceNow import set");
      JsonNode responseObj = response.body();
      if (responseObj.isEmpty()) {
        log.info(
            "Empty response received from serviceNow for IMPORT_SET, might because of missing permissions to view target record");
        saveLogs(executionLogCallback,
            "Succeeded to execute import set: Empty response received from serviceNow for IMPORT_SET, might because of missing permissions to view target record",
            LogLevel.WARN);
        return ServiceNowTaskNGResponse.builder()
            .serviceNowImportSetResponseNG(new ServiceNowImportSetResponseNG())
            .build();
      }
      JsonNode transformMapResultBody = responseObj.get("result");
      if (JsonNodeUtils.isNull(transformMapResultBody) || !transformMapResultBody.isArray()
          || transformMapResultBody.isEmpty()) {
        // even if no transform maps corresponds to table, then also one entry in result.
        throw new ServiceNowException(
            "Transformation details are missing or invalid in the response received from ServiceNow", SERVICENOW_ERROR,
            USER);
      }
      ServiceNowImportSetResponseNGBuilder serviceNowImportSetResponseNGBuilder =
          parseFromServiceNowImportSetResponse(transformMapResultBody, serviceNowConnectorDTO);
      serviceNowImportSetResponseNGBuilder.importSet(JsonNodeUtils.mustGetString(responseObj, "import_set"));
      serviceNowImportSetResponseNGBuilder.stagingTable(JsonNodeUtils.mustGetString(responseObj, "staging_table"));
      ServiceNowImportSetResponseNG serviceNowImportSetResponseNG = serviceNowImportSetResponseNGBuilder.build();
      log.info("Corresponding ServiceNow import set : {}", serviceNowImportSetResponseNG.getImportSet());
      saveLogs(executionLogCallback,
          LogHelper.color(
              String.format("Succeeded to execute import set: %s; please refer to step for input/output details",
                  serviceNowImportSetResponseNG.getImportSet()),
              LogColor.Cyan),
          LogLevel.INFO);
      return ServiceNowTaskNGResponse.builder().serviceNowImportSetResponseNG(serviceNowImportSetResponseNG).build();
    } catch (ServiceNowException e) {
      log.error("Failed to create/execute ServiceNow import set: {}", ExceptionUtils.getMessage(e), e);
      saveLogs(executionLogCallback,
          String.format("Failed to create/execute import set: %s", ExceptionUtils.getMessage(e)), LogLevel.ERROR);
      throw e;
    } catch (Exception ex) {
      log.error("Failed to create/execute ServiceNow import set: {}", ExceptionUtils.getMessage(ex), ex);
      saveLogs(executionLogCallback,
          String.format("Failed to create/execute import set: %s", ExceptionUtils.getMessage(ex)), LogLevel.ERROR);
      throw new ServiceNowException(String.format("Error occurred while creating/executing serviceNow import set: %s",
                                        ExceptionUtils.getMessage(ex)),
          SERVICENOW_ERROR, USER, ex);
    }
  }

  private ServiceNowTaskNGResponse getStagingTableList(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());

    final Call<JsonNode> request =
        serviceNowRestClient.getStagingTableList(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO));
    Response<JsonNode> response = null;
    try {
      response = Retry.decorateCallable(retry, () -> request.clone().execute()).call();
      log.info("Response received from serviceNow: {}", response);
      handleResponse(response, "Failed to get ServiceNow staging tables");
      JsonNode responseObj = response.body().get("result");
      List<ServiceNowStagingTable> stagingTableList = new ArrayList<>();
      if (responseObj != null && responseObj.isArray()) {
        for (final JsonNode stagingTable : responseObj) {
          String name = JsonNodeUtils.mustGetString(stagingTable, "name");
          stagingTableList.add(ServiceNowStagingTable.builder()
                                   .name(name)
                                   .label(JsonNodeUtils.getString(stagingTable, "label", name))
                                   .build());
        }
        return ServiceNowTaskNGResponse.builder().serviceNowStagingTableList(stagingTableList).build();
      } else {
        throw new ServiceNowException(
            String.format("Failed to fetch staging tables received response: {%s}", response.body()), SERVICENOW_ERROR,
            USER);
      }
    } catch (ServiceNowException e) {
      log.error("Failed to fetch staging tables: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    } catch (Exception ex) {
      log.error("Failed to fetch staging tables: {}", ExceptionUtils.getMessage(ex), ex);
      throw new ServiceNowException(
          String.format("Error occurred while fetching serviceNow staging tables: %s", ExceptionUtils.getMessage(ex)),
          SERVICENOW_ERROR, USER, ex);
    }
  }

  private ServiceNowTaskNGResponse getTicketTypes(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());
    List<ServiceNowTicketTypeDTO> standardTicketTypes =
        Arrays.stream(ServiceNowTicketTypeNG.values())
            .map(ticketType -> new ServiceNowTicketTypeDTO(ticketType.name(), ticketType.getDisplayName()))
            .collect(Collectors.toList());
    ServiceNowTaskNGResponse standardTicketTypeServiceResponse =
        ServiceNowTaskNGResponse.builder().serviceNowTicketTypeList(standardTicketTypes).build();
    final Call<JsonNode> request =
        serviceNowRestClient.getTicketTypes(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO));
    Response<JsonNode> response = null;
    try {
      response = Retry.decorateCallable(retry, () -> request.clone().execute()).call();
      log.info("Response received from serviceNow: {}", response);
      handleResponse(response, "Failed to get ServiceNow ticket types");
      JsonNode responseObj = response.body().get("result");
      List<ServiceNowTicketTypeDTO> ticketTypes = new ArrayList<>();
      if (responseObj != null && responseObj.isArray()) {
        for (final JsonNode ticketType : responseObj) {
          ticketTypes.add(new ServiceNowTicketTypeDTO(ticketType));
        }
        return ServiceNowTaskNGResponse.builder().serviceNowTicketTypeList(ticketTypes).build();
      } else {
        log.warn("Failed to fetch ticket types, received response: {}, defaulting to standard ticket types",
            response.body());
        return standardTicketTypeServiceResponse;
      }
    } catch (Exception e) {
      log.warn(
          "Failed to fetch ticket types, defaulting to standard ticket types: {}", ExceptionUtils.getMessage(e), e);
      return standardTicketTypeServiceResponse;
    }
  }

  private ServiceNowImportSetResponseNGBuilder parseFromServiceNowImportSetResponse(
      JsonNode responseObj, ServiceNowConnectorDTO serviceNowConnectorDTO) {
    List<ServiceNowImportSetTransformMapResult> transformMapResultList = new ArrayList<>();
    for (final JsonNode transformMapBody : responseObj) {
      String targetTableName = JsonNodeUtils.getString(transformMapBody, "table");
      transformMapResultList.add(
          ServiceNowImportSetTransformMapResult
              .builder()
              // these two fields are always present even in error case
              .transformMap(JsonNodeUtils.mustGetString(transformMapBody, "transform_map"))
              .status(JsonNodeUtils.mustGetString(transformMapBody, "status"))
              .targetTable(targetTableName)
              .displayName(JsonNodeUtils.getString(transformMapBody, "display_name"))
              .displayValue(JsonNodeUtils.getString(transformMapBody, "display_value"))
              .errorMessage(JsonNodeUtils.getString(transformMapBody, "error_message"))
              .statusMessage(JsonNodeUtils.getString(transformMapBody, "status_message"))
              .targetRecordURL(ServiceNowUtils.prepareTicketUrlFromTicketIdV2(serviceNowConnectorDTO.getServiceNowUrl(),
                  JsonNodeUtils.getString(transformMapBody, "sys_id"), targetTableName))
              .build());
    }
    return ServiceNowImportSetResponseNG.builder().serviceNowImportSetTransformMapResultList(transformMapResultList);
  }

  private List<ServiceNowFieldAllowedValueNG> buildAllowedValues(JsonNode choices) {
    ArrayList<ServiceNowFieldAllowedValueNG> allowedValues = new ArrayList<>();
    if (choices == null || !choices.isArray()) {
      return allowedValues;
    }
    if (choices.isArray()) {
      for (final JsonNode objNode : choices) {
        allowedValues.add(ServiceNowFieldAllowedValueNG.builder()
                              .id(objNode.get("value").textValue())
                              .name(objNode.get("label").textValue())
                              .build());
      }
    }
    return allowedValues;
  }

  private ServiceNowTaskNGResponse validateCredentials(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    String url = serviceNowConnectorDTO.getServiceNowUrl();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(url);
    final Call<JsonNode> request =
        serviceNowRestClient.validateConnection(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO, true));
    Response<JsonNode> response = null;
    try {
      response = Retry.decorateCallable(retry, () -> request.clone().execute()).call();
      if (isUnauthorizedError(response)) {
        log.warn("Failed to validate ServiceNow credentials using cached auth token; trying with fresh token");
        Call<JsonNode> requestWithoutCache =
            serviceNowRestClient.validateConnection(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO, false));
        response = Retry.decorateCallable(retry, () -> requestWithoutCache.clone().execute()).call();
      }
      handleResponse(response, "Failed to validate ServiceNow credentials");
      return ServiceNowTaskNGResponse.builder().build();
    } catch (ServiceNowException se) {
      log.error("Failed to authenticate to servicenow: {}", se.getMessage());
      if (se.getMessage().equals(INVALID_SERVICE_NOW_CREDENTIALS)) {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Check if the ServiceNow credentials are correct and you have necessary permissions to access the incident table",
            "The credentials provided are invalid or the user does not have necessary permissions to access incident table",
            new ServiceNowException(se.getMessage(), SERVICENOW_ERROR, USER));
      } else if (se.getMessage().equals(NOT_FOUND)) {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Check if the ServiceNow url is correct and accessible from delegate",
            "Not able to access the given ServiceNow url", new ServiceNowException(NOT_FOUND, SERVICENOW_ERROR, USER));
      } else {
        throw wrapInNestedException(se);
      }
    } catch (Exception e) {
      log.error("Failed to authenticate to servicenow. ");
      throw wrapInNestedException(new ServiceNowException(ExceptionUtils.getMessage(e), SERVICENOW_ERROR, USER, e));
    }
  }

  private ServiceNowRestClient getServiceNowRestClient(String url) {
    Retrofit retrofit = new Retrofit.Builder()
                            .client(getHttpClient(url))
                            .baseUrl(url)
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(ServiceNowRestClient.class);
  }

  @NotNull
  private OkHttpClient getHttpClient(String url) {
    return getOkHttpClientBuilder()
        .connectTimeout(TIME_OUT, TimeUnit.SECONDS)
        .readTimeout(TIME_OUT, TimeUnit.SECONDS)
        .proxy(Http.checkAndGetNonProxyIfApplicable(url))
        .build();
  }

  private void decryptRequestDTOs(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    if (!isNull(serviceNowConnectorDTO.getAuth()) && !isNull(serviceNowConnectorDTO.getAuth().getCredentials())) {
      secretDecryptionService.decrypt(
          serviceNowConnectorDTO.getAuth().getCredentials(), serviceNowTaskNGParameters.getEncryptionDetails());
    } else {
      secretDecryptionService.decrypt(serviceNowConnectorDTO, serviceNowTaskNGParameters.getEncryptionDetails());
    }
  }

  public static void handleResponse(Response<?> response, String message) throws IOException {
    if (response.isSuccessful()) {
      return;
    }
    if (response.code() == 401) {
      throw new ServiceNowException(INVALID_SERVICE_NOW_CREDENTIALS, SERVICENOW_ERROR, USER);
    }
    if (response.code() == 404) {
      throw new ServiceNowException(NOT_FOUND, SERVICENOW_ERROR, USER);
    }
    if (response.errorBody() == null) {
      throw new ServiceNowException(message + " : " + response.message(), SERVICENOW_ERROR, USER);
    }
    throw new ServiceNowException(message + " : " + response.errorBody().string(), SERVICENOW_ERROR, USER);
  }

  @NotNull
  private static WingsException wrapInNestedException(ServiceNowException e) {
    return NestedExceptionUtils.hintWithExplanationException(
        "Check if the ServiceNow url and credentials are correct and accessible from delegate",
        "Not able to access the given ServiceNow url with the credentials", e);
  }

  private void saveLogs(LogCallback executionLogCallback, String message) {
    if (executionLogCallback != null) {
      executionLogCallback.saveExecutionLog(message);
    }
  }

  private void saveLogs(LogCallback executionLogCallback, String message, LogLevel logLevel) {
    if (executionLogCallback != null) {
      executionLogCallback.saveExecutionLog(message, logLevel);
    }
  }

  private Retry buildRetryAndRegisterListeners() {
    final Retry exponentialRetry = RetryHelper.getExponentialRetry(this.getClass().getSimpleName(),
        new Class[] {ConnectException.class, TimeoutException.class, ConnectionShutdownException.class,
            StreamResetException.class, SocketTimeoutException.class});
    RetryHelper.registerEventListeners(exponentialRetry);
    return exponentialRetry;
  }
}
