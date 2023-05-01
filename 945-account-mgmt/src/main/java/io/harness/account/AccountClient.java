/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.account;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureFlag;
import io.harness.beans.PageResponse;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.user.UserMetadata;
import io.harness.rest.RestResponse;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(PL)
public interface AccountClient {
  String ACCOUNT_API = "ng/accounts";
  String FEATURE_FLAG_CHECK_API = "ng/accounts/feature-flag-enabled";

  String IMMUTABLE_DELEGATE_ENABLED = "ng/accounts/immutable-delegate-enabled";

  String FEATURE_FLAG_ENABLED_ACCOUNTS_API = "ng/accounts/feature-flag-enabled-accounts";
  String ACCOUNT_BASEURL_API = "ng/accounts/baseUrl";
  String ACCOUNT_VANITYURL_API = "ng/accounts/vanityUrl";
  String ACCOUNT_GATEWAYBASEURL_API = "ng/accounts/gatewayBaseUrl";
  String ACCOUNT_EXISTS = "ng/accounts/exists";
  String ACCOUNT_ADMIN_API = ACCOUNT_API + "/account-admins";
  String FEATURE_FLAG_LIST_API = "ng/user/feature-flags/{accountId}";
  String HARNESS_USER_GROUP_API = "harnessUserGroup";
  String NG_DELEGATE_TOKEN_API = "v2/delegate-token-internal";
  String UPSERT_DEFAULT = "/default";
  String DEFAULT_ORG_TOKENS = "/default-for-orgs";
  String DEFAULT_PROJECT_TOKENS = "/default-for-projects";

  @POST(ACCOUNT_API) Call<RestResponse<AccountDTO>> create(@Body AccountDTO dto);

  @GET(ACCOUNT_API + "/{accountId}") Call<RestResponse<AccountDTO>> getAccountDTO(@Path("accountId") String accountId);

  @GET(ACCOUNT_API) Call<RestResponse<List<AccountDTO>>> getAccountDTOs(@Query("accountIds") List<String> accountIds);

  @PUT(ACCOUNT_API + "/{accountId}/name")
  Call<RestResponse<AccountDTO>> updateAccountName(@Path("accountId") String accountId, @Body AccountDTO dto);

  @GET(ACCOUNT_API + "/trustLevel")
  Call<RestResponse<Integer>> getAccountTrustLevel(@Query("accountId") String accountId);

  @GET(ACCOUNT_API + "/update-trust-level")
  Call<RestResponse<Boolean>> updateAccountTrustLevel(
      @Query("accountId") String accountId, @Query("trustLevel") Integer trustLevel);

  @GET(FEATURE_FLAG_CHECK_API)
  Call<RestResponse<Boolean>> isFeatureFlagEnabled(
      @Query("featureName") String featureName, @Query("accountId") String accountId);

  @GET(FEATURE_FLAG_ENABLED_ACCOUNTS_API)
  Call<RestResponse<Set<String>>> featureFlagEnabledAccounts(@Query("featureName") String featureName);

  @GET(ACCOUNT_API + "/{accountId}/nextgen-enabled")
  Call<RestResponse<Boolean>> isNextGenEnabled(@Path("accountId") String accountId);

  @GET(ACCOUNT_BASEURL_API) Call<RestResponse<String>> getBaseUrl(@Query("accountId") String accountId);

  @GET(ACCOUNT_GATEWAYBASEURL_API) Call<RestResponse<String>> getGatewayBaseUrl(@Query("accountId") String accountId);

  @GET(ACCOUNT_ADMIN_API) Call<RestResponse<List<String>>> getAccountAdmins(@Query("accountId") String accountId);

  @GET(ACCOUNT_EXISTS + "/{accountName}")
  Call<RestResponse<Boolean>> doesAccountExist(@Path("accountName") String accountName);

  @PUT(ACCOUNT_API + "/{accountId}/default-experience-if-applicable")
  Call<RestResponse<Boolean>> updateDefaultExperienceIfApplicable(
      @Path("accountId") String accountId, @Query("defaultExperience") DefaultExperience defaultExperience);

  @PUT(ACCOUNT_API + "/{accountId}/default-experience")
  Call<RestResponse<AccountDTO>> updateDefaultExperience(@Path("accountId") String accountId, @Body AccountDTO dto);

  @PUT(ACCOUNT_API + "/{accountId}/cross-generation-access")
  Call<RestResponse<AccountDTO>> updateCrossGenerationAccessEnabled(
      @Path("accountId") String accountId, @Body AccountDTO dto);

  @GET(FEATURE_FLAG_LIST_API)
  Call<RestResponse<Collection<FeatureFlag>>> listAllFeatureFlagsForAccount(@Path("accountId") String accountId);

  @GET(HARNESS_USER_GROUP_API + "/supportUsers") Call<RestResponse<List<UserMetadata>>> listAllHarnessSupportUsers();

  @GET(HARNESS_USER_GROUP_API + "/supportEnabledStatus")
  Call<RestResponse<Boolean>> checkIfHarnessSupportEnabledForAccount(@Query("accountId") String accountId);

  @GET(ACCOUNT_API + "/is-auto-invite-acceptance-enabled")
  Call<RestResponse<Boolean>> checkAutoInviteAcceptanceEnabledForAccount(@Query("accountId") String accountId);

  @GET(ACCOUNT_API + "/is-pl-no-email-invite-acceptance-enabled")
  Call<RestResponse<Boolean>> checkPLNoEmailForSamlAccountInvitesEnabledForAccount(
      @Query("accountId") String accountId);

  @GET(ACCOUNT_API + "/is-sso-enabled") Call<RestResponse<Boolean>> isSSOEnabled(@Query("accountId") String accountId);

  @PUT(NG_DELEGATE_TOKEN_API + UPSERT_DEFAULT)
  Call<RestResponse<Void>> upsertDefaultToken(@Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgId,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectId, @Query("skipIfExists") Boolean skipIfExists);

  @GET(NG_DELEGATE_TOKEN_API + DEFAULT_ORG_TOKENS)
  Call<RestResponse<List<String>>> getOrgsWithActiveDefaultDelegateToken(
      @Query("accountIdentifier") String accountIdentifier);

  @GET(NG_DELEGATE_TOKEN_API + DEFAULT_PROJECT_TOKENS)
  Call<RestResponse<List<String>>> getProjectsWithActiveDefaultDelegateToken(
      @Query("accountIdentifier") String accountIdentifier);

  @GET(ACCOUNT_VANITYURL_API) Call<RestResponse<String>> getVanityUrl(@Query("accountId") String accountIdentifier);

  @GET(IMMUTABLE_DELEGATE_ENABLED)
  Call<RestResponse<Boolean>> isImmutableDelegateEnabled(@Query("accountId") String accountId);

  @GET(ACCOUNT_API + "/listV2")
  Call<RestResponse<PageResponse<AccountDTO>>> listAccounts(
      @Query("offset") int offset, @Query("pageSize") int pageSize);
}
