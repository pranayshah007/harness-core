/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.security;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.time.Duration;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class ErrorTrackingAuthInterceptor implements Interceptor {
  private static final int DURATION = 4;
  private final ServiceTokenGenerator tokenGenerator;
  private final String serviceSecret;

  public ErrorTrackingAuthInterceptor(String serviceSecret, ServiceTokenGenerator tokenGenerator) {
    this.tokenGenerator = tokenGenerator;
    this.serviceSecret = serviceSecret;
  }

  @Override
  public Response intercept(Interceptor.Chain chain) throws IOException {
    Request request = chain.request();
    return chain.proceed(
        request.newBuilder().header("Authorization", "ErrorTracking " + getErrorTrackingServiceToken()).build());
  }

  private String getErrorTrackingServiceToken() {
    Preconditions.checkState(isNotEmpty(serviceSecret), "error tracking service secret empty");
    return tokenGenerator.getServiceTokenWithDuration(serviceSecret, Duration.ofHours(DURATION));
  }
}
