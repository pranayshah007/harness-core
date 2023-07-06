/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.utility;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class LoggingInterceptor implements Interceptor {
  @NotNull
  @Override
  public Response intercept(@NotNull Chain chain) throws IOException {
    Request request = chain.request();

    long t1 = System.nanoTime();

    if (request != null && request.url() != null && chain != null && chain.connection() != null) {
      log.info(String.format("AzureClientSDK - Sending request %s on %s", request.url(), chain.connection()));
    }

    Response response = chain.proceed(request);

    long t2 = System.nanoTime();

    if (response != null && response.request() != null && response.request().url() != null) {
      log.info(String.format(
          "AzureClientSDK - Received response for %s in %.1fms", response.request().url(), (t2 - t1) / 1e6d));
    }

    return response;
  }
}
