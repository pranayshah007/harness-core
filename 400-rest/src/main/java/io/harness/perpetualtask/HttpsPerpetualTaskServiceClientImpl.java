/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static java.lang.String.format;

import io.harness.beans.DelegateTask;
import io.harness.callback.HttpsClientEntrypoint;
import io.harness.network.Http;
import io.harness.perpetualtask.https.HttpsPerpetualTaskParams;

import com.google.api.client.util.Base64;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.FieldMap;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;

@Slf4j
public class HttpsPerpetualTaskServiceClientImpl implements HttpsPerpetualTaskServiceClient {
  private HttpsClient httpsClient;

  public HttpsPerpetualTaskServiceClientImpl(HttpsClientEntrypoint entryPoint) {
    this.httpsClient = buildHttpsClient(entryPoint);
  }

  @Override
  public Message getTaskParams(PerpetualTaskClientContext clientContext, boolean referenceFalse) {
    if (clientContext == null) {
      return null;
    }

    try {
      if (clientContext.getClientParams() != null) {
        Response<ResponseBody> response = httpsClient.getTaskParams(clientContext.getClientParams()).execute();
        if (response != null && response.body() != null) {
          return HttpsPerpetualTaskParams.newBuilder()
              .setTaskParams(ByteString.copyFrom(response.body().bytes()))
              .build();
        }
      }

      if (clientContext.getExecutionBundle() != null) {
        PerpetualTaskExecutionBundle perpetualTaskExecutionBundle =
            PerpetualTaskExecutionBundle.parseFrom(clientContext.getExecutionBundle());
        return perpetualTaskExecutionBundle.getTaskParams();
      }

    } catch (IOException ex) {
      log.error("Failed to fetch task params.", ex);
    }

    return null;
  }

  @Override
  public DelegateTask getValidationTask(PerpetualTaskClientContext clientContext, String accountId) {
    throw new UnsupportedOperationException();
  }

  public interface HttpsClient {
    @FormUrlEncoded @POST("/") Call<ResponseBody> getTaskParams(@FieldMap Map<String, String> body);
  }

  private HttpsClient buildHttpsClient(HttpsClientEntrypoint entrypoint) {
    OkHttpClient client =
        Http.getUnsafeOkHttpClientBuilder(entrypoint.getUrl(), 15, 15)
            .connectionPool(new ConnectionPool())
            .retryOnConnectionFailure(true)
            .addInterceptor(chain -> {
              Request.Builder request = chain.request().newBuilder().addHeader("Authorization",
                  "Basic "
                      + Base64.encodeBase64String(format("%s:%s", entrypoint.getBasicAuthCredentials().getUsername(),
                          entrypoint.getBasicAuthCredentials().getPassword())
                                                      .getBytes(StandardCharsets.UTF_8)));
              return chain.proceed(request.build());
            })
            .build();

    final Retrofit retrofit = new Retrofit.Builder()
                                  .baseUrl(entrypoint.getUrl())
                                  .addConverterFactory(JacksonConverterFactory.create())
                                  .client(client)
                                  .build();
    return retrofit.create(HttpsClient.class);
  }
}
