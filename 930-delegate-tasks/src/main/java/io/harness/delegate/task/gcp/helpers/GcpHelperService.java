/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.gcp.helpers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.INVALID_CLOUD_PROVIDER;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.artifacts.gcr.exceptions.GcbClientException;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.gcp.helpers.GcpCredentialsHelperService;
import io.harness.gcp.helpers.GcpHttpTransportHelperService;
import io.harness.globalcontex.ErrorHandlingGlobalContextData;
import io.harness.manage.GlobalContextManager;
import io.harness.network.Http;
import io.harness.serializer.JsonUtils;

import software.wings.beans.TaskType;

import com.fasterxml.jackson.core.JsonParseException;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.container.Container;
import com.google.api.services.container.ContainerScopes;
import com.google.api.services.logging.v2.Logging;
import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.storage.Storage;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.io.IOUtils;

/**
 * Created by bzane on 2/22/17
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class GcpHelperService {
  public static final String LOCATION_DELIMITER = "/";
  public static final String ALL_LOCATIONS = "-";

  private static final int SLEEP_INTERVAL_SECS = 5;
  private static final int TIMEOUT_MINS = 30;
  private static final String GOOGLE_INTERNAL_COMPUTE_METADATA_DEFAULT_TOKEN_URL =
      "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token?scopes=";
  private static final String GOOGLE_META_BASE_URL = "http://metadata.google.internal/computeMetadata";
  private static final Long REQUEST_TIMEOUT_IN_SECONDS = 10L;
  private static final String HEADER_NAME = "Metadata-Flavor";
  private static final String HEADER_VALUE = "Google";

  @Inject private GcpHttpTransportHelperService gcpHttpTransportHelperService;
  @Inject private GcpCredentialsHelperService gcpCredentialsHelperService;

  /**
   * Gets a GCP container service.
   *
   * @param serviceAccountKeyFileContent
   * @param isUseDelegate
   *
   * @return the gke container service
   */
  public Container getGkeContainerService(char[] serviceAccountKeyFileContent, boolean isUseDelegate) {
    try {
      JacksonFactory jsonFactory = JacksonFactory.getDefaultInstance();
      HttpTransport transport = gcpHttpTransportHelperService.checkIfUseProxyAndGetHttpTransport();
      GoogleCredential credential = getGoogleCredential(serviceAccountKeyFileContent, isUseDelegate);
      return new Container.Builder(transport, jsonFactory, credential).setApplicationName("Harness").build();
    } catch (GeneralSecurityException e) {
      log.error("Security exception getting Google container service", e);
      throw new WingsException(INVALID_CLOUD_PROVIDER, USER)
          .addParam("message", "Invalid Google Cloud Platform credentials.");
    } catch (IOException e) {
      log.error("Error getting Google container service", e);
      throw new WingsException(INVALID_CLOUD_PROVIDER, USER)
          .addParam("message", "Invalid Google Cloud Platform credentials.");
    }
  }

  /**
   * Gets a GCS Service
   *
   * @param serviceAccountKeyFileContent
   * @param isUseDelegate
   *
   * @return the gcs storage service
   */
  public Storage getGcsStorageService(char[] serviceAccountKeyFileContent, boolean isUseDelegate) {
    try {
      JacksonFactory jsonFactory = JacksonFactory.getDefaultInstance();
      HttpTransport transport = gcpHttpTransportHelperService.checkIfUseProxyAndGetHttpTransport();
      GoogleCredential credential = getGoogleCredential(serviceAccountKeyFileContent, isUseDelegate);
      return new Storage.Builder(transport, jsonFactory, credential).setApplicationName("Harness").build();
    } catch (GeneralSecurityException e) {
      log.error("Security exception getting Google storage service", e);
      throw new WingsException(INVALID_CLOUD_PROVIDER, USER)
          .addParam("message", "Invalid Google Cloud Platform credentials.");
    } catch (IOException e) {
      log.error("Error getting Google storage service", e);
      throw new WingsException(INVALID_CLOUD_PROVIDER, USER)
          .addParam("message", "Invalid Google Cloud Platform credentials.");
    }
  }

  public Compute getGCEService(char[] serviceAccountKeyFileContent, String projectId, boolean isUseDelegate) {
    try {
      JacksonFactory jsonFactory = JacksonFactory.getDefaultInstance();
      HttpTransport transport = gcpHttpTransportHelperService.checkIfUseProxyAndGetHttpTransport();
      GoogleCredential credential = getGoogleCredential(serviceAccountKeyFileContent, isUseDelegate);
      return new Compute.Builder(transport, jsonFactory, credential).setApplicationName(projectId).build();
    } catch (GeneralSecurityException e) {
      log.error("Security exception getting Google storage service", e);
      throw new WingsException(INVALID_CLOUD_PROVIDER, USER)
          .addParam("message", "Invalid Google Cloud Platform credentials.");
    } catch (IOException e) {
      log.error("Error getting Google storage service", e);
      throw new WingsException(INVALID_CLOUD_PROVIDER, USER)
          .addParam("message", "Invalid Google Cloud Platform credentials.");
    }
  }

  public Monitoring getMonitoringService(char[] serviceAccountKeyFileContent, String projectId, boolean isUseDelegate) {
    try {
      JacksonFactory jsonFactory = JacksonFactory.getDefaultInstance();
      HttpTransport transport = gcpHttpTransportHelperService.checkIfUseProxyAndGetHttpTransport();
      GoogleCredential credential = getGoogleCredential(serviceAccountKeyFileContent, isUseDelegate);
      return new Monitoring.Builder(transport, jsonFactory, credential).setApplicationName(projectId).build();
    } catch (GeneralSecurityException e) {
      log.error("Security exception getting Google storage service", e);
      throw new WingsException(INVALID_CLOUD_PROVIDER, USER)
          .addParam("message", "Invalid Google Cloud Platform credentials.");
    } catch (IOException e) {
      log.error("Error getting Google storage service", e);
      throw new WingsException(INVALID_CLOUD_PROVIDER, USER)
          .addParam("message", "Invalid Google Cloud Platform credentials.");
    }
  }

  public Logging getLoggingResource(char[] serviceAccountKeyFileContent, String projectId, boolean isUseDelegate) {
    try {
      JacksonFactory jsonFactory = JacksonFactory.getDefaultInstance();
      HttpTransport transport = gcpHttpTransportHelperService.checkIfUseProxyAndGetHttpTransport();
      GoogleCredential credential = getGoogleCredential(serviceAccountKeyFileContent, isUseDelegate);
      return new Logging.Builder(transport, jsonFactory, credential).setApplicationName(projectId).build();
    } catch (GeneralSecurityException e) {
      log.error("Security exception getting Google storage service", e);
      throw new WingsException(INVALID_CLOUD_PROVIDER, USER)
          .addParam("message", "Invalid Google Cloud Platform credentials.");
    } catch (IOException e) {
      log.error("Error getting Google storage service", e);
      throw new WingsException(INVALID_CLOUD_PROVIDER, USER)
          .addParam("message", "Invalid Google Cloud Platform credentials.");
    }
  }

  public GoogleCredential getGoogleCredential(char[] serviceAccountKeyFileContent, boolean isUseDelegate)
      throws IOException {
    if (isUseDelegate) {
      return GcpCredentialsHelperService.getApplicationDefaultCredentials();
    }
    validateServiceAccountKey(serviceAccountKeyFileContent);
    return checkIfUseProxyAndGetGoogleCredentials(serviceAccountKeyFileContent);
  }

  private GoogleCredential checkIfUseProxyAndGetGoogleCredentials(char[] serviceAccountKeyFileContent)
      throws IOException {
    String tokenUri =
        (String) (JsonUtils.asObject(new String(serviceAccountKeyFileContent), HashMap.class)).get("token_uri");
    return Http.getProxyHostName() != null && !Http.shouldUseNonProxy(tokenUri)
        ? gcpCredentialsHelperService.getGoogleCredentialWithProxyConfiguredHttpTransport(serviceAccountKeyFileContent)
        : gcpCredentialsHelperService.getGoogleCredentialWithDefaultHttpTransport(serviceAccountKeyFileContent);
  }

  private void validateServiceAccountKey(char[] serviceAccountKeyFileContent) {
    if (isEmpty(serviceAccountKeyFileContent)) {
      throw new InvalidRequestException("Empty service key found. Unable to validate", USER);
    }
    try {
      GoogleCredential.fromStream(
          IOUtils.toInputStream(String.valueOf(serviceAccountKeyFileContent), Charset.defaultCharset()));
    } catch (Exception e) {
      if (e instanceof JsonParseException) {
        throw new InvalidRequestException("Provided Service account key is not in JSON format ", USER);
      }
      throw new InvalidRequestException("Invalid Google Cloud Platform credentials: " + e.getMessage(), e, USER);
    }
  }

  @SuppressWarnings("PMD")
  public String getDefaultCredentialsAccessToken(String taskTypeName) {
    try {
      String accessToken = getIdentifierFromUrl(
          taskTypeName, GOOGLE_INTERNAL_COMPUTE_METADATA_DEFAULT_TOKEN_URL + ContainerScopes.CLOUD_PLATFORM);
      return String.join(" ", "Bearer", (String) JsonUtils.asObject(accessToken, HashMap.class).get("access_token"));
    } catch (IOException | NullPointerException e) {
      log.error(taskTypeName + " - Failed to get accessToken due to: ", e);
      throw new InvalidRequestException("Can not retrieve accessToken from from cluster meta");
    }
  }

  @SuppressWarnings("PMD")
  public String getClusterProjectId(String taskTypeName) {
    try {
      String projectId =
          getIdentifierFromUrl(taskTypeName, "http://metadata.google.internal/computeMetadata/v1/project/project-id");
      log.info(taskTypeName + " - Fetched projectId from metadata server: " + projectId);
      return projectId;
    } catch (IOException | NullPointerException e) {
      throw new InvalidRequestException("Can not retrieve project-id from from cluster meta");
    }
  }

  public String getIdentifierFromUrl(String taskTypeName, String requestUrl) throws IOException {
    OkHttpClient client = getHttpClient(REQUEST_TIMEOUT_IN_SECONDS, GOOGLE_META_BASE_URL);
    Request request = getHttpRequest(HEADER_NAME, HEADER_VALUE, requestUrl);
    return extractBodyFromClientAndRequest(taskTypeName, client, request);
  }

  public String extractBodyFromClientAndRequest(String taskTypeName, OkHttpClient httpClient, Request request)
      throws IOException {
    okhttp3.Response response = httpClient.newCall(request).execute();
    if (response.isSuccessful()) {
      log.info(taskTypeName + " - Successfully fetched identifier from metadata server");
      return response.body().string();
    }

    log.error(taskTypeName + " - Failed to fetch data from metadata server: " + response);
    throw new GcbClientException("Failed to fetch data from metadata server");
  }

  public String getBasicAuthHeader(char[] serviceAccountKeyFileContent, boolean isUseDelegate) throws IOException {
    if (isUseDelegate) {
      return getDefaultCredentialsAccessToken(TaskType.GCP_TASK.name());
    }
    GoogleCredential gc = getGoogleCredential(serviceAccountKeyFileContent, isUseDelegate);
    try {
      if (gc.refreshToken()) {
        return Credentials.basic("_token", gc.getAccessToken());
      } else {
        String msg = "Could not refresh token for google cloud provider";
        log.warn(msg);
        throw new WingsException(ErrorCode.DEFAULT_ERROR_CODE, USER).addParam("message", msg);
      }
    } catch (TokenResponseException e) {
      ErrorHandlingGlobalContextData globalContextData =
          GlobalContextManager.get(ErrorHandlingGlobalContextData.IS_SUPPORTED_ERROR_FRAMEWORK);
      if (globalContextData != null && globalContextData.isSupportedErrorFramework()) {
        throw e;
      }
      throw new InvalidRequestException("407 Proxy Authentication Required");
    }
  }

  public String getProjectId(char[] serviceAccountKeyFileContent, boolean isUseDelegate) {
    if (isUseDelegate) {
      return getClusterProjectId(TaskType.GCP_TASK.name());
    } else {
      return (String) (JsonUtils.asObject(new String(serviceAccountKeyFileContent), HashMap.class)).get("project_id");
    }
  }

  public OkHttpClient getHttpClient(long timeoutInSeconds, String url) {
    return Http.getOkHttpClientBuilder()
        .connectTimeout(timeoutInSeconds, TimeUnit.SECONDS)
        .proxy(Http.checkAndGetNonProxyIfApplicable(url))
        .build();
  }

  public Request getHttpRequest(String headerName, String headerValue, String url) {
    return new Request.Builder().header(headerName, headerValue).url(url).build();
  }

  /**
   * Gets sleep interval secs.
   *
   * @return the sleep interval secs
   */
  public int getSleepIntervalSecs() {
    return SLEEP_INTERVAL_SECS;
  }

  /**
   * Gets timeout mins.
   *
   * @return the timeout mins
   */
  public int getTimeoutMins() {
    return TIMEOUT_MINS;
  }
}
