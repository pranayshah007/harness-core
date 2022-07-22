/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.agent.client;

import io.harness.NGCommonEntityConstants;
import io.harness.agent.beans.AgentMtlsEndpointDetails;
import io.harness.agent.beans.AgentMtlsEndpointRequest;
import io.harness.agent.utils.AgentMtlsApiConstants;
import io.harness.rest.RestResponse;

import javax.validation.constraints.NotNull;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Query;

// this client is used to communicate between ng manager and cg manager for all agent related stuff
public interface AgentNgManagerCgManagerClient {
  String AGENT_MTLS_ENDPOINT_API =
      AgentMtlsApiConstants.API_ROOT_RELATIVE_NG_INTERNAL + "/" + AgentMtlsApiConstants.API_PATH_ENDPOINT;
  String AGENT_MTLS_PREFIX_AVAILABLE_API =
      AgentMtlsApiConstants.API_ROOT_RELATIVE_NG_INTERNAL + "/" + AgentMtlsApiConstants.API_PATH_CHECK_AVAILABILITY;

  //------------------------ Agent mTLS Endpoint Apis -----------------------------------

  @POST(AGENT_MTLS_ENDPOINT_API)
  Call<RestResponse<AgentMtlsEndpointDetails>> createEndpointForAccount(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Body @NotNull AgentMtlsEndpointRequest endpointRequest);

  @PUT(AGENT_MTLS_ENDPOINT_API)
  Call<RestResponse<AgentMtlsEndpointDetails>> updateEndpointForAccount(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Body @NotNull AgentMtlsEndpointRequest endpointRequest);

  @PATCH(AGENT_MTLS_ENDPOINT_API)
  Call<RestResponse<AgentMtlsEndpointDetails>> patchEndpointForAccount(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Body @NotNull AgentMtlsEndpointRequest patchRequest);

  @DELETE(AGENT_MTLS_ENDPOINT_API)
  Call<RestResponse<Boolean>> deleteEndpointForAccount(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier);

  @GET(AGENT_MTLS_ENDPOINT_API)
  Call<RestResponse<AgentMtlsEndpointDetails>> getEndpointForAccount(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier);

  @GET(AGENT_MTLS_PREFIX_AVAILABLE_API)
  Call<RestResponse<Boolean>> isDomainPrefixAvailable(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(AgentMtlsApiConstants.API_PARAM_DOMAIN_PREFIX_NAME) @NotNull String domainPrefix);
}
