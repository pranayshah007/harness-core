/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.code;

import static io.harness.annotations.dev.HarnessTeam.CODE;
import static io.harness.security.JWTAuthenticationFilter.X_SOURCE_PRINCIPAL;

import static org.apache.http.HttpHeaders.AUTHORIZATION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.AbstractHttpClientFactory;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.JWTTokenServiceUtils;
import io.harness.security.ServiceTokenGenerator;
import io.harness.security.dto.PrincipalType;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Singleton
@Slf4j
@OwnedBy(CODE)
public class CodeResourceHttpClientFactory extends AbstractHttpClientFactory implements Provider<CodeResourceClient> {
  public CodeResourceHttpClientFactory(ServiceHttpClientConfig httpClientConfig, String serviceSecret,
      ServiceTokenGenerator tokenGenerator, KryoConverterFactory kryoConverterFactory, String clientId,
      ClientMode clientMode) {
    super(
        httpClientConfig, serviceSecret, tokenGenerator, kryoConverterFactory, clientId, false, ClientMode.PRIVILEGED);
  }

  @Override
  public CodeResourceClient get() {
    return getRetrofit().create(CodeResourceClient.class);
  }

  @Override
  protected Interceptor getAuthorizationInterceptor(ClientMode clientMode) {
    return chain -> {
      Request.Builder builder = chain.request().newBuilder();

      ImmutableMap<String, String> claims = ImmutableMap.of("name", clientId, "type", PrincipalType.SERVICE.name());
      final long TEN_HOURS_IN_MS = TimeUnit.MILLISECONDS.convert(10, TimeUnit.HOURS);

      String token = JWTTokenServiceUtils.generateJWTToken(claims, TEN_HOURS_IN_MS, getServiceSecret());

      builder.header(X_SOURCE_PRINCIPAL, clientId + StringUtils.SPACE + token);
      builder.header(AUTHORIZATION, clientId + StringUtils.SPACE + token);
      return chain.proceed(builder.build());
    };
  }
}
