/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.client;

import io.harness.azure.model.AzureConstants;

import software.wings.helpers.ext.azure.AksClusterCredentials;

import retrofit2.Call;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface AzureKubernetesRestClient {
  @POST("subscriptions/{" + AzureConstants.SUBSCRIPTION + "}/resourceGroups/{" + AzureConstants.RESOURCE_GROUP
      + "}/providers/Microsoft.ContainerService/managedClusters/{" + AzureConstants.AKS_CLUSTER_NAME
      + "}/listClusterUserCredential?api-version=2022-02-01")
  Call<AksClusterCredentials>
  listClusterUserCredential(@Header("Authorization") String accessToken,
      @Path(value = AzureConstants.SUBSCRIPTION) String subscription,
      @Path(value = AzureConstants.RESOURCE_GROUP) String resourceGroup,
      @Path(value = AzureConstants.AKS_CLUSTER_NAME) String aksClusterName,
      @Query(value = AzureConstants.FORMAT) String format);

  @POST("subscriptions/{" + AzureConstants.SUBSCRIPTION + "}/resourceGroups/{" + AzureConstants.RESOURCE_GROUP
      + "}/providers/Microsoft.ContainerService/managedClusters/{" + AzureConstants.AKS_CLUSTER_NAME
      + "}/listClusterAdminCredential?api-version=2022-02-01")
  Call<AksClusterCredentials>
  listClusterAdminCredential(@Header("Authorization") String accessToken,
      @Path(value = AzureConstants.SUBSCRIPTION) String subscription,
      @Path(value = AzureConstants.RESOURCE_GROUP) String resourceGroup,
      @Path(value = AzureConstants.AKS_CLUSTER_NAME) String aksClusterName);
}
