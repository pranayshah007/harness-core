/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.NAMANG;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.interfaces.Claim;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class JWTTokenServiceUtilsTest extends CategoryTest {
  private static final String ISSUER = "Harness Inc";
  private static final String EMAIL = randomAlphabetic(9) + ".com";
  private static final String JWT_PASSWORD_SECRET = randomAlphabetic(10);
  private static final String EMAIL_ID = "email";
  private static final String EXP = "exp";
  private static final String TYPE = "type";
  private static final String NAME = "name";
  private static final String PEM_KEY_VALID = loadResource("/io/harness/security/certs/key-valid.pem");
  private static final ImmutableMap<String, String> claims = ImmutableMap.of(
      "iss", "clientID", "sub", "clientID", "aud", "https://aud.url.com", "jti", UUIDGenerator.generateUuid());
  private static final ImmutableMap<String, Object> headers =
      ImmutableMap.of("alg", "RS256", "typ", "JWT", "kid", UUIDGenerator.generateUuid());

  private String generateToken(ImmutableMap<String, String> claims) {
    try {
      Algorithm algorithm = Algorithm.HMAC256(JWT_PASSWORD_SECRET);
      JWTCreator.Builder jwtBuilder = JWT.create().withIssuer(ISSUER).withIssuedAt(new Date());

      if (!isEmpty(claims)) {
        claims.forEach(jwtBuilder::withClaim);
      }
      return jwtBuilder.sign(algorithm);
    } catch (UnsupportedEncodingException | JWTCreationException exception) {
      throw new JWTCreationException("JWTToken could not be generated", exception);
    }
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void isvalidServiceAuthTest() {
    ImmutableMap<String, String> claims = ImmutableMap.of(EMAIL_ID, EMAIL);
    String jwtToken = JWTTokenServiceUtils.generateJWTToken(
        claims, TimeUnit.MILLISECONDS.convert(60, TimeUnit.DAYS), JWT_PASSWORD_SECRET);
    Pair<Boolean, Map<String, Claim> > authMap =
        JWTTokenServiceUtils.isServiceAuthorizationValid(jwtToken, JWT_PASSWORD_SECRET);
    System.out.println(authMap);
    assertThat(authMap.getKey().booleanValue()).isTrue();
    assertThat(authMap.getValue().containsKey(EXP)).isTrue();
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void isvalidServiceAuthTestWithPrincipal() {
    ImmutableMap<String, String> claims = ImmutableMap.of(TYPE, "USER", NAME, EMAIL);
    String jwtToken = JWTTokenServiceUtils.generateJWTToken(
        claims, TimeUnit.MILLISECONDS.convert(60, TimeUnit.DAYS), JWT_PASSWORD_SECRET);
    Pair<Boolean, Map<String, Claim> > authMap =
        JWTTokenServiceUtils.isServiceAuthorizationValid(jwtToken, JWT_PASSWORD_SECRET);
    assertThat(authMap.getKey().booleanValue()).isTrue();
    assertThat(authMap.getValue().containsKey(EXP)).isTrue();
    assertThat(authMap.getValue().containsKey(TYPE)).isTrue();
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void isInvalidServiceAuthTestWithPrincipalWithoutExp() {
    ImmutableMap<String, String> claims = ImmutableMap.of(TYPE, "USER", NAME, "xyz@gmail.com");
    String jwtToken = generateToken(claims);
    Pair<Boolean, Map<String, Claim> > authMap =
        JWTTokenServiceUtils.isServiceAuthorizationValid(jwtToken, JWT_PASSWORD_SECRET);
    assertThat(authMap.getKey().booleanValue()).isTrue();
    assertThat(authMap.getValue().containsKey(EXP)).isFalse();
    assertThat(authMap.getValue().containsKey(TYPE)).isTrue();
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void isInvalidServiceAuthTestWithoutPrincipalWithoutExp() {
    ImmutableMap<String, String> claims = ImmutableMap.of(EMAIL_ID, EMAIL);
    String jwtToken = generateToken(claims);
    Pair<Boolean, Map<String, Claim> > authMap =
        JWTTokenServiceUtils.isServiceAuthorizationValid(jwtToken, JWT_PASSWORD_SECRET);
    assertThat(authMap.getKey().booleanValue()).isTrue();
    assertThat(authMap.getValue().containsKey(EXP)).isFalse();
    assertThat(authMap.getValue().containsKey(TYPE)).isFalse();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void isInvalidServiceAuthTestExpiredToken() throws InterruptedException {
    ImmutableMap<String, String> claims = ImmutableMap.of(EMAIL_ID, EMAIL);
    String jwtToken = JWTTokenServiceUtils.generateJWTToken(claims, 1L, JWT_PASSWORD_SECRET);
    Thread.sleep(1100);
    JWTTokenServiceUtils.isServiceAuthorizationValid(jwtToken, JWT_PASSWORD_SECRET);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGenerateJWTTokenWithRSAPrivateKey() throws Exception {
    RSAKey rsaKey = getRSAPrivateKey();
    String jwtToken = JWTTokenServiceUtils.generateJWTToken(claims, headers, TimeUnit.DAYS.toMillis(2), rsaKey);
    assertThat(jwtToken).isNotNull();
    // key is null
    assertThatThrownBy(() -> JWTTokenServiceUtils.generateJWTToken(claims, headers, TimeUnit.DAYS.toMillis(2), null))
        .isInstanceOf(InvalidRequestException.class);
    // invalid private key but a valid key
    assertThatThrownBy(
        () -> JWTTokenServiceUtils.generateJWTToken(claims, headers, TimeUnit.DAYS.toMillis(2), getRSAPublicKey()))
        .isInstanceOf(JWTCreationException.class);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGenerateVerifyJWTTokenWithRSAPrivateKey() throws Exception {
    RSAKey rsaKey = getRSAPrivateKey();
    String jwtToken = JWTTokenServiceUtils.generateJWTToken(claims, headers, TimeUnit.DAYS.toMillis(2), rsaKey);
    Map<String, Claim> decodedClaims = JWTTokenServiceUtils.verifyJWTToken(jwtToken, getRSAPublicKey(), "clientID");
    assertThat(decodedClaims.size()).isEqualTo(6);
    assertThat(decodedClaims.get("iss").asString()).isEqualTo("clientID");
    assertThat(decodedClaims.get("aud").asString()).isEqualTo("https://aud.url.com");
    assertThat(decodedClaims.get("sub").asString()).isEqualTo("clientID");
    assertThat(TimeUnit.MILLISECONDS.toDays(
                   decodedClaims.get("exp").asDate().getTime() - decodedClaims.get("iat").asDate().getTime()))
        .isEqualTo(2);

    // invalid token case (iss claim)
    assertThatThrownBy(() -> JWTTokenServiceUtils.verifyJWTToken(jwtToken, getRSAPublicKey(), "diffClientID"))
        .isInstanceOf(InvalidRequestException.class);
  }

  private RSAKey getRSAPrivateKey() throws Exception {
    PKCS8EncodedKeySpec pkcs8EncodedKeySpec =
        X509KeyManagerBuilder.loadPkcs8EncodedPrivateKeySpecFromPem(PEM_KEY_VALID);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    return (RSAKey) keyFactory.generatePrivate(pkcs8EncodedKeySpec);
  }

  private RSAKey getRSAPublicKey() throws Exception {
    RSAPrivateCrtKey rsaPrivateCrtKey = (RSAPrivateCrtKey) getRSAPrivateKey();
    RSAPublicKeySpec publicKeySpec =
        new RSAPublicKeySpec(rsaPrivateCrtKey.getModulus(), rsaPrivateCrtKey.getPublicExponent());
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    return (RSAKey) keyFactory.generatePublic(publicKeySpec);
  }

  private static String loadResource(String resourcePath) {
    try {
      return Resources.toString(JWTTokenServiceUtilsTest.class.getResource(resourcePath), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      return "NOT FOUND";
    }
  }
}
