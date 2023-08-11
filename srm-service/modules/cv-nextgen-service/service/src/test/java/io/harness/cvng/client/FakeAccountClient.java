/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.client;

import io.harness.account.AccountClient;
import io.harness.beans.FeatureFlag;
import io.harness.beans.PageResponse;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.user.UserMetadata;
import io.harness.rest.RestResponse;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FakeAccountClient implements AccountClient {
  private static final String EXCEPTION_MESSAGE = "mocked method - provide impl when required";

  @Override
  public Call<RestResponse<AccountDTO>> create(AccountDTO dto) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<AccountDTO>> getAccountDTO(String accountId) {
    return new Call<RestResponse<AccountDTO>>() {
      @Override
      public Response<RestResponse<AccountDTO>> execute() throws IOException {
        return Response.success(new RestResponse(AccountDTO.builder().build()));
      }

      @Override
      public void enqueue(Callback<RestResponse<AccountDTO>> callback) {}

      @Override
      public boolean isExecuted() {
        return false;
      }

      @Override
      public void cancel() {}

      @Override
      public boolean isCanceled() {
        return false;
      }

      @Override
      public Call<RestResponse<AccountDTO>> clone() {
        return null;
      }

      @Override
      public Request request() {
        return null;
      }
    };
  }

  @Override
  public Call<RestResponse<List<AccountDTO>>> getAccountDTOs(List<String> accountIds) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<AccountDTO>> updateAccountName(String accountId, AccountDTO dto) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<Integer>> getAccountTrustLevel(String accountId) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<Boolean>> updateAccountTrustLevel(String accountId, Integer trustLevel) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<Boolean>> isFeatureFlagEnabled(String featureName, String accountId) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<Set<String>>> featureFlagEnabledAccounts(String featureName) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<Boolean>> isNextGenEnabled(String accountId) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<String>> getBaseUrl(String accountId) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<String>> getGatewayBaseUrl(String accountId) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<List<String>>> getAccountAdmins(String accountId) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<Boolean>> doesAccountExist(String accountName) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<Boolean>> updateDefaultExperienceIfApplicable(
      String accountId, DefaultExperience defaultExperience) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<AccountDTO>> updateDefaultExperience(String accountId, AccountDTO dto) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<AccountDTO>> updateCrossGenerationAccessEnabled(String accountId, AccountDTO dto) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<Collection<FeatureFlag>>> listAllFeatureFlagsForAccount(String accountId) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<List<UserMetadata>>> listAllHarnessSupportUsers() {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<Boolean>> checkIfHarnessSupportEnabledForAccount(String accountId) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<Boolean>> isHarnessSupportUserId(String userId) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<Boolean>> checkAutoInviteAcceptanceEnabledForAccount(String accountId) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<Boolean>> checkPLNoEmailForSamlAccountInvitesEnabledForAccount(String accountId) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<Boolean>> isSSOEnabled(String accountId) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<Void>> upsertDefaultToken(
      String accountId, String orgId, String projectId, Boolean skipIfExists) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<List<String>>> getOrgsWithActiveDefaultDelegateToken(String accountIdentifier) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<List<String>>> getProjectsWithActiveDefaultDelegateToken(String accountIdentifier) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<String>> getVanityUrl(String accountIdentifier) {
    return new Call<RestResponse<String>>() {
      @Override
      public Response<RestResponse<String>> execute() throws IOException {
        return Response.success(new RestResponse());
      }

      @Override
      public void enqueue(Callback<RestResponse<String>> callback) {}

      @Override
      public boolean isExecuted() {
        return false;
      }

      @Override
      public void cancel() {}

      @Override
      public boolean isCanceled() {
        return false;
      }

      @Override
      public Call<RestResponse<String>> clone() {
        return null;
      }

      @Override
      public Request request() {
        return null;
      }
    };
  }

  @Override
  public Call<RestResponse<Boolean>> isImmutableDelegateEnabled(String accountId) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }

  @Override
  public Call<RestResponse<PageResponse<AccountDTO>>> listAccounts(int offset, int pageSize) {
    throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
  }
}
