/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.exception;

import static io.harness.k8s.exception.RancherExplanation.RANCHER_GENERIC_EXPLANATION;
import static io.harness.k8s.exception.RancherExplanation.RANCHER_KUBECFG_CLUSTER_INVALID_PERMS;
import static io.harness.k8s.exception.RancherExplanation.RANCHER_KUBECFG_CLUSTER_INVALID_TOKEN;
import static io.harness.k8s.exception.RancherExplanation.RANCHER_KUBECFG_CLUSTER_NOT_FOUND;
import static io.harness.k8s.exception.RancherExplanation.RANCHER_KUBECFG_GENERIC_EXPLANATION;
import static io.harness.k8s.exception.RancherExplanation.RANCHER_KUBECFG_GENERIC_EXPLANATION_WITH_DETAILS;
import static io.harness.k8s.exception.RancherExplanation.RANCHER_LIST_CLUSTERS_GENERIC_EXPLANATION;
import static io.harness.k8s.exception.RancherExplanation.RANCHER_LIST_CLUSTERS_GENERIC_EXPLANATION_WITH_DETAILS;
import static io.harness.k8s.exception.RancherExplanation.RANCHER_LIST_CLUSTERS_INVALID_PERMS;
import static io.harness.k8s.exception.RancherExplanation.RANCHER_LIST_CLUSTERS_INVALID_TOKEN;
import static io.harness.k8s.exception.RancherExplanation.RANCHER_SOCKET_TIMEOUT;
import static io.harness.k8s.exception.RancherHint.RANCHER_CLUSTER_INVALID_PERMS;
import static io.harness.k8s.exception.RancherHint.RANCHER_CLUSTER_INVALID_TOKEN;
import static io.harness.k8s.exception.RancherHint.RANCHER_GENERIC_HINT;
import static io.harness.k8s.exception.RancherHint.RANCHER_KUBECONFIG_CLUSTER_NOT_FOUND;
import static io.harness.k8s.exception.RancherHint.RANCHER_KUBECONFIG_GENERIC_HINT;
import static io.harness.k8s.exception.RancherHint.RANCHER_LIST_CLUSTERS_GENERIC_HINT;
import static io.harness.k8s.exception.RancherHint.RANCHER_SOCKET_TIMEOUT_HINT;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.FailureType;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.exception.ngexception.RancherClientRuntimeException;
import io.harness.exception.ngexception.RancherClientRuntimeException.RancherRequestData;

import java.net.SocketTimeoutException;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class RancherRuntimeExceptionHandler implements ExceptionHandler {
  public static Set<Class<? extends Exception>> exceptions() {
    return Set.of(RancherClientRuntimeException.class);
  }

  @Override
  public WingsException handleException(Exception exception) {
    RancherClientRuntimeException runtimeException = (RancherClientRuntimeException) exception;
    if (runtimeException.getRequestData() != null) {
      switch (runtimeException.getActionType()) {
        case GENERATE_KUBECONFIG:
          return handleGenerateKubeConfigException(runtimeException);
        case LIST_CLUSTERS:
          return handleListClustersException(runtimeException);
        default:
          break;
      }
      RancherRequestData requestData = runtimeException.getRequestData();
      String errorMessage = requestData.getErrorMessage() + requestData.getErrorBody();
      return NestedExceptionUtils.hintWithExplanationException(
          RANCHER_GENERIC_HINT, format(RANCHER_GENERIC_EXPLANATION, errorMessage));
    }

    Throwable cause = runtimeException.getCause();
    if (cause instanceof SocketTimeoutException) {
      return NestedExceptionUtils.hintWithExplanationException(RANCHER_SOCKET_TIMEOUT_HINT, RANCHER_SOCKET_TIMEOUT,
          new KubernetesTaskException(runtimeException.getMessage(), FailureType.TIMEOUT_ERROR));
    }

    return NestedExceptionUtils.hintWithExplanationException(
        RANCHER_GENERIC_HINT, format(RANCHER_GENERIC_EXPLANATION, runtimeException.getMessage()), cause);
  }

  private WingsException handleListClustersException(RancherClientRuntimeException runtimeException) {
    RancherRequestData requestData = runtimeException.getRequestData();
    if (requestData != null) {
      switch (requestData.getCode()) {
        case 401:
          return NestedExceptionUtils.hintWithExplanationException(RANCHER_CLUSTER_INVALID_TOKEN,
              format(RANCHER_LIST_CLUSTERS_INVALID_TOKEN, requestData.getEndpoint(), requestData.getCode(),
                  requestData.getErrorBody()),
              new KubernetesTaskException(requestData.getErrorMessage()));
        case 403:
          return NestedExceptionUtils.hintWithExplanationException(RANCHER_CLUSTER_INVALID_PERMS,
              format(RANCHER_LIST_CLUSTERS_INVALID_PERMS, requestData.getEndpoint(), requestData.getCode(),
                  requestData.getErrorBody()),
              new KubernetesTaskException(requestData.getErrorMessage()));
        default:
          break;
      }
      return NestedExceptionUtils.hintWithExplanationException(RANCHER_LIST_CLUSTERS_GENERIC_HINT,
          format(RANCHER_LIST_CLUSTERS_GENERIC_EXPLANATION_WITH_DETAILS, requestData.getEndpoint(),
              requestData.getCode(), requestData.getErrorBody()),
          new KubernetesTaskException(requestData.getErrorMessage()));
    }
    return NestedExceptionUtils.hintWithExplanationException(RANCHER_LIST_CLUSTERS_GENERIC_HINT,
        format(RANCHER_LIST_CLUSTERS_GENERIC_EXPLANATION, runtimeException.getMessage()));
  }

  private WingsException handleGenerateKubeConfigException(RancherClientRuntimeException runtimeException) {
    RancherRequestData requestData = runtimeException.getRequestData();
    if (requestData != null) {
      switch (requestData.getCode()) {
        case 404:
          return NestedExceptionUtils.hintWithExplanationException(RANCHER_KUBECONFIG_CLUSTER_NOT_FOUND,
              format(RANCHER_KUBECFG_CLUSTER_NOT_FOUND, requestData.getEndpoint(), requestData.getCode(),
                  requestData.getErrorBody()),
              new KubernetesTaskException(requestData.getErrorMessage()));
        case 401:
          return NestedExceptionUtils.hintWithExplanationException(RANCHER_CLUSTER_INVALID_TOKEN,
              format(RANCHER_KUBECFG_CLUSTER_INVALID_TOKEN, requestData.getEndpoint(), requestData.getCode(),
                  requestData.getErrorBody()),
              new KubernetesTaskException(requestData.getErrorMessage()));
        case 403:
          return NestedExceptionUtils.hintWithExplanationException(RANCHER_CLUSTER_INVALID_PERMS,
              format(RANCHER_KUBECFG_CLUSTER_INVALID_PERMS, requestData.getEndpoint(), requestData.getCode(),
                  requestData.getErrorBody()),
              new KubernetesTaskException(requestData.getErrorMessage()));
        default:
          break;
      }

      return NestedExceptionUtils.hintWithExplanationException(RANCHER_KUBECONFIG_GENERIC_HINT,
          format(RANCHER_KUBECFG_GENERIC_EXPLANATION_WITH_DETAILS, requestData.getEndpoint(), requestData.getCode(),
              requestData.getErrorBody()),
          new KubernetesTaskException(requestData.getErrorMessage()));
    }
    return NestedExceptionUtils.hintWithExplanationException(
        RANCHER_KUBECONFIG_GENERIC_HINT, format(RANCHER_KUBECFG_GENERIC_EXPLANATION, runtimeException.getMessage()));
  }
}
