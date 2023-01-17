/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.adfs;

import static io.harness.adfs.AdfsConstants.ADFS_ACCESS_TOKEN_ENDPOINT;
import static io.harness.adfs.AdfsConstants.CLIENT_ASSERTION;
import static io.harness.adfs.AdfsConstants.CLIENT_ASSERTION_TYPE;
import static io.harness.adfs.AdfsConstants.CLIENT_ID;
import static io.harness.adfs.AdfsConstants.GRANT_TYPE;
import static io.harness.adfs.AdfsConstants.RESOURCE_ID;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface AdfsRestClient {
  @FormUrlEncoded
  @Headers({"Content-Type: application/x-www-form-urlencoded; charset=utf-8", "accept-language: en-US"})
  @POST(ADFS_ACCESS_TOKEN_ENDPOINT)
  Call<AdfsAccessTokenResponse> getAccessToken(@Field(CLIENT_ID) String clientId,
      @Field(CLIENT_ASSERTION_TYPE) String clientAssertionType, @Field(CLIENT_ASSERTION) String jwtToken,
      @Field(GRANT_TYPE) String grantType, @Field(RESOURCE_ID) String resourceId);
}