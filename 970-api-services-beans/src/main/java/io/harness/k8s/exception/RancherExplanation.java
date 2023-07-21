/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.exception;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class RancherExplanation {
  public final String RANCHER_GENERIC_EXPLANATION = "Rancher failed to perform the desired action. Error: %n%s";
  public final String RANCHER_KUBECFG_GENERIC_EXPLANATION = "Rancher failed to generate kubeconfig with error: %n%s";
  public final String RANCHER_LIST_CLUSTERS_GENERIC_EXPLANATION = "Rancher failed to list clusters with error: %n%s";
  public final String RANCHER_LIST_CLUSTERS_GENERIC_EXPLANATION_WITH_DETAILS =
      "Rancher failed to list clusters:%nEndpoint: [%s] %nError Code: [%s] %nError Body: %s";
  public final String RANCHER_KUBECFG_GENERIC_EXPLANATION_WITH_DETAILS =
      "Rancher failed to generate kubeconfig%nEndpoint: [%s] %nError Code: [%s] %nError Body: %s";
  public final String RANCHER_KUBECFG_CLUSTER_NOT_FOUND =
      "Rancher failed to generate kubeconfig as it was not able to find the given cluster. %nEndpoint: [%s] %nError Code: [%s] %nError Body: %s";
  public final String RANCHER_KUBECFG_CLUSTER_INVALID_TOKEN =
      "Rancher failed to generate kubeconfig due to an invalid token. %nEndpoint: [%s] %nError Code: [%s] %nError Body: %s";
  public final String RANCHER_KUBECFG_CLUSTER_INVALID_PERMS =
      "Rancher failed to generate kubeconfig due to insufficient permissions. %nEndpoint: [%s] %nError Code: [%s] %nError Body: %s";

  public final String RANCHER_LIST_CLUSTERS_INVALID_TOKEN =
      "Rancher failed to list clusters due to an invalid token. %nEndpoint: [%s] %nError Code: [%s] %nError Body: %s";
  public final String RANCHER_LIST_CLUSTERS_INVALID_PERMS =
      "Rancher failed to list clusters due to insufficient permissions. %nEndpoint: [%s] %nError Code: [%s] %nError Body: %s";
  public final String RANCHER_SOCKET_TIMEOUT = "Delegate was unable to reach rancher cluster.";
}
