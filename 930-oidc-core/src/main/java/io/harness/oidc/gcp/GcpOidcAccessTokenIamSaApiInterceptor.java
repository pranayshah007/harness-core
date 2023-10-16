/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.gcp;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

public class GcpOidcAccessTokenIamSaApiInterceptor implements Interceptor {
  String workloadAccessToken;

  public GcpOidcAccessTokenIamSaApiInterceptor(String workloadAccessToken) {
    this.workloadAccessToken = workloadAccessToken;
  }

  @NotNull
  @Override
  public Response intercept(@NotNull Chain chain) throws IOException {
    Request original = chain.request();
    Request request = original.newBuilder()
                          .header("Authorization", this.workloadAccessToken)
                          .method(original.method(), original.body())
                          .build();
    return chain.proceed(request);
  }
}
