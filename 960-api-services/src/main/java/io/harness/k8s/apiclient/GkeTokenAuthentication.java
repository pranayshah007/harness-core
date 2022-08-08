/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.apiclient;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.credentials.Authentication;
import java.io.IOException;
import java.util.function.Supplier;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@OwnedBy(HarnessTeam.CDP)
public class GkeTokenAuthentication implements Authentication, Interceptor {
  private final Supplier<String> tokenAuthentication;

  public GkeTokenAuthentication(Supplier<String> tokenAuthentication) {
    this.tokenAuthentication = tokenAuthentication;
  }

  @Override
  public void provide(ApiClient client) {
    OkHttpClient withInterceptor = client.getHttpClient().newBuilder().addInterceptor(this).build();
    client.setHttpClient(withInterceptor);
  }

  @Override
  public Response intercept(Interceptor.Chain chain) throws IOException {
    Request request = chain.request();
    Request newRequest = request.newBuilder().header("Authorization", "Bearer " + tokenAuthentication.get()).build();
    return chain.proceed(newRequest);
  }
}
