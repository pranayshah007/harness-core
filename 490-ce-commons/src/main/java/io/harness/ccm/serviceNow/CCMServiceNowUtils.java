/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.serviceNow;

import static io.harness.eraro.ErrorCode.SERVICENOW_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.network.Http.getOkHttpClientBuilder;

import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.task.servicenow.ServiceNowAuthNgHelper;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ServiceNowException;
import io.harness.exception.WingsException;
import io.harness.jackson.JsonNodeUtils;
import io.harness.network.Http;
import io.harness.retry.RetryHelper;
import io.harness.servicenow.ServiceNowFieldValueNG;
import io.harness.servicenow.ServiceNowTicketNG;
import io.harness.servicenow.ServiceNowTicketNG.ServiceNowTicketNGBuilder;
import io.harness.servicenow.ServiceNowUtils;

import software.wings.helpers.ext.servicenow.ServiceNowRestClient;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.retry.Retry;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

@Slf4j
public class CCMServiceNowUtils {
  private static final long TIME_OUT = 120;
  private static final String INVALID_SERVICE_NOW_CREDENTIALS = "Invalid ServiceNow credentials";
  private static final String NOT_FOUND = "404 Not found";
  private static final String SERVICENOW_TICKET_STATE_KEY = "state";
  private final Retry retry = buildRetryAndRegisterListeners();

  public ServiceNowTicketNG createTicket(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    validateServiceNowTaskInputs(serviceNowTaskNGParameters);
    if (!serviceNowTaskNGParameters.isUseServiceNowTemplate()) {
      return createTicketWithoutTemplate(serviceNowTaskNGParameters);
    } else {
      return createTicketUsingServiceNowTemplate(serviceNowTaskNGParameters);
    }
  }

  public ServiceNowTicketNG getTicket(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    ServiceNowConnectorDTO serviceNowConnectorDTO = serviceNowTaskNGParameters.getServiceNowConnectorDTO();
    ServiceNowRestClient serviceNowRestClient = getServiceNowRestClient(serviceNowConnectorDTO.getServiceNowUrl());

    final Call<JsonNode> request =
        serviceNowRestClient.getIssue(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO, true),
            serviceNowTaskNGParameters.getTicketType().toLowerCase(),
            "number=" + serviceNowTaskNGParameters.getTicketNumber(), "all");
    Response<JsonNode> response = null;

    try {
      response = Retry.decorateCallable(retry, () -> request.clone().execute()).call();
      if (isUnauthorizedError(response)) {
        log.warn("Failed to get serviceNow ticket using cached auth token; trying with fresh token");
        Call<JsonNode> requestWithoutCache =
            serviceNowRestClient.getIssue(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO, false),
                serviceNowTaskNGParameters.getTicketType().toLowerCase(),
                "number=" + serviceNowTaskNGParameters.getTicketNumber(), "all");
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
        return ServiceNowTicketNG.builder()
            .number(serviceNowTaskNGParameters.getTicketNumber())
            .fields(fieldValues)
            .url(ServiceNowUtils.prepareTicketUrlFromTicketNumber(serviceNowConnectorDTO.getServiceNowUrl(),
                serviceNowTaskNGParameters.getTicketNumber(), serviceNowTaskNGParameters.getTicketType()))
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

  public String getStatus(ServiceNowTicketNG serviceNowTicketNG) {
    return serviceNowTicketNG.getFields().get(SERVICENOW_TICKET_STATE_KEY).getDisplayValue();
  }

  private ServiceNowTicketNG createTicketWithoutTemplate(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
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
      return ticketNg;
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

  private ServiceNowTicketNG createTicketUsingServiceNowTemplate(
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
      return serviceNowTicketNGBuilder.url(ticketUrlFromSysId).build();
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

  private void validateServiceNowTaskInputs(ServiceNowTaskNGParameters serviceNowTaskNGParameters) {
    String ticketType = serviceNowTaskNGParameters.getTicketType();
    // allowing custom tables too , hence ticketType is not validated to be in ServiceNowTicketTypeNG
    if (StringUtils.isBlank(ticketType)) {
      throw new InvalidRequestException("Blank ticketType provided for ServiceNow");
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

  public void handleResponse(Response<?> response, String message) throws IOException {
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

  private Retry buildRetryAndRegisterListeners() {
    final Retry exponentialRetry = RetryHelper.getExponentialRetry(this.getClass().getSimpleName(),
        new Class[] {ConnectException.class, TimeoutException.class, ConnectionShutdownException.class,
            StreamResetException.class, SocketTimeoutException.class});
    RetryHelper.registerEventListeners(exponentialRetry);
    return exponentialRetry;
  }

  private boolean isUnauthorizedError(Response<?> response) {
    return 401 == response.code();
  }
}
