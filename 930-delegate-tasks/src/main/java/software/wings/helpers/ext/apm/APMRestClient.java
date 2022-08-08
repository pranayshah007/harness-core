/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.apm;

import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.HeaderMap;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.QueryMap;
import retrofit2.http.Url;

public interface APMRestClient {
  @GET
  Call<Object> validate(@Url String url, @HeaderMap Map<String, String> headers, @QueryMap Map<String, String> options);

  @POST
  Call<Object> validatePost(@Url String url, @HeaderMap Map<String, String> headers,
      @QueryMap Map<String, String> options, @Body Map<String, Object> body);

  @FormUrlEncoded
  @POST
  Call<Object> getAzureBearerToken(@Url String url, @HeaderMap Map<String, String> headers,
      @Field("grant_type") String grantType, @Field("client_id") String clientId, @Field("resource") String resource,
      @Field("client_secret") String clientSecret);

  @GET
  Call<Object> collect(@Url String url, @HeaderMap Map<String, Object> headers, @QueryMap Map<String, Object> options);

  @PUT
  Call<Object> putCollect(@Url String url, @HeaderMap Map<String, Object> headers,
      @QueryMap Map<String, Object> options, @Body String body);

  @POST
  Call<Object> postCollect(@Url String url, @HeaderMap Map<String, Object> headers,
      @QueryMap Map<String, Object> options, @Body Map<String, Object> body);
}
