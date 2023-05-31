/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rancher;

import static io.harness.exception.sanitizer.ExceptionMessageSanitizer.sanitizeException;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ngexception.RancherClientRuntimeException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class RancherClusterClientImpl implements RancherClusterClient {
  private static final String RANCHER_LIST_CLUSTERS_ERROR_MESSAGE = "Failed to list clusters using Rancher [%s].";
  private static final String RANCHER_GENERATE_KUBECONFIG_ERROR_MESSAGE =
      "Failed to generate kubeconfig from cluster [%s] using Rancher [%s].";
  private static final String STATUS_CODE_MESSAGE = " Status code: [%s]";
  private static final String RANCHER_LIST_CLUSTERS_ERROR_MESSAGE_WITH_CODE =
      RANCHER_LIST_CLUSTERS_ERROR_MESSAGE + STATUS_CODE_MESSAGE;
  private static final String RANCHER_GENERATE_KUBECONFIG_ERROR_MESSAGE_WITH_CODE =
      RANCHER_GENERATE_KUBECONFIG_ERROR_MESSAGE + STATUS_CODE_MESSAGE;
  private static final RetryConfig retryConfig =
      RetryConfig.custom()
          .maxAttempts(3)
          .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(1)))
          .build();
  private static final Retry retry = Retry.of("RancherClusterClient", retryConfig);

  @Inject private RancherRestClientFactory rancherRestClientFactory;

  @Override
  public RancherListClustersResponse listClusters(String bearerToken, String url) {
    try {
      RancherRestClient rancherRestClient = rancherRestClientFactory.getRestClient(url, bearerToken);
      Response<RancherListClustersResponse> listClustersResponse =
          Retry.decorateCallable(retry, () -> rancherRestClient.listClusters().execute()).call();

      if (isResponseUnsuccessfulOrEmpty(listClustersResponse)) {
        throw createRancherRuntimeException(
            format(RANCHER_LIST_CLUSTERS_ERROR_MESSAGE_WITH_CODE, url, listClustersResponse.code()));
      }
      return listClustersResponse.body();
    } catch (RancherClientRuntimeException e) {
      log.error("Failed to list clusters using rancher {}", url, e);
      throw e;
    } catch (Exception e) {
      Exception sanitizedException = sanitizeException(e);
      log.error("Failed to list clusters using rancher {}", url, sanitizedException);
      throw createRancherRuntimeException(format(RANCHER_LIST_CLUSTERS_ERROR_MESSAGE, url), sanitizedException);
    }
  }

  @Override
  public RancherGenerateKubeconfigResponse generateKubeconfig(String bearerToken, String url, String clusterName) {
    try {
      RancherRestClient rancherRestClient = rancherRestClientFactory.getRestClient(url, bearerToken);
      Response<RancherGenerateKubeconfigResponse> generateKubeconfigResponse =
          Retry.decorateCallable(retry, () -> rancherRestClient.generateKubeconfig(clusterName).execute()).call();

      if (isResponseUnsuccessfulOrEmpty(generateKubeconfigResponse)) {
        throw createRancherRuntimeException(format(
            RANCHER_GENERATE_KUBECONFIG_ERROR_MESSAGE_WITH_CODE, clusterName, url, generateKubeconfigResponse.code()));
      }
      return generateKubeconfigResponse.body();
    } catch (RancherClientRuntimeException e) {
      log.error("Failed to generate kubeconfig for cluster {} using rancher {}", clusterName, url, e);
      throw e;
    } catch (Exception e) {
      Exception sanitizedException = sanitizeException(e);
      log.error("Failed to generate kubeconfig for cluster {} using rancher {}", clusterName, url, sanitizedException);
      throw createRancherRuntimeException(
          format(RANCHER_GENERATE_KUBECONFIG_ERROR_MESSAGE, clusterName, url), sanitizedException);
    }
  }

  private RancherClientRuntimeException createRancherRuntimeException(String errorMessage, Throwable cause) {
    return new RancherClientRuntimeException(errorMessage, cause);
  }

  private RancherClientRuntimeException createRancherRuntimeException(String errorMessage) {
    return new RancherClientRuntimeException(errorMessage);
  }

  private boolean isResponseUnsuccessfulOrEmpty(Response<?> rancherResponse) {
    return !rancherResponse.isSuccessful() || rancherResponse.body() == null;
  }
}
