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
import io.harness.retry.RetryHelper;
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
import io.github.resilience4j.retry.Retry;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.StreamResetException;
import org.jetbrains.annotations.NotNull;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class GcbServiceImpl implements GcbService {
  private static final String GCP_CREDENTIALS_ERROR_MESSAGE = "Invalid Google Cloud Platform credentials.";
  private static final String GCP_ERROR_MESSAGE = "Invalid Google Cloud Platform integration.";
  public static final String GCB_BASE_URL = "https://cloudbuild.googleapis.com/";
  public static final String GCS_BASE_URL = "https://storage.googleapis.com/storage/";
  private final GcpHelperService gcpHelperService;
  private final EncryptionService encryptionService;
  private final Retry exponentialRetry;

  @Inject
  public GcbServiceImpl(GcpHelperService gcpHelperService, EncryptionService encryptionService) {
    this.gcpHelperService = gcpHelperService;
    this.encryptionService = encryptionService;
    this.exponentialRetry = buildRetryAndRegisterListeners();
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
    } catch (InterruptedIOException | InterruptedException e) {
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
    } catch (InterruptedIOException | InterruptedException e) {
      log.error("Failed to run GCB trigger due to: ", e);
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new GcbClientException(GCP_CREDENTIALS_ERROR_MESSAGE, e);
    }
  }

  @Override
  public String fetchBuildLogs(
      GcpConfig gcpConfig, List<EncryptedDataDetail> encryptionDetails, String bucketName, String fileName) {
    Response<ResponseBody> response;
    int count = 0;
    int maxTries = 10;
    try {
      final String bucket = bucketName.replace("gs://", "");
      log.info("GCB_TASK - fetching logs");
      final GcsRestClient restClient = getRestClient(GcsRestClient.class, GCS_BASE_URL);

      while (true) {
        try {
          response = retry(
              () -> restClient.fetchLogs(getBasicAuthHeader(gcpConfig, encryptionDetails), bucket, fileName).execute());
          if (!response.isSuccessful()) {
            log.error("GCB_TASK - failed to fetch logs due to: " + response.errorBody().string());
            throw new GcbClientException(response.errorBody().string());
          }
          break;
        } catch (GcbClientException e) {
          log.warn("GCB fetching logs response is unsuccessful due to error {}", e.getMessage());
          TimeUnit.SECONDS.sleep(10);
          if (++count == maxTries) {
            throw e;
          }
        }
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
      return Retry.decorateCallable(exponentialRetry, action).call();

    } catch (Exception e) {
      // RETRY OPERATION GIVE TO US THE ORIGINAL EXCEPTION AND WE HANDLE IT AND RE-THROW TO THE CALLER.
      handleRetryException(e);

      // WHEN HANDLE CANNOT TAKE CARE OF THE EXCEPTION WE THROW A SPECIFIC ONE
      throw new GcbClientException(GCP_ERROR_MESSAGE, e);
    }
  }

  private Retry buildRetryAndRegisterListeners() {
    final Retry exponentialRetry = RetryHelper.getExponentialRetry(this.getClass().getSimpleName(),
        new Class[] {ConnectException.class, TimeoutException.class, ConnectionShutdownException.class,
            StreamResetException.class, SocketTimeoutException.class, GcbClientException.class, SocketException.class,
            IOException.class});
    RetryHelper.registerEventListeners(exponentialRetry);
    return exponentialRetry;
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
    } catch (IOException | InterruptedException e) {
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
    } catch (IOException | InterruptedException e) {
      throw new GcbClientException(GCP_CREDENTIALS_ERROR_MESSAGE, e);
    }
  }

  @VisibleForTesting
  <T> T getRestClient(final Class<T> client, String baseUrl) {
    OkHttpClient okHttpClient = getOkHttpClientBuilder()
                                    .connectTimeout(10, TimeUnit.SECONDS)
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
  String getBasicAuthHeader(GcpConfig gcpConfig, List<EncryptedDataDetail> encryptionDetails)
      throws IOException, InterruptedException {
    if (gcpConfig.isUseDelegateSelectors()) {
      return gcpHelperService.getDefaultCredentialsAccessToken(TaskType.GCB.name());
    }
    encryptionService.decrypt(gcpConfig, encryptionDetails, false);
    GoogleCredential gc = gcpHelperService.getGoogleCredential(
        gcpConfig.getServiceAccountKeyFileContent(), gcpConfig.isUseDelegateSelectors());
    return retry(() -> {
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
            throw new GcbClientException(
                "GCB_TASK - GCB task failed due to: " + e.getDetails().getErrorDescription(), e);
          }
          if (e.getDetails().getError() != null) {
            throw new GcbClientException("GCB_TASK - GCB task failed due to: " + e.getDetails().getError(), e);
          }
        }

        throw new GcbClientException("GCB_TASK - GCB task failed due to: " + e.getMessage(), e);
      }
    });
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
