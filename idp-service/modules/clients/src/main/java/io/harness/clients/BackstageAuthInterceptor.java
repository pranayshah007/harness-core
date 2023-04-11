/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.clients;

import io.harness.beans.DecryptedSecretValue;
import io.harness.idp.common.Constants;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

public class BackstageAuthInterceptor implements Interceptor {
  @Inject @Named("PRIVILEGED") private SecretManagerClientService ngSecretService;
  private static final int EXPIRATION_TIME = 3600;
  @NotNull
  @Override
  public Response intercept(@NotNull Chain chain) throws IOException {
    Request request = chain.request();
    URL url = new URL(chain.request().url().toString());
    String path = url.getPath();
    String accountIdentifier = path.split("/")[1];
    String token = getBackstageBackendSecret(accountIdentifier);
    return chain.proceed(request.newBuilder().header("Authorization", "Bearer " + token).build());
  }
  private String getBackstageBackendSecret(String harnessAccount) {
    DecryptedSecretValue decryptedValue =
        ngSecretService.getDecryptedSecretValue(harnessAccount, null, null, Constants.IDP_BACKEND_SECRET);
    byte[] decodedSecret = Base64.getDecoder().decode(String.valueOf(decryptedValue));
    return generateToken(new String(decodedSecret));
  }

  private String generateToken(String secretKey) {
    String subject = "backstage-server";
    long expirationTime = Instant.now().plusSeconds(EXPIRATION_TIME).getEpochSecond();

    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", subject);
    claims.put("exp", expirationTime);

    Map<String, Object> headers = new HashMap<>();
    headers.put("alg", SignatureAlgorithm.HS256.getValue());
    headers.put("typ", Header.JWT_TYPE);

    JwtBuilder builder = Jwts.builder().setHeaderParams(headers).setClaims(claims).signWith(
        SignatureAlgorithm.HS256, secretKey.getBytes());
    return builder.compact();
  }
}