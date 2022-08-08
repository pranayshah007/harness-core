/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.filter;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ResponseMessage;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;
import io.harness.telemetry.utils.TelemetryDataUtils;

import com.google.inject.Singleton;
import io.dropwizard.jersey.errors.ErrorMessage;
import java.util.Collections;
import java.util.HashMap;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PIPELINE)
@Singleton
@Slf4j
public class APIErrorsTelemetrySenderFilter implements ContainerResponseFilter {
  public static final String ACCOUNT_IDENTIFIER = "account_identifier";
  public static final String PROJECT_IDENTIFIER = "project_identifier";
  public static final String ORG_IDENTIFIER = "org_identifier";
  public static final String API_ENDPOINT = "api_endpoint";
  public static final String API_TYPE = "api_type";
  public static final String RESPONSE_CODE = "response_code";
  public static final String ERROR_MESSAGE = "error_message";
  public static final String API_ERRORS = "api_errors";
  public static final String API_PATTERN = "api_pattern";
  public static final String SERVICE_NAME = "service_name";
  private final TelemetryReporter telemetryReporter;
  public final String serviceName;

  public APIErrorsTelemetrySenderFilter(TelemetryReporter telemetryReporter, String serviceName) {
    this.telemetryReporter = telemetryReporter;
    this.serviceName = serviceName;
  }

  @Override
  public void filter(
      ContainerRequestContext containerRequestContext, ContainerResponseContext containerResponseContext) {
    String accountIdentifier = getParameterValueFromUri(containerRequestContext, NGCommonEntityConstants.ACCOUNT_KEY);
    int responseCode = containerResponseContext.getStatus();
    HashMap<String, Object> properties = new HashMap<>();

    if (!StringUtils.isEmpty(accountIdentifier) && (responseCode < 200 || responseCode > 399)) {
      properties.put(ACCOUNT_IDENTIFIER, accountIdentifier);
      properties.put(
          ORG_IDENTIFIER, getParameterValueFromUri(containerRequestContext, NGCommonEntityConstants.ORG_KEY));
      properties.put(
          PROJECT_IDENTIFIER, getParameterValueFromUri(containerRequestContext, NGCommonEntityConstants.PROJECT_KEY));
      properties.put(SERVICE_NAME, serviceName);
      properties.put(API_ENDPOINT, containerRequestContext.getUriInfo().getRequestUri().toString());
      properties.put(API_PATTERN, TelemetryDataUtils.getApiPattern(containerRequestContext));
      properties.put(API_TYPE, containerRequestContext.getMethod());
      properties.put(RESPONSE_CODE, responseCode);
      if (containerResponseContext.getEntity() instanceof ErrorDTO) {
        properties.put(ERROR_MESSAGE, generateErrorMessage((ErrorDTO) containerResponseContext.getEntity()));
      } else if (containerResponseContext.getEntity() instanceof FailureDTO) {
        properties.put(ERROR_MESSAGE, ((FailureDTO) containerResponseContext.getEntity()).getMessage());
      } else if (containerResponseContext.getEntity() instanceof ErrorMessage) {
        properties.put(ERROR_MESSAGE, ((ErrorMessage) containerResponseContext.getEntity()).getMessage());
      }
      if (!properties.containsKey(ERROR_MESSAGE)) {
        log.warn("Error message is not captured for error: {}. and for Error entity class: {}",
            containerResponseContext.getEntity(), containerResponseContext.getEntity().getClass());
      }
      try {
        telemetryReporter.sendTrackEvent(API_ERRORS, null, accountIdentifier, properties,
            Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL,
            TelemetryOption.builder().sendForCommunity(false).build());
      } catch (Exception exception) {
        log.error("Error occurred while sending telemetry event for API Error : account={}, endpoint={}, exception={}",
            properties.get(ACCOUNT_IDENTIFIER), properties.get(API_ENDPOINT), exception);
      }
    }
  }

  private String getParameterValueFromUri(ContainerRequestContext containerRequestContext, String param) {
    String paramValue = containerRequestContext.getUriInfo().getQueryParameters().getFirst(param);
    if (StringUtils.isEmpty(paramValue)) {
      paramValue = containerRequestContext.getUriInfo().getPathParameters().getFirst(param);
    }
    return StringUtils.isEmpty(paramValue) ? "" : paramValue;
  }

  private String generateErrorMessage(ErrorDTO errorDTO) {
    StringBuilder message = new StringBuilder();
    if (EmptyPredicate.isEmpty(errorDTO.getResponseMessages())) {
      message.append(errorDTO.getMessage());
    } else {
      for (ResponseMessage responseMessage : errorDTO.getResponseMessages()) {
        message.append(responseMessage.getMessage()).append(". ");
      }
    }
    // TODO(BRIJESH): Add logic to get error info from all ErrorMetadataDTO implementations.
    return message.toString();
  }
}