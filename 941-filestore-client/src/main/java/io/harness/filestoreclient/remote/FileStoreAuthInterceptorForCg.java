/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestoreclient.remote;

import io.harness.security.ServiceTokenGenerator;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class FileStoreAuthInterceptorForCg implements Interceptor {
  private String serviceSecret;
  private ServiceTokenGenerator serviceTokenGenerator;

  public FileStoreAuthInterceptorForCg(String serviceSecret, ServiceTokenGenerator serviceTokenGenerator) {
    this.serviceSecret = serviceSecret;
    this.serviceTokenGenerator = serviceTokenGenerator;
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    String token = serviceTokenGenerator.getServiceToken(serviceSecret);
    Request request = chain.request();
    return chain.proceed(request.newBuilder().header("Authorization", "Manager " + token).build());
  }
}
