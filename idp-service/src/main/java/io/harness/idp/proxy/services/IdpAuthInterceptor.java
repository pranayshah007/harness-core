/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.services;

import static io.harness.authorization.AuthorizationServiceHeader.IDP_SERVICE;

import io.harness.security.SecurityContextBuilder;
import io.harness.security.ServiceTokenGenerator;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

public class IdpAuthInterceptor implements Interceptor {
  public static final String X_SOURCE_PRINCIPAL = "X-Source-Principal";
  public static final String AUTHORIZATION = "Authorization";
  private static final String SPACE = " ";
  private final ServiceTokenGenerator tokenGenerator;
  private final String secret;

  @Inject
  public IdpAuthInterceptor(ServiceTokenGenerator tokenGenerator, @Named("idpServiceSecret") String secret) {
    this.tokenGenerator = tokenGenerator;
    this.secret = secret;
  }

  @NotNull
  @Override
  public Response intercept(@NotNull Chain chain) throws IOException {
    Map<String, String> authHeaders = getAuthHeaders();
    Headers headers = Headers.of(authHeaders);
    Request request = chain.request();
    return chain.proceed(request.newBuilder().headers(headers).build());
  }

  public Map<String, String> getAuthHeaders() {
    SecurityContextBuilder.setContext(new ServicePrincipal(IDP_SERVICE.getServiceId()));
    String authorizationToken =
        tokenGenerator.getServiceTokenWithDuration(secret, Duration.ofHours(4), SecurityContextBuilder.getPrincipal());
    Principal sourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal() != null
        ? SourcePrincipalContextBuilder.getSourcePrincipal()
        : SecurityContextBuilder.getPrincipal();
    String sourcePrincipalToken =
        tokenGenerator.getServiceTokenWithDuration(secret, Duration.ofHours(4), sourcePrincipal);
    return Map.of(AUTHORIZATION, IDP_SERVICE.getServiceId() + SPACE + authorizationToken, X_SOURCE_PRINCIPAL,
        IDP_SERVICE.getServiceId() + SPACE + sourcePrincipalToken);
  }
}
