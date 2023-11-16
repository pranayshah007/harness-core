/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.creditcard.remote;

import io.harness.NGCommonEntityConstants;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.subscription.responses.AccountCreditCardValidationResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface CreditCardClient {
  String CREDIT_CARD_API = "credit-cards";

  @GET(CREDIT_CARD_API + "/has-valid-card")
  Call<ResponseDTO<AccountCreditCardValidationResponse>> validateCreditCard(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier);
}
