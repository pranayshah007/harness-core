/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.azure;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.model.tag.AzureListTagsResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;

@OwnedBy(HarnessTeam.CDC)
@TargetModule(HarnessModule._960_API_SERVICES)
public interface AzureManagementRestClient {
  @GET(
      "/subscriptions/{subscriptionId}/resourcegroups/{resourceGroup}/providers/Microsoft.ContainerService/managedClusters/{clusterName}/accessProfiles/clusterAdmin?api-version=2017-08-31")
  Call<AksGetCredentialsResponse>
  getAdminCredentials(@Header("Authorization") String bearerAuthHeader,
      @Path(value = "subscriptionId", encoded = true) String subscriptionId,
      @Path(value = "resourceGroup", encoded = true) String resourceGroup,
      @Path(value = "clusterName", encoded = true) String clusterName);

  @GET("subscriptions/{subscriptionId}/tagNames?api-version=2016-09-01")
  Call<AzureListTagsResponse> listTags(
      @Header("Authorization") String bearerAuthHeader, @Path("subscriptionId") String subscriptionId);
}
