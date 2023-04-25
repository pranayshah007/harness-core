/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.gcb;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.network.Http.getOkHttpClientBuilder;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.artifacts.gcr.exceptions.GcbClientException;
import io.harness.delegate.task.gcp.helpers.GcpHelperService;
import io.harness.network.Http;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;

import software.wings.beans.GcpConfig;
import software.wings.beans.TaskType;
import software.wings.helpers.ext.gcb.models.BuildOperationDetails;
import software.wings.helpers.ext.gcb.models.GcbBuildDetails;
import software.wings.helpers.ext.gcb.models.GcbBuildTriggers;
import software.wings.helpers.ext.gcb.models.GcbTrigger;
import software.wings.helpers.ext.gcb.models.RepoSource;
import software.wings.helpers.ext.gcs.GcsRestClient;
import software.wings.service.intfc.security.EncryptionService;

import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class GcbServiceImpl implements GcbService {
  private static final long RETRY_INITIAL_INTERVAL = 2500;
  private static final String GCP_CREDENTIALS_ERROR_MESSAGE = "Invalid Google Cloud Platform credentials.";
  private static final String GCP_ERROR_MESSAGE = "Invalid Google Cloud Platform integration.";
  public static final String GCB_BASE_URL = "https://cloudbuild.googleapis.com/";
  public static final String GCS_BASE_URL = "https://storage.googleapis.com/storage/";
  private final GcpHelperService gcpHelperService;
  private final EncryptionService encryptionService;
  private final Retry retry;

  @Inject
  public GcbServiceImpl(GcpHelperService gcpHelperService, EncryptionService encryptionService) {
    this.gcpHelperService = gcpHelperService;
    this.encryptionService = encryptionService;
    final RetryConfig config = RetryConfig.custom()
                                   .maxAttempts(5)
                                   .retryExceptions(SocketException.class)
                                   .intervalFunction(IntervalFunction.ofExponentialBackoff(RETRY_INITIAL_INTERVAL))
                                   .build();
    this.retry = Retry.of("GcbService", config);
  }

  @Override
  public BuildOperationDetails createBuild(
      GcpConfig gcpConfig, List<EncryptedDataDetail> encryptionDetails, GcbBuildDetails buildParams) {
    try {
      Response<BuildOperationDetails> response =
          getRestClient(GcbRestClient.class, GCB_BASE_URL)
              .createBuild(getBasicAuthHeader(gcpConfig, encryptionDetails), getProjectId(gcpConfig), buildParams)
              .execute();
      if (!response.isSuccessful()) {
        throw new GcbClientException(extractErrorMessage(response));
      }
      return response.body();
    } catch (InterruptedIOException e) {
      log.error("Failed to create GCB build due to: ", e);
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new GcbClientException(GCP_CREDENTIALS_ERROR_MESSAGE, e);
    }
  }

  @NotNull
  @Override
  public GcbBuildDetails getBuild(GcpConfig gcpConfig, List<EncryptedDataDetail> encryptionDetails, String buildId) {
    try {
      final GcbRestClient restClient = getRestClient(GcbRestClient.class, GCB_BASE_URL);
      Response<GcbBuildDetails> response = retry(
          ()
              -> restClient.getBuild(getBasicAuthHeader(gcpConfig, encryptionDetails), getProjectId(gcpConfig), buildId)
                     .execute());
      if (!response.isSuccessful()) {
        throw new GcbClientException(extractErrorMessage(response));
      }
      return response.body();
    } catch (InterruptedIOException | InterruptedException e) {
      log.error("Failed to fetch GCB build due to: ", e);
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new GcbClientException(GCP_CREDENTIALS_ERROR_MESSAGE, e);
    }
  }

  @Override
  public BuildOperationDetails runTrigger(
      GcpConfig gcpConfig, List<EncryptedDataDetail> encryptionDetails, String triggerId, RepoSource repoSource) {
    try {
      Response<BuildOperationDetails> response = getRestClient(GcbRestClient.class, GCB_BASE_URL)
                                                     .runTrigger(getBasicAuthHeader(gcpConfig, encryptionDetails),
                                                         getProjectId(gcpConfig), triggerId, repoSource)
                                                     .execute();
      if (response.isSuccessful()) {
        return response.body();
      }
      throw new GcbClientException(extractErrorMessage(response));
    } catch (InterruptedIOException e) {
      log.error("Failed to run GCB trigger due to: ", e);
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new GcbClientException(GCP_CREDENTIALS_ERROR_MESSAGE, e);
    }
  }

  @Override
  public String fetchBuildLogs(
      GcpConfig gcpConfig, List<EncryptedDataDetail> encryptionDetails, String bucketName, String fileName) {
    try {
      final String bucket = bucketName.replace("gs://", "");
      log.info("GCB_TASK - fetching logs");
      final GcsRestClient restClient = getRestClient(GcsRestClient.class, GCS_BASE_URL);
      Response<ResponseBody> response = retry(
          () -> restClient.fetchLogs(getBasicAuthHeader(gcpConfig, encryptionDetails), bucket, fileName).execute());
      if (!response.isSuccessful()) {
        log.error("GCB_TASK - failed to fetch logs due to: " + response.errorBody().string());
        throw new GcbClientException(response.errorBody().string());
      }
      log.info("GCB_TASK - logs are fetched");
      return response.body().string();
    } catch (InterruptedIOException | InterruptedException e) {
      log.error("GCB_TASK - Failed to fetch GCB build logs due to: ", e);
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new GcbClientException(GCP_CREDENTIALS_ERROR_MESSAGE, e);
    }
  }

  @NotNull
  @VisibleForTesting
  <T> T retry(Callable<T> action) throws InterruptedException, IOException {
    try {
      return Retry.decorateCallable(retry, action).call();

    } catch (Exception e) {
      // RETRY OPERATION GIVE TO US THE ORIGINAL EXCEPTION AND WE HANDLE IT AND RE-THROW TO THE CALLER.
      handleRetryException(e);

      // WHEN HANDLE CANNOT TAKE CARE OF THE EXCEPTION WE THROW A SPECIFIC ONE
      throw new GcbClientException(GCP_ERROR_MESSAGE, e);
    }
  }

  /**
   * Take care of the original retry exception and re-throw it when it is of specific types. Otherwise, do nothing.
   */
  @VisibleForTesting
  void handleRetryException(Exception e) throws InterruptedException, IOException {
    if (e instanceof InterruptedException) {
      throw(InterruptedException) e;
    } else if (e instanceof InterruptedIOException) {
      throw(InterruptedIOException) e;
    } else if (e instanceof IOException) {
      throw(IOException) e;
    }
  }

  @Override
  public List<GcbTrigger> getAllTriggers(GcpConfig gcpConfig, List<EncryptedDataDetail> encryptionDetails) {
    try {
      Response<GcbBuildTriggers> response =
          getRestClient(GcbRestClient.class, GCB_BASE_URL)
              .getAllTriggers(getBasicAuthHeader(gcpConfig, encryptionDetails), getProjectId(gcpConfig))
              .execute();
      if (!response.isSuccessful()) {
        log.error("GCB_TASK - failed to fetch GCB triggers");
        throw new GcbClientException(extractErrorMessage(response));
      }
      log.info("GCB_TASK - triggers have been fetched");
      return response.body().getTriggers();
    } catch (IOException e) {
      throw new GcbClientException(GCP_CREDENTIALS_ERROR_MESSAGE, e);
    }
  }

  @Override
  public GcbBuildDetails cancelBuild(GcpConfig gcpConfig, List<EncryptedDataDetail> encryptionDetails, String buildId) {
    try {
      Response<GcbBuildDetails> response =
          getRestClient(GcbRestClient.class, GCB_BASE_URL)
              .cancelBuild(getBasicAuthHeader(gcpConfig, encryptionDetails), getProjectId(gcpConfig), buildId)
              .execute();
      if (!response.isSuccessful()) {
        throw new GcbClientException(response.errorBody().string());
      }
      return response.body();
    } catch (IOException e) {
      throw new GcbClientException(GCP_CREDENTIALS_ERROR_MESSAGE, e);
    }
  }

  @VisibleForTesting
  <T> T getRestClient(final Class<T> client, String baseUrl) {
    OkHttpClient okHttpClient = getOkHttpClientBuilder()
                                    .connectTimeout(5, TimeUnit.SECONDS)
                                    .proxy(Http.checkAndGetNonProxyIfApplicable(baseUrl))
                                    .build();
    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(baseUrl)
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(client);
  }

  @VisibleForTesting
  String getBasicAuthHeader(GcpConfig gcpConfig, List<EncryptedDataDetail> encryptionDetails) throws IOException {
    if (gcpConfig.isUseDelegateSelectors()) {
      return gcpHelperService.getDefaultCredentialsAccessToken(TaskType.GCB.name());
    }
    encryptionService.decrypt(gcpConfig, encryptionDetails, false);
    GoogleCredential gc = gcpHelperService.getGoogleCredential(
        gcpConfig.getServiceAccountKeyFileContent(), gcpConfig.isUseDelegateSelectors());

    try {
      if (gc.refreshToken()) {
        return String.join(" ", "Bearer", gc.getAccessToken());
      } else {
        String msg = "Could not refresh token for google cloud provider";
        log.warn(msg);
        throw new GcbClientException(msg);
      }
    } catch (TokenResponseException e) {
      if (e.getDetails() != null) {
        if (e.getDetails().getErrorDescription() != null) {
          throw new GcbClientException("GCB_TASK - GCB task failed due to: " + e.getDetails().getErrorDescription(), e);
        }
        if (e.getDetails().getError() != null) {
          throw new GcbClientException("GCB_TASK - GCB task failed due to: " + e.getDetails().getError(), e);
        }
      }

      throw new GcbClientException("GCB_TASK - GCB task failed due to: " + e.getMessage(), e);
    }
  }

  @Override
  public String getProjectId(GcpConfig gcpConfig) {
    if (gcpConfig.isUseDelegateSelectors()) {
      return gcpHelperService.getClusterProjectId(TaskType.GCB.name());
    } else {
      return (String) (JsonUtils.asObject(new String(gcpConfig.getServiceAccountKeyFileContent()), HashMap.class))
          .get("project_id");
    }
  }

  private String extractErrorMessage(Response<?> response) {
    try {
      return (String) ((Map) JsonUtils.asObject(response.errorBody().string(), HashMap.class).get("error"))
          .get("message");
    } catch (IOException e) {
      throw new GcbClientException("Exception has occurred while reading error message", e);
    }
  }
}
