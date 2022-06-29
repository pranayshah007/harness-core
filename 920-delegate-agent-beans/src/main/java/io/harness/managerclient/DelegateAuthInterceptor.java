/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.managerclient;

import static io.harness.network.Localhost.getLocalHostName;

import io.harness.delegate.DelegateAgentCommonVariables;
import io.harness.security.TokenGenerator;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class DelegateAuthInterceptor implements Interceptor {
  private static final String HOST_NAME = getLocalHostName();

  private TokenGenerator tokenGenerator;

  public DelegateAuthInterceptor(TokenGenerator tokenGenerator) {
    this.tokenGenerator = tokenGenerator;
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    String scheme = chain.request().url().scheme();
    String host = chain.request().url().host();
    int port = chain.request().url().port();

    String token = tokenGenerator.getToken(scheme, host, port, HOST_NAME);

    Request request = chain.request();
    return chain.proceed(request.newBuilder()
                             .header("Authorization", "Delegate " + token)
                             .addHeader("delegateId", DelegateAgentCommonVariables.getDelegateId())
                             .addHeader("delegateTokenName", DelegateAgentCommonVariables.getDelegateTokenName())
                             .build());
  }
}
