/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.gcp;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface GcpOidcAccessTokenIamSaAPI {
  @POST
  @Headers("Content-Type: application/json")
  Call<GcpOidcServiceAccountAccessTokenResponse> exchangeServiceAccountAccessToken(
      @Body GcpOidcServiceAccountAccessTokenRequest request);
}
