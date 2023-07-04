/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eraro.ErrorCode.SERVICENOW_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.network.Http.getOkHttpClientBuilder;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
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
import io.harness.servicenow.ServiceNowUtils;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;
import software.wings.helpers.ext.servicenow.ServiceNowRestClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.resilience4j.retry.Retry;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

@OwnedBy(CDC)
@Slf4j
@Singleton
public class ServiceNowTaskNgHelper {
  public static final String INVALID_SERVICE_NOW_CREDENTIALS = "Invalid ServiceNow credentials";
  public static final String NOT_FOUND = "404 Not found";
  private final SecretDecryptionService secretDecryptionService;
  static final long TIME_OUT = 120;
  private final Retry retry = buildRetryAndRegisterListeners();

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
      case UPDATE_TICKET:
        return updateTicket(serviceNowTaskNGParameters);
      case GET_METADATA:
        return getMetadata(serviceNowTaskNGParameters);
      case GET_TEMPLATE:
        return getTemplateList(serviceNowTaskNGParameters);
      case IMPORT_SET:
        return createImportSet(serviceNowTaskNGParameters, executionLogCallback);
      case GET_IMPORT_SET_STAGING_TABLES:
        return getStagingTableList(serviceNowTaskNGParameters);
      case GET_TICKET_TYPES:
        return getTicketTypes(serviceNowTaskNGParameters);
      case GET_METADATA_V2:
        return getMetadataV2(serviceNowTaskNGParameters);
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
      String ticketUrlFromTicketId = ServiceNowUtils.prepareTicketUrlFromTicketNumber(
          serviceNowConnectorDTO.getServiceNowUrl(), ticketNg.getNumber(), serviceNowTaskNGParameters.getTicketType());
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

      ServiceNowTicketNGBuilder serviceNowTicketNGBuilder =
          fetchServiceNowTicketUsingSysId(ticketSysId, serviceNowTaskNGParameters);
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

  private ServiceNowTaskNGResponse updateTicket(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    validateServiceNowTaskInputs(serviceNowTaskNGParameters);
    if (!serviceNowTaskNGParameters.isUseServiceNowTemplate()) {
      return updateTicketWithoutTemplate(serviceNowTaskNGParameters);
    } else {
      return updateTicketUsingServiceNowTemplate(serviceNowTaskNGParameters);
    }
  }

  private ServiceNowTaskNGResponse updateTicketUsingServiceNowTemplate(
      ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    if (serviceNowTaskNGParameters.getTemplateName() == null) {
      throw new ServiceNowException("templateName can not be empty", SERVICENOW_ERROR, USER);
    }
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());

