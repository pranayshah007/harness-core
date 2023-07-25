/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.DEFAULT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.EXPIRED_TOKEN;
import static io.harness.eraro.ErrorCode.INVALID_TOKEN;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_ADMIN;
import static io.harness.exception.WingsException.USER_SRE;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.SPACE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.security.dto.Principal;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.InvalidClaimException;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.Claim;
import java.io.UnsupportedEncodingException;
import java.security.interfaces.RSAKey;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(PL)
@UtilityClass
@Slf4j
public class JWTTokenServiceUtils {
  private final String ISSUER = "Harness Inc";

  public Pair<Boolean, Map<String, Claim> > isServiceAuthorizationValid(String serviceToken, String serviceSecret) {
    Map<String, Claim> claimMap = verifyJWTToken(serviceToken, serviceSecret);
    if (!claimMap.containsKey("exp")) {
      log.warn("JWTTokenServiceUtils Class verifies JWT Token without Expiry Date.");
      Principal principal = SecurityContextBuilder.getPrincipalFromClaims(claimMap);
      if (principal != null) {
        log.info(String.format(
            "Principal type is %s and its name is %s", principal.getType().toString(), principal.getName()));
      }
    }
    return Pair.of(true, claimMap);
  }

  public Map<String, Claim> verifyJWTToken(String jwtToken, String secret) {
    try {
      Algorithm algorithm = Algorithm.HMAC256(secret);
      JWTVerifier verifier = JWT.require(algorithm).withIssuer(ISSUER).build();
      verifier.verify(jwtToken);
      return JWT.decode(jwtToken).getClaims();
    } catch (UnsupportedEncodingException | JWTDecodeException | SignatureVerificationException e) {
      throw new InvalidRequestException(
          "Invalid JWTToken received, failed to decode the token", e, INVALID_TOKEN, USER);
    } catch (InvalidClaimException e) {
      throw new InvalidRequestException("Token expired", e, EXPIRED_TOKEN, USER);
    }
  }

  public Map<String, Claim> verifyJWTToken(String jwtToken, RSAKey secret) {
    try {
      Algorithm algorithm = Algorithm.RSA256(secret);
      JWTVerifier verifier = JWT.require(algorithm).withIssuer(ISSUER).build();
      verifier.verify(jwtToken);
      return JWT.decode(jwtToken).getClaims();
    } catch (JWTDecodeException | SignatureVerificationException e) {
      throw new InvalidRequestException(
          "Invalid JWTToken received, failed to decode the token", e, INVALID_TOKEN, USER);
    } catch (InvalidClaimException e) {
      throw new InvalidRequestException("Token expired", e, EXPIRED_TOKEN, USER);
    }
  }

  public static Map<String, Claim> verifyJWTToken(String jwtToken, RSAKey publicKey, String issuer) {
    try {
      Algorithm algorithm = Algorithm.RSA256(publicKey);
      JWTVerifier verifier = JWT.require(algorithm).withIssuer(issuer).build();
      verifier.verify(jwtToken);
      return JWT.decode(jwtToken).getClaims();
    } catch (JWTDecodeException | SignatureVerificationException | InvalidClaimException e) {
      throw new InvalidRequestException(
          "Error while verifying jwt token: Invalid JWTToken received, failed to decode the token", e, INVALID_TOKEN,
          USER);
    }
  }

  public String extractToken(ContainerRequestContext requestContext, String prefix) {
    return extractToken(HttpHeaders.AUTHORIZATION, requestContext, prefix)
        .orElseThrow(() -> new InvalidRequestException("Invalid token", INVALID_TOKEN, USER_ADMIN));
  }

  public Optional<String> extractToken(String headerName, ContainerRequestContext requestContext, String prefix) {
    String headerValue = requestContext.getHeaderString(headerName);
    if (headerValue == null || !headerValue.startsWith(prefix)) {
      return Optional.empty();
    }
    return Optional.of(headerValue.substring(prefix.length()).trim());
  }

