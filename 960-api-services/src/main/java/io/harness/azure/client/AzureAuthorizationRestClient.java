/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.client;

import io.harness.azure.model.AzureConstants;

import software.wings.helpers.ext.azure.AzureIdentityAccessTokenResponse;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface AzureAuthorizationRestClient {
  @FormUrlEncoded
  @Headers({"Content-Type: application/x-www-form-urlencoded; charset=utf-8", "accept-language: en-US"})
  @POST("/{" + AzureConstants.TENANT_ID + "}/oauth2/v2.0/token")
  Call<AzureIdentityAccessTokenResponse> servicePrincipalAccessToken(
      @Path(value = AzureConstants.TENANT_ID) String tenantId, @Field(AzureConstants.GRANT_TYPE) String grantType,
      @Field(AzureConstants.CLIENT_ID) String clientId, @Field(AzureConstants.SCOPE) String scope,
      @Field(AzureConstants.CLIENT_SECRET) String clientSecret);

  @FormUrlEncoded
  @Headers({"Content-Type: application/x-www-form-urlencoded; charset=utf-8", "accept-language: en-US"})
  @POST("/{" + AzureConstants.TENANT_ID + "}/oauth2/v2.0/token")
  Call<AzureIdentityAccessTokenResponse> servicePrincipalAccessToken(
      @Path(value = AzureConstants.TENANT_ID) String tenantId, @Field(AzureConstants.GRANT_TYPE) String grantType,
      @Field(AzureConstants.CLIENT_ID) String clientId, @Field(AzureConstants.SCOPE) String scope,
      @Field(AzureConstants.CLIENT_ASSERTION_TYPE) String clientAssertionType,
      @Field(AzureConstants.CLIENT_ASSERTION) String clientAssertion);
}
