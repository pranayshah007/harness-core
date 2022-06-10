/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.delegate.client;

import io.harness.NGCommonEntityConstants;
import io.harness.delegate.DelegateDownloadResponse;
import io.harness.delegate.beans.DelegateDownloadRequest;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroupDTO;
import io.harness.delegate.beans.DelegateGroupListing;
import io.harness.delegate.beans.DelegateGroupTags;
import io.harness.delegate.beans.DelegateMtlsEndpointDetails;
import io.harness.delegate.beans.DelegateMtlsEndpointRequest;
import io.harness.delegate.beans.DelegateSetupDetails;
import io.harness.delegate.beans.DelegateTokenDetails;
import io.harness.delegate.beans.DelegateTokenStatus;
import io.harness.delegate.utils.DelegateMtlsApiConstants;
import io.harness.rest.RestResponse;

import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Query;

// this client is used to communicate between ng manager and cg manager for all delegate related stuff
public interface DelegateNgManagerCgManagerClient {
  String DELEGATE_TOKEN_NG_API = "delegate-token/ng";
  String DELEGATE_GROUP_TAGS_API = "delegate-group-tags-internal";
  String DELEGATE_MTLS_ENDPOINT_API =
      DelegateMtlsApiConstants.API_ROOT_RELATIVE_NG_INTERNAL + "/" + DelegateMtlsApiConstants.API_PATH_ENDPOINT;
  String DELEGATE_MTLS_PREFIX_AVAILABLE_API = DelegateMtlsApiConstants.API_ROOT_RELATIVE_NG_INTERNAL + "/"
      + DelegateMtlsApiConstants.API_PATH_CHECK_AVAILABILITY;
  String DELEGATE_DOWNLOAD_API = "delegate-download";

  //------------------------ Delegate Token -------------------------------------

  @POST(DELEGATE_TOKEN_NG_API)
  Call<RestResponse<DelegateTokenDetails>> createToken(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query("tokenName") @NotNull String tokenName);

  @PUT(DELEGATE_TOKEN_NG_API)
  Call<RestResponse<DelegateTokenDetails>> revokeToken(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query("tokenName") @NotNull String tokenName);

  @GET(DELEGATE_TOKEN_NG_API)
  Call<RestResponse<List<DelegateTokenDetails>>> getTokens(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query("status") DelegateTokenStatus status);

  @GET(DELEGATE_TOKEN_NG_API + "/delegate-groups")
  Call<RestResponse<DelegateGroupListing>> getDelegateGroupsUsingToken(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query("delegateTokenName") String delegateTokenName);

  @GET(DELEGATE_TOKEN_NG_API + "/delegate-token-value")
  Call<RestResponse<String>> getDelegateTokenValue(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query("delegateTokenName") String delegateTokenName);

  //------------------------ Delegate Group Tags, Deprecated Apis -----------------------------------

  @PUT(DELEGATE_GROUP_TAGS_API + "/tags")
  Call<RestResponse<DelegateGroup>> updateDelegateGroupTags_old(
      @Query(NGCommonEntityConstants.IDENTIFIER_KEY) @NotNull String groupIdentifier,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Body @NotNull DelegateGroupTags tags);

  //------------------------ Delegate Group Tags, New Apis -----------------------------------

  @GET(DELEGATE_GROUP_TAGS_API)
  Call<RestResponse<Optional<DelegateGroupDTO>>> getDelegateGroupTags(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(NGCommonEntityConstants.GROUP_IDENTIFIER_KEY) @NotNull String groupIdentifier);

  @POST(DELEGATE_GROUP_TAGS_API)
  Call<RestResponse<Optional<DelegateGroupDTO>>> addDelegateGroupTags(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(NGCommonEntityConstants.GROUP_IDENTIFIER_KEY) @NotNull String groupIdentifier,
      @Body @NotNull DelegateGroupTags tags);

  @PUT(DELEGATE_GROUP_TAGS_API)
  Call<RestResponse<Optional<DelegateGroupDTO>>> updateDelegateGroupTags(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(NGCommonEntityConstants.GROUP_IDENTIFIER_KEY) @NotNull String groupIdentifier,
      @Body @NotNull DelegateGroupTags tags);

  //------------------------ Delegate Group Upsert -----------------------------------

  @PUT(DELEGATE_TOKEN_NG_API + "/upsert")
  Call<RestResponse<DelegateGroup>> upsert(@Query(NGCommonEntityConstants.NAME_KEY) @NotNull String delegateName,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Body DelegateSetupDetails delegateSetupDetails);

  //------------------------ Delegate mTLS Endpoint Apis -----------------------------------

  @POST(DELEGATE_MTLS_ENDPOINT_API)
  Call<RestResponse<DelegateMtlsEndpointDetails>> createEndpointForAccount(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Body @NotNull DelegateMtlsEndpointRequest endpointRequest);

  @PUT(DELEGATE_MTLS_ENDPOINT_API)
  Call<RestResponse<DelegateMtlsEndpointDetails>> updateEndpointForAccount(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Body @NotNull DelegateMtlsEndpointRequest endpointRequest);

  @PATCH(DELEGATE_MTLS_ENDPOINT_API)
  Call<RestResponse<DelegateMtlsEndpointDetails>> patchEndpointForAccount(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Body @NotNull DelegateMtlsEndpointRequest patchRequest);

  @DELETE(DELEGATE_MTLS_ENDPOINT_API)
  Call<RestResponse<Boolean>> deleteEndpointForAccount(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier);

  @GET(DELEGATE_MTLS_ENDPOINT_API)
  Call<RestResponse<DelegateMtlsEndpointDetails>> getEndpointForAccount(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier);

  @GET(DELEGATE_MTLS_PREFIX_AVAILABLE_API)
  Call<RestResponse<Boolean>> isDomainPrefixAvailable(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(DelegateMtlsApiConstants.API_PARAM_DOMAIN_PREFIX_NAME) @NotNull String domainPrefix);

  //------------------------ Delegate Download Apis -----------------------------------

  @POST(DELEGATE_DOWNLOAD_API + "/kubernetes")
  Call<RestResponse<DelegateDownloadResponse>> downloadKubernetesDelegate(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body @NotNull DelegateDownloadRequest delegateDownloadRequest);

  @POST(DELEGATE_DOWNLOAD_API + "/docker")
  Call<RestResponse<DelegateDownloadResponse>> downloadDockerDelegate(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body @NotNull DelegateDownloadRequest delegateDownloadRequest);
}
