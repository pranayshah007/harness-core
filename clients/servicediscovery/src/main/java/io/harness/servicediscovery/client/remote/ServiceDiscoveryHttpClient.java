/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.servicediscovery.client.remote;

import io.harness.ng.core.dto.ResponseDTO;
import io.harness.servicediscovery.client.beans.DiscoveredServiceConnectionResponse;
import io.harness.servicediscovery.client.beans.DiscoveredServiceResponse;
import io.harness.servicediscovery.client.beans.ServiceDiscoveryApplyManifestResponse;
import io.harness.servicediscovery.client.beans.ServiceDiscoveryResponseDTO;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ServiceDiscoveryHttpClient {
  String API_PREFIX = "api/v1/";

  @POST("api/v1/delegateCallback")
  Call<ResponseDTO<Boolean>> pushTaskResponse(@Body ServiceDiscoveryApplyManifestResponse response);

  @GET(API_PREFIX + "agents/{agentIdentifier}/discoveredservices")
  Call<ServiceDiscoveryResponseDTO<DiscoveredServiceResponse>> getDiscoveredServices(
      @Path("agentIdentifier") String agentIdentifier, @Query("accountIdentifier") String accountIdentifier,
      @Query("organizationIdentifier") String orgIdentifier, @Query("projectIdentifier") String projectIdentifier,
      @Query("all") boolean all);

  @GET(API_PREFIX + "agents/{agentIdentifier}/discoveredserviceconnections")
  Call<ServiceDiscoveryResponseDTO<DiscoveredServiceConnectionResponse>> getDiscoveredServiceConnection(
      @Path("agentIdentifier") String agentIdentifier, @Query("accountIdentifier") String accountIdentifier,
      @Query("organizationIdentifier") String orgIdentifier, @Query("projectIdentifier") String projectIdentifier,
      @Query("all") boolean all);
}
