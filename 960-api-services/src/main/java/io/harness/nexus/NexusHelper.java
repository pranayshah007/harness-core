/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.nexus;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.NexusRegistryException;
import io.harness.network.Http;

import java.io.IOException;
import javax.net.ssl.SSLHandshakeException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;
import retrofit2.Converter;
import retrofit2.Response;
import retrofit2.Retrofit;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class NexusHelper {
  public static void handleException(IOException e) {
    throw NestedExceptionUtils.hintWithExplanationException(
        "Ensure that the Nexus server is up and running. Retry the action in sometime or Report the issue with delegate logs",
        "Failed to perform the operation", new InvalidArtifactServerException(ExceptionUtils.getMessage(e), USER));
  }

  public static String getBaseUrl(NexusRequest nexusRequest) {
    return getBaseUrl(nexusRequest.getNexusUrl());
  }

  public static String getBaseUrl(String url) {
    return url.endsWith("/") ? url : url + "/";
  }

  static Retrofit getRetrofit(NexusRequest nexusRequest, Converter.Factory converterFactory) {
    String baseUrl = getBaseUrl(nexusRequest);
    return getRetrofit(baseUrl, nexusRequest.isCertValidationRequired(), converterFactory);
  }

  static Retrofit getRetrofit(String baseUrl, boolean isCertValidationRequired, Converter.Factory converterFactory) {
    return new Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(converterFactory)
        .client(Http.getOkHttpClient(baseUrl, isCertValidationRequired))
        .build();
  }

  public static void checkSSLHandshakeException(Exception e) {
    if (e.getCause() instanceof SSLHandshakeException
        || ExceptionUtils.getMessage(e).contains("unable to find valid certification path")) {
      throw NestedExceptionUtils.hintWithExplanationException("Ensure that the SSL certificate has not expired",
          "SSL certificate is invalid",
          new ArtifactServerException("Certificate validation failed:" + getRootCauseMessage(e), e));
    }
  }

  public static boolean isSuccessful(Response<?> response) {
    if (response == null) {
      return false;
    }
    if (!response.isSuccessful()) {
      log.error("Request not successful. Reason: {}", response);
      int code = response.code();
      switch (code) {
        case 404:
          return false;
        case 401:
          throw NestedExceptionUtils.hintWithExplanationException(
              "Update the connector credentials with correct values", "The connector credentials are incorrect",
              new InvalidArtifactServerException("Invalid Nexus credentials", USER));
        case 405:
          throw NestedExceptionUtils.hintWithExplanationException(
              "Ensure that the connector URL is correct & the provided credentials have all the required permissions",
              "Failed to perform action",
              new InvalidArtifactServerException("Method not allowed " + response.message(), USER));
        default:
          throw NestedExceptionUtils.hintWithExplanationException(
              "Please retry the operation or check the Delegate logs for more information", "Failed to perform action.",
              new InvalidArtifactServerException(response.message(), USER));
      }
    }
    return true;
  }

  public static boolean isRequestSuccessful(Call request, Response<?> response) {
    if (response == null) {
      throw NestedExceptionUtils.hintWithExplanationException("Please check if Nexus registry is running properly.",
          String.format("Received a 'null' response for API call '%s %s' which is not expected.",
              request.request().method(), request.request().url()),
          new NexusRegistryException("'null' response was received!", USER));
    }

    if (!response.isSuccessful()) {
      log.error("Request was not successful. Reason: {}", response);
      int code = response.code();
      switch (code) {
        case 404:
          return false;
        case 401:
          throw NestedExceptionUtils.hintWithExplanationException(
              "Update the connector credentials with correct values", "The connector credentials are incorrect",
              new NexusRegistryException("Invalid Nexus credentials", USER));
        case 405:
          throw NestedExceptionUtils.hintWithExplanationException(
              "Ensure that the connector URL is correct & the provided credentials have all the required permissions",
              "Failed to perform action",
              new NexusRegistryException("Method not allowed: " + response.message(), USER));
        default:
          throw NestedExceptionUtils.hintWithExplanationException(
              "Please retry the operation or check the Delegate logs for more information", "Failed to perform action.",
              new NexusRegistryException(response.message(), USER));
      }
    }

    if (response.body() == null) {
      throw NestedExceptionUtils.hintWithExplanationException("Please check if Nexus registry is running properly.",
          String.format("Did not receive a body in the response for API call '%s %s' with response code %s.",
              request.request().method(), request.request().url(), response.code()),
          new NexusRegistryException("Response received but message body was empty.", USER));
    }

    return true;
  }

  public static boolean isNexusVersion2(NexusRequest nexusConfig) {
    if (nexusConfig.getVersion() == null || nexusConfig.getVersion().equalsIgnoreCase("2.x")) {
      return true;
    }
    return false;
  }
}
