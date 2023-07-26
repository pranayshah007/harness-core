/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rancher;

import static io.harness.exception.ngexception.RancherClientRuntimeException.RancherActionType.GENERATE_KUBECONFIG;
import static io.harness.exception.ngexception.RancherClientRuntimeException.RancherActionType.LIST_CLUSTERS;
import static io.harness.exception.sanitizer.ExceptionMessageSanitizer.sanitizeException;

import static io.github.resilience4j.retry.Retry.decorateCallable;
import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ngexception.RancherClientRuntimeException;
import io.harness.exception.ngexception.RancherClientRuntimeException.RancherActionType;
import io.harness.k8s.KubernetesApiRetryUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.resilience4j.retry.Retry;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;
import retrofit2.Response;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class RancherClusterClientImpl implements RancherClusterClient {
  private static final String RANCHER_LIST_CLUSTERS_ERROR_MESSAGE = "Failed to list clusters using rancher cluster.";
  private static final String RANCHER_ERROR_MESSAGE_RANCHER_URL = " Rancher URL: [%s].";
  private static final String RANCHER_ERROR_MESSAGE_RANCHER_URL_CLUSTER = " Rancher URL: [%s], Cluster: [%s].";

  private static final String RANCHER_GENERATE_KUBECONFIG_ERROR_MESSAGE =
      "Failed to generate kubeconfig from rancher cluster.";
  private static final String ENDPOINT_AND_STATUS_CODE_MESSAGE = " Endpoint: [%s]. Status code: [%s]. ";
  private static final String RANCHER_LIST_CLUSTERS_ERROR_MESSAGE_WITH_INFO =
      RANCHER_LIST_CLUSTERS_ERROR_MESSAGE + ENDPOINT_AND_STATUS_CODE_MESSAGE;
  private static final String RANCHER_GENERATE_KUBECFG_ERROR_MESSAGE_WITH_INFO =
      RANCHER_GENERATE_KUBECONFIG_ERROR_MESSAGE + ENDPOINT_AND_STATUS_CODE_MESSAGE;
  private static final Retry retry = KubernetesApiRetryUtils.buildRetryAndRegisterListeners("RancherClusterClient");

  @Inject private RancherRestClientFactory rancherRestClientFactory;

  @Override
  public RancherListClustersResponse listClusters(
      String bearerToken, String url, Map<String, String> pageRequestParams) {
    try {
      RancherClientRuntimeException.RancherRequestData rancherRequestData =
          RancherClientRuntimeException.RancherRequestData.builder().build();
      RancherRestClient rancherRestClient = rancherRestClientFactory.getRestClient(url, bearerToken);
      Response<RancherListClustersResponse> listClustersResponse = decorateCallable(retry, () -> {
        Call<RancherListClustersResponse> listClustersCall = rancherRestClient.listClusters(pageRequestParams);
        rancherRequestData.setEndpoint(listClustersCall.request().url().toString());
        return listClustersCall.execute();
      }).call();

      if (isResponseUnsuccessfulOrEmpty(listClustersResponse)) {
        constructAndThrowRancherException(listClustersResponse, rancherRequestData, LIST_CLUSTERS);
      }
      return listClustersResponse.body();
    } catch (RancherClientRuntimeException e) {
      log.error("Failed to list clusters using rancher {}", url, e);
      throw e;
    } catch (Exception e) {
      Exception sanitizedException = sanitizeException(e);
      log.error("Failed to list clusters using rancher {}", url, sanitizedException);
      throw new RancherClientRuntimeException(
          RANCHER_LIST_CLUSTERS_ERROR_MESSAGE + format(RANCHER_ERROR_MESSAGE_RANCHER_URL, url), sanitizedException);
    }
  }

  @Override
  public RancherGenerateKubeconfigResponse generateKubeconfig(String bearerToken, String url, String clusterId) {
    try {
      RancherClientRuntimeException.RancherRequestData rancherRequestData =
          RancherClientRuntimeException.RancherRequestData.builder().build();
      RancherRestClient rancherRestClient = rancherRestClientFactory.getRestClient(url, bearerToken);
      Response<RancherGenerateKubeconfigResponse> generateKubeconfigResponse = decorateCallable(retry, () -> {
        Call<RancherGenerateKubeconfigResponse> kubeconfigCall = rancherRestClient.generateKubeconfig(clusterId);
        rancherRequestData.setEndpoint(kubeconfigCall.request().url().toString());
        return kubeconfigCall.execute();
      }).call();

      if (isResponseUnsuccessfulOrEmpty(generateKubeconfigResponse)) {
        constructAndThrowRancherException(generateKubeconfigResponse, rancherRequestData, GENERATE_KUBECONFIG);
      }
      return generateKubeconfigResponse.body();
    } catch (RancherClientRuntimeException e) {
      log.error("Failed to generate kubeconfig for cluster {} using rancher {}", clusterId, url, e);
      throw e;
    } catch (Exception e) {
      Exception sanitizedException = sanitizeException(e);
      log.error("Failed to generate kubeconfig for cluster {} using rancher {}", clusterId, url, sanitizedException);
      throw new RancherClientRuntimeException(
          RANCHER_GENERATE_KUBECONFIG_ERROR_MESSAGE + format(RANCHER_ERROR_MESSAGE_RANCHER_URL_CLUSTER, url, clusterId),
          sanitizedException);
    }
  }

  private void constructAndThrowRancherException(Response<?> response,
      RancherClientRuntimeException.RancherRequestData requestData, RancherActionType actionType) {
    String errorMessage = StringUtils.EMPTY;
    int errorCode = response.code();
    if (GENERATE_KUBECONFIG == actionType) {
      errorMessage = format(RANCHER_GENERATE_KUBECFG_ERROR_MESSAGE_WITH_INFO, requestData.getEndpoint(), errorCode);
    } else if (LIST_CLUSTERS == actionType) {
      errorMessage = format(RANCHER_LIST_CLUSTERS_ERROR_MESSAGE_WITH_INFO, requestData.getEndpoint(), errorCode);
    }
    requestData.setErrorMessage(errorMessage);
    requestData.setCode(errorCode);
    requestData.setErrorBody(getErrorBodyFromResponse(response));
    throw new RancherClientRuntimeException(errorMessage, actionType, requestData);
  }

  private boolean isResponseUnsuccessfulOrEmpty(Response<?> rancherResponse) {
    return !rancherResponse.isSuccessful() || rancherResponse.body() == null;
  }

  private String getErrorBodyFromResponse(Response<?> response) {
    try {
      if (response.errorBody() != null) {
        return response.errorBody().string();
      }
      return StringUtils.EMPTY;
    } catch (Exception e) {
      return StringUtils.EMPTY;
    }
  }
}
