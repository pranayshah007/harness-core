/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dms.client;

import io.harness.NGCommonEntityConstants;
import io.harness.rest.RestResponse;

import javax.validation.constraints.NotNull;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface DelegateSecretManagerClient {
  String SECRET_MANAGER_ENDPOINT = "secret-manager";

  //------------------------ Agent mTLS Endpoint Apis -----------------------------------

  @GET(SECRET_MANAGER_ENDPOINT + "/fetchSecret")
  Call<RestResponse<String>> fetchSecretValue(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query("secretRecordId") @NotNull String secretRecordId);
}
