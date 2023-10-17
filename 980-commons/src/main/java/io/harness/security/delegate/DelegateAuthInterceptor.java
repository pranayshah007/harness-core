/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security.delegate;

import static io.harness.network.Localhost.getLocalHostName;

import io.harness.delegate.DelegateAgentCommonVariables;
import io.harness.security.TokenGenerator;

import java.io.IOException;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

@RequiredArgsConstructor
public class DelegateAuthInterceptor implements Interceptor {
  private static final String HOST_NAME = getLocalHostName();

  private final TokenGenerator tokenGenerator;
  private final Supplier<String> delegateIdSupplier;

  public DelegateAuthInterceptor(final TokenGenerator tokenGenerator) {
    this(tokenGenerator, DelegateAgentCommonVariables::getDelegateId);
  }

  @Override
  @NonNull
  public Response intercept(Chain chain) throws IOException {
    final String scheme = chain.request().url().scheme();
    final String host = chain.request().url().host();
    final int port = chain.request().url().port();

    final String token = tokenGenerator.getToken(scheme, host, port, HOST_NAME);

    final Request request = chain.request();
    return chain.proceed(request.newBuilder()
                             .header("Authorization", "Delegate " + token)
                             .addHeader("accountId", tokenGenerator.getAccountId())
                             .addHeader("delegateId", delegateIdSupplier.get())
                             .addHeader("delegateTokenName", DelegateAgentCommonVariables.getDelegateTokenName())
                             .build());
  }
}