    final Call<JsonNode> request =
        serviceNowRestClient.updateUsingTemplate(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO),
            serviceNowTaskNGParameters.getTicketType().toLowerCase(), serviceNowTaskNGParameters.getTemplateName(),
            serviceNowTaskNGParameters.getTicketNumber());
    Response<JsonNode> response = null;

    try {
      log.info("updateUsingTemplate called for ticketType: {}, templateName: {}",
          serviceNowTaskNGParameters.getTicketType(), serviceNowTaskNGParameters.getTemplateName());
      response = Retry.decorateCallable(retry, () -> request.clone().execute()).call();
      log.info("Response received for updateUsingTemplate: {}", response);
      handleResponse(response, "Failed to update ServiceNow ticket");
      JsonNode responseObj = response.body().get("result");
      String ticketNumber = responseObj.get("record_number").asText();
      String ticketSysId = parseTicketSysIdFromResponse(responseObj);

      ServiceNowTicketNGBuilder serviceNowTicketNGBuilder =
          fetchServiceNowTicketUsingSysId(ticketSysId, serviceNowTaskNGParameters);

      log.info(String.format("Updated ticket with number: %s, sys_id: %s", ticketNumber, ticketSysId));
      String ticketUrlFromSysId = ServiceNowUtils.prepareTicketUrlFromTicketId(
          serviceNowConnectorDTO.getServiceNowUrl(), ticketSysId, serviceNowTaskNGParameters.getTicketType());
      return ServiceNowTaskNGResponse.builder()
          .ticket(serviceNowTicketNGBuilder.url(ticketUrlFromSysId).build())
          .build();
    } catch (ServiceNowException e) {
      log.error("Failed to update ServiceNow ticket: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    } catch (Exception ex) {
      log.error("Failed to update ServiceNow ticket: {}", ExceptionUtils.getMessage(ex), ex);
      throw new ServiceNowException(
          String.format("Error occurred while updating serviceNow ticket: %s", ExceptionUtils.getMessage(ex)),
          SERVICENOW_ERROR, USER, ex);
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
      String ticketSysId, ServiceNowTaskNGParameters parameters) {
    String query = "sys_id=" + ticketSysId;
    ServiceNowConnectorDTO serviceNowConnectorDTO = parameters.getServiceNowConnectorDTO();

    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());

    final Call<JsonNode> request =
        serviceNowRestClient.getIssue(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO),
            parameters.getTicketType().toString().toLowerCase(), query, "all");
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
    Map<String, ServiceNowFieldValueNG> fields = new HashMap<>();
    for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
      Map.Entry<String, JsonNode> f = it.next();
      String displayValue = JsonNodeUtils.getString(f.getValue(), "display_value");
      if (EmptyPredicate.isNotEmpty(displayValue)) {
        fields.put(f.getKey().trim(), ServiceNowFieldValueNG.builder().displayValue(displayValue.trim()).build());
      }
    }

    return ServiceNowTicketNG.builder().number(fields.get("number").getDisplayValue()).fields(fields);
  }

  private ServiceNowTaskNGResponse updateTicketWithoutTemplate(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());
    String ticketId = null;
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
            serviceNowTaskNGParameters.getTicketType().toLowerCase(), ticketId, "all", null, body);
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
          serviceNowConnectorDTO.getServiceNowUrl(), ticketNg.getNumber(), serviceNowTaskNGParameters.getTicketType());
      ticketNg.setUrl(ticketUrlFromTicketId);
      return ServiceNowTaskNGResponse.builder().ticket(ticketNg).build();
    } catch (ServiceNowException e) {
      log.error("Failed to update ServiceNow ticket: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    } catch (Exception ex) {
      log.error("Failed to update ServiceNow ticket: {}", ExceptionUtils.getMessage(ex), ex);
      throw new ServiceNowException(
          String.format("Error occurred while updating serviceNow ticket: %s", ExceptionUtils.getMessage(ex)),
          SERVICENOW_ERROR, USER, ex);
    }
  }

  private ServiceNowTaskNGResponse getTemplateList(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());

    final Call<JsonNode> request =
        serviceNowRestClient.getTemplateList(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO),
            serviceNowTaskNGParameters.getTicketType().toLowerCase(), serviceNowTaskNGParameters.getTemplateListLimit(),
            serviceNowTaskNGParameters.getTemplateListOffset(), serviceNowTaskNGParameters.getTemplateName());
    Response<JsonNode> response = null;
    try {
      response = Retry.decorateCallable(retry, () -> request.clone().execute()).call();
      log.info("Response received from serviceNow: {}", response);
      handleResponse(response, "Failed to get ServiceNow templates");
      JsonNode responseObj = response.body().get("result");
      if (responseObj != null && responseObj.get("queryResponse") != null) {
        JsonNode templateList = responseObj.get("queryResponse");
        List<ServiceNowTemplate> templateResponse = new ArrayList<>();
        for (JsonNode template : templateList) {
          String[] fieldList = template.get("readableValue").asText().split(".and.");
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

    final Call<JsonNode> request =
        serviceNowRestClient.getIssue(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO),
            serviceNowTaskNGParameters.getTicketType().toLowerCase(),
            "number=" + serviceNowTaskNGParameters.getTicketNumber(), "all");
    Response<JsonNode> response = null;

    try {
      response = Retry.decorateCallable(retry, () -> request.clone().execute()).call();
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
        serviceNowRestClient.validateConnection(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO));
    Response<JsonNode> response = null;
    try {
      response = Retry.decorateCallable(retry, () -> request.clone().execute()).call();
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
