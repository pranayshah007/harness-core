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
public class RancherHint {
  public final String RANCHER_GENERIC_HINT =
      "Please check if the rancher connector is configured correctly and rancher cluster is reachable from the delegate.";
  public final String RANCHER_KUBECONFIG_GENERIC_HINT =
      "Please check if: %n1.Rancher connector is valid.  %n2.Rancher cluster is reachable from the delegate. %n3.`/v3/clusters/<clusterId>/?action=generateKubeconfig` endpoint is returning a valid kubeconfig. %nHow to use Rancher API: https://ranchermanager.docs.rancher.com/v2.7/pages-for-subheaders/about-the-api#how-to-use-the-api";
  public final String RANCHER_LIST_CLUSTERS_GENERIC_HINT =
      "Please check if: %n1.Rancher connector is valid.  %n2.Rancher cluster is reachable from the delegate. %n3.`/v3/clusters` endpoint is returning a valid list of clusters. %nHow to use Rancher API: https://ranchermanager.docs.rancher.com/v2.7/pages-for-subheaders/about-the-api#how-to-use-the-api";

  public final String RANCHER_KUBECONFIG_CLUSTER_NOT_FOUND =
      "Please check if the cluster identifier provided to rancher is correct.";
  public final String RANCHER_CLUSTER_INVALID_TOKEN =
      "Please check if the bearer token provided in the rancher connector is valid.";
  public final String RANCHER_CLUSTER_INVALID_PERMS =
      "Please check if the bearer token provided in the rancher connector has enough permissions to hit the `generateKubeconfig` endpoint.";
  public final String RANCHER_SOCKET_TIMEOUT_HINT = "Please check if rancher cluster is reachable from the delegate.";
}
