/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.credit.remote.admin;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.beans.credits.CreditDTO;
import io.harness.ng.core.dto.ResponseDTO;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(GTM)
public interface AdminCreditHttpClient {
  String ADMIN_CREDIT_API = "admin/credits";

  @POST(ADMIN_CREDIT_API + "/create")
  Call<ResponseDTO<CreditDTO>> createAccountCredit(
      @Query("accountIdentifier") String accountIdentifier, @Body CreditDTO creditDTO);

  @GET(ADMIN_CREDIT_API + "/{accountIdentifier}")
  Call<ResponseDTO<List<CreditDTO>>> getAccountCredit(@Path("accountIdentifier") String accountIdentifier);
}
