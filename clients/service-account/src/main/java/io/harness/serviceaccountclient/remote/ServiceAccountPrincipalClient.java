/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serviceaccountclient.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.IDENTIFIER_KEY;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.serviceaccount.ServiceAccountDTO;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(PL)
public interface ServiceAccountPrincipalClient {
  String BASE_SERVICE_PRINCIPAL_URL = "serviceaccount";

  @GET(BASE_SERVICE_PRINCIPAL_URL + "/{identifier}")
  Call<ResponseDTO<ServiceAccountDTO>> getAggregatedServiceAccount(
      @Path(value = IDENTIFIER_KEY) String identifier, @Query(value = ACCOUNT_KEY) String accountIdentifier);
}
