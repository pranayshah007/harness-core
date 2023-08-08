/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.servicenow.auth.refreshtoken;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenConstants.CLIENT_ID;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenConstants.CLIENT_SECRET;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenConstants.GRANT_TYPE;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenConstants.REFRESH_TOKEN;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenConstants.SCOPE;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenConstants.SERVICENOW_TOKEN_URL_SUFFIX;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Url;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
public interface RefreshTokenRestClient {
  @FormUrlEncoded
  @Headers({"Content-Type: application/x-www-form-urlencoded; charset=utf-8", "Accept: application/json"})
  @POST()
  Call<AccessTokenResponse> getAccessToken(@Field(GRANT_TYPE) String grantType, @Field(CLIENT_ID) String clientId,
      @Field(CLIENT_SECRET) String clientSecret, @Field(REFRESH_TOKEN) String refreshToken, @Field(SCOPE) String scope,
      @Url String url);

  @FormUrlEncoded
  @Headers({"Content-Type: application/x-www-form-urlencoded; charset=utf-8", "Accept: application/json"})
  @POST(SERVICENOW_TOKEN_URL_SUFFIX)
  Call<AccessTokenResponse> getAccessTokenFromServiceNow(@Field(GRANT_TYPE) String grantType,
      @Field(CLIENT_ID) String clientId, @Field(CLIENT_SECRET) String clientSecret,
      @Field(REFRESH_TOKEN) String refreshToken, @Field(SCOPE) String scope);
}
