/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.security.dto.Principal;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.google.common.base.Preconditions;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by rsingh on 9/17/18.
 */
@OwnedBy(PL)
public class ServiceTokenGenerator {
  public static final AtomicReference<String> VERIFICATION_SERVICE_SECRET = new AtomicReference<>();
  private static ServiceTokenGenerator serviceTokenGenerator;

  public static synchronized ServiceTokenGenerator newInstance() {
    if (serviceTokenGenerator == null) {
      serviceTokenGenerator = new ServiceTokenGenerator();
    }
    return serviceTokenGenerator;
  }

  public String getVerificationServiceToken() {
    Preconditions.checkState(isNotEmpty(VERIFICATION_SERVICE_SECRET.get()),
        "could not read verification secret from system or env properties");
    return createNewToken(VERIFICATION_SERVICE_SECRET.get());
  }

  public String getServiceTokenWithDuration(String secretKey, Duration duration) {
    return getServiceTokenWithDuration(secretKey, duration, SecurityContextBuilder.getPrincipal());
  }

  public String getServiceTokenWithDuration(String secretKey, Duration duration, Principal principal) {
    if (isNotEmpty(secretKey)) {
      return createNewToken(secretKey, duration, principal);
    }
    throw new InvalidArgumentsException("secretKey cannot be empty", null);
  }

  public String getServiceToken(String secretKey) {
    return getServiceTokenWithDuration(secretKey, Duration.ofHours(4), SecurityContextBuilder.getPrincipal());
  }

  private String createNewToken(String serviceSecret, Duration tokenDuration, Principal principal) {
    try {
      Algorithm algorithm = Algorithm.HMAC256(serviceSecret);

      JWTCreator.Builder jwtBuilder =
          JWT.create()
              .withIssuer("Harness Inc")
              .withExpiresAt(new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(tokenDuration.toHours())))
              .withNotBefore(new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)))
              // added leeway of 1 min to IssuedAt claim as well to avoid minor clock drifts between microservices
              .withIssuedAt(new Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1)));

      addPrincipalToJWTBuilder(jwtBuilder, principal);
      return jwtBuilder.sign(algorithm);
    } catch (UnsupportedEncodingException | JWTCreationException exception) {
      throw new InvalidRequestException("error creating jwt token", exception);
    }
  }

  private void addPrincipalToJWTBuilder(JWTCreator.Builder jwtBuilder, Principal principal) {
    Map<String, String> claims = new HashMap<>();
    if (principal != null) {
      claims = principal.getJWTClaims();
    }
    if (claims.size() > 0) {
      claims.forEach(jwtBuilder::withClaim);
    }
  }

  private String createNewToken(String serviceSecret) {
    return createNewToken(serviceSecret, Duration.ofHours(4), SecurityContextBuilder.getPrincipal());
  }
}
