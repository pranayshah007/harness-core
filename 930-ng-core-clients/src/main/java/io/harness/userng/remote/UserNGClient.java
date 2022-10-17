/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.userng.remote;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.PL)
public interface UserNGClient {
  String IS_EMAIL_IN_ACCOUNT_API = "user/is-email-in-account";
  @GET(IS_EMAIL_IN_ACCOUNT_API)
  Call<ResponseDTO<Boolean>> isEmailIdInAccount(@Query(value = "emailIdentifier") String emailIdentifier,
      @Query(value = "accountIdentifier") String accountIdentifier);
}