  public String extractSecret(Map<String, String> serviceToSecretMapping, String source) {
    if (serviceToSecretMapping != null && serviceToSecretMapping.containsKey(source)) {
      return serviceToSecretMapping.get(source);
    }
    if (serviceToSecretMapping != null && serviceToSecretMapping.containsKey(DEFAULT.getServiceId())) {
      return serviceToSecretMapping.get(DEFAULT.getServiceId());
    }
    throw new InvalidRequestException(String.format("Unknown Source [%s]", source), USER);
  }

  public String extractSource(ContainerRequestContext requestContext) {
    return extractSource(HttpHeaders.AUTHORIZATION, requestContext)
        .orElseThrow(() -> new InvalidRequestException("Invalid token", INVALID_TOKEN, USER_SRE));
  }

  public Optional<String> extractSource(String headerName, ContainerRequestContext requestContext) {
    String headerValue = requestContext.getHeaderString(headerName);
    if (headerValue == null) {
      return Optional.empty();
    }

    String[] sourceAndToken = headerValue.trim().split(SPACE, 2);
    if (sourceAndToken.length < 2) {
      return Optional.empty();
    }
    return Optional.of(sourceAndToken[0].trim());
  }

  public String generateJWTToken(Map<String, String> claims, Long validityDurationInMillis, String jwtPasswordSecret) {
    if (jwtPasswordSecret == null) {
      throw new InvalidRequestException("Could not find verification secret token");
    }
    try {
      Algorithm algorithm = Algorithm.HMAC256(jwtPasswordSecret);
      JWTCreator.Builder jwtBuilder = JWT.create().withIssuer(ISSUER).withIssuedAt(new Date());

      if (validityDurationInMillis != null) {
        jwtBuilder.withExpiresAt(new Date(System.currentTimeMillis() + validityDurationInMillis));
      } else {
        jwtBuilder.withExpiresAt(new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)));
      }
      if (!isEmpty(claims)) {
        claims.forEach(jwtBuilder::withClaim);
      }
      return jwtBuilder.sign(algorithm);
    } catch (UnsupportedEncodingException | JWTCreationException exception) {
      throw new JWTCreationException("JWTToken could not be generated", exception);
    }
  }

  public String generateJWTToken(Map<String, String> claims, Map<String, String[]> arrayClaims,
      Long validityDurationInMillis, String jwtPasswordSecret) {
    if (jwtPasswordSecret == null) {
      throw new InvalidRequestException("Could not find verification secret token");
    }
    try {
      Algorithm algorithm = Algorithm.HMAC256(jwtPasswordSecret);
      JWTCreator.Builder jwtBuilder = JWT.create().withIssuer(ISSUER).withIssuedAt(new Date());

      if (validityDurationInMillis != null) {
        jwtBuilder.withExpiresAt(new Date(System.currentTimeMillis() + validityDurationInMillis));
      } else {
        jwtBuilder.withExpiresAt(new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)));
      }
      if (!isEmpty(claims)) {
        claims.forEach(jwtBuilder::withClaim);
      }
      if (!isEmpty(arrayClaims)) {
        arrayClaims.forEach(jwtBuilder::withArrayClaim);
      }
      return jwtBuilder.sign(algorithm);
    } catch (UnsupportedEncodingException | JWTCreationException exception) {
      throw new JWTCreationException("JWTToken could not be generated", exception);
    }
  }

  public static String generateJWTToken(
      Map<String, String> jwtClaims, Map<String, Object> jwtHeader, Long validityDurationInMillis, RSAKey privateKey) {
    if (isNull(privateKey)) {
      throw new InvalidRequestException("Empty privateKey provided to generate Jwt Token");
    }
    try {
      Algorithm algorithm = Algorithm.RSA256(privateKey);

      Date exp = new Date(System.currentTimeMillis() + validityDurationInMillis);
      Date iat = new Date(System.currentTimeMillis());

      JWTCreator.Builder jwtBuilder = JWT.create().withIssuedAt(iat).withExpiresAt(exp).withHeader(jwtHeader);

      if (isNotEmpty(jwtClaims)) {
        jwtClaims.forEach(jwtBuilder::withClaim);
      }
      return jwtBuilder.sign(algorithm);
    } catch (IllegalArgumentException | JWTCreationException exception) {
      throw new JWTCreationException("JWTToken could not be generated", exception);
    }
  }
}
