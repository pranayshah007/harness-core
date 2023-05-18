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
import io.harness.delegate.beans.DelegateListResponse;
import io.harness.delegate.beans.DelegateSetupDetails;
import io.harness.delegate.beans.DelegateTokenDetails;
import io.harness.delegate.beans.DelegateTokenStatus;
import io.harness.delegate.beans.SupportedDelegateVersion;
import io.harness.delegate.filter.DelegateFilterPropertiesDTO;
import io.harness.delegate.utilities.DelegateGroupDeleteResponse;
import io.harness.rest.RestResponse;

import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Query;

// this client is used to communicate between ng manager and cg manager for all delegate related stuff
public interface DelegateNgManagerCgManagerClient {
  String DELEGATE_TOKEN_NG_API = "delegate-token/ng";
  String DELEGATE_GROUP_TAGS_API = "delegate-group-tags-internal";
  String DELEGATE_SETUP_NG_API = "delegate-setup/internal";
  String DELEGATE_DOWNLOAD_API = "delegate-download";

  String DELEGATE_VERSION_OVERRIDE_API = "version-override/internal";

  //------------------------ Delegate Token -------------------------------------

  @POST(DELEGATE_TOKEN_NG_API)
  Call<RestResponse<DelegateTokenDetails>> createToken(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query("tokenName") @NotNull String tokenName, @Query("revokeAfter") Long revokeAfter);

  @PUT(DELEGATE_TOKEN_NG_API)
  Call<RestResponse<DelegateTokenDetails>> revokeToken(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query("tokenName") @NotNull String tokenName);

  @GET(DELEGATE_TOKEN_NG_API)
  Call<RestResponse<List<DelegateTokenDetails>>> getTokens(@Query("tokenName") String tokenName,
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

  @POST(DELEGATE_GROUP_TAGS_API + "/delegate-groups")
  Call<RestResponse<List<DelegateGroupDTO>>> listDelegateGroupHavingTags(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Body @NotNull DelegateGroupTags tags);

  //------------------------ Delegate Group Upsert -----------------------------------

  @PUT(DELEGATE_TOKEN_NG_API + "/upsert")
  Call<RestResponse<DelegateGroup>> upsert(@Query(NGCommonEntityConstants.NAME_KEY) @NotNull String delegateName,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Body DelegateSetupDetails delegateSetupDetails);

  //------------------------ NG Delegate Setup Apis -----------------------------------

  @POST(DELEGATE_SETUP_NG_API + "/list")
  Call<RestResponse<List<DelegateListResponse>>> getDelegates(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body DelegateFilterPropertiesDTO delegateFilterPropertiesDTO);

  @POST(DELEGATE_SETUP_NG_API + "/delegate-helm-values-yaml")
  Call<RestResponse<String>> generateHelmValuesFile(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body @NotNull DelegateSetupDetails delegateSetupDetails);

  @GET(DELEGATE_SETUP_NG_API + "/delegate-terraform-module-file")
  Call<RestResponse<String>> getTerraformModuleFile(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier);

  @DELETE(DELEGATE_SETUP_NG_API + "/delegate")
  Call<RestResponse<DelegateGroupDeleteResponse>> deleteDelegateGroup(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(NGCommonEntityConstants.DELEGATE_IDENTIFIER_KEY) @NotNull String delegateGroupIdentifier);

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

  //------------------------ Version Override API -----------------------------------
  @PUT(DELEGATE_VERSION_OVERRIDE_API + "/delegate-tag")
  Call<RestResponse<String>> overrideDelegateImage(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query("delegateTag") @NotNull String delegateTag, @Query("validTillNextRelease") Boolean validTillNextRelease,
      @Query("validForDays") int validForDays);

  @GET("version/supportedDelegate")
  Call<RestResponse<SupportedDelegateVersion>> getPublishedDelegateVersion(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier);
}
