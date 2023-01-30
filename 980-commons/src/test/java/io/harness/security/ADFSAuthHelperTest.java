/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.AdfsAuthException;
import io.harness.rule.Owner;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.google.common.io.Resources;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class ADFSAuthHelperTest extends CategoryTest {
  private static final String PEM_KEY_VALID = loadResource("/io/harness/security/certs/adfs-test/rsa-key-valid.pem");
  private static final String PEM_CERT_VALID = loadResource("/io/harness/security/certs/adfs-test/x509-cert-valid.pem");
  private static final String CLIENT_ID = "clientID";
  private static final String ADFS_URL = "https://adfs.test.com";
  private static String loadResource(String resourcePath) {
    try {
      return Resources.toString(ADFSAuthHelperTest.class.getResource(resourcePath), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      return "NOT FOUND";
    }
  }

  private RSAKey getRSAPublicKey(String privateKeyContent) throws Exception {
    PKCS8EncodedKeySpec pkcs8EncodedKeySpec =
        X509KeyManagerBuilder.loadPkcs8EncodedPrivateKeySpecFromPem(privateKeyContent);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    RSAPrivateCrtKey rsaPrivateCrtKey = (RSAPrivateCrtKey) keyFactory.generatePrivate(pkcs8EncodedKeySpec);
    RSAPublicKeySpec publicKeySpec =
        new RSAPublicKeySpec(rsaPrivateCrtKey.getModulus(), rsaPrivateCrtKey.getPublicExponent());
    return (RSAKey) keyFactory.generatePublic(publicKeySpec);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSmokeGenerateJwtSignedRequestWithCertificate() throws Exception {
    String jwtToken =
        ADFSAuthHelper.generateJwtSignedRequestWithCertificate(PEM_CERT_VALID, PEM_KEY_VALID, CLIENT_ID, ADFS_URL);

    // validated generated jwt using public key
    Map<String, Claim> decodedClaims =
        JWTTokenServiceUtils.verifyJWTToken(jwtToken, getRSAPublicKey(PEM_KEY_VALID), CLIENT_ID);

    // validating claims
    assertThat(decodedClaims.size()).isEqualTo(6);
    assertThat(decodedClaims.get("iss").asString()).isEqualTo(CLIENT_ID);
    assertThat(decodedClaims.get("aud").asString()).isEqualTo(ADFS_URL);
    assertThat(decodedClaims.get("sub").asString()).isEqualTo(CLIENT_ID);
    assertThat(TimeUnit.MILLISECONDS.toDays(
                   decodedClaims.get("exp").asDate().getTime() - decodedClaims.get("iat").asDate().getTime()))
        .isEqualTo(2);

    // validating headers
    assertThat(JWT.decode(jwtToken).getHeaderClaim("alg").asString()).isEqualTo("RS256");
    assertThat(JWT.decode(jwtToken).getHeaderClaim("typ").asString()).isEqualTo("JWT");
    assertThat(JWT.decode(jwtToken).getHeaderClaim("kid").asString())
        .isEqualTo("25A3135CE45C1E36101906EE8729EAA412B655FE");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGenerateJwtSignedRequestWithCertificate_InvalidCertificateFormat() throws Exception {
    assertThatThrownBy(
        () -> ADFSAuthHelper.generateJwtSignedRequestWithCertificate(PEM_KEY_VALID, PEM_KEY_VALID, CLIENT_ID, ADFS_URL))
        .isInstanceOf(AdfsAuthException.class);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGenerateJwtSignedRequestWithCertificate_InvalidKeyFormat() throws Exception {
    assertThatThrownBy(()
                           -> ADFSAuthHelper.generateJwtSignedRequestWithCertificate(
                               PEM_CERT_VALID, PEM_CERT_VALID, CLIENT_ID, ADFS_URL))
        .isInstanceOf(AdfsAuthException.class);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGenerateJwtSignedRequestWithCertificate_ValidDifferentKeyFormat() throws Exception {
    assertThatThrownBy(()
                           -> ADFSAuthHelper.generateJwtSignedRequestWithCertificate(
                               PEM_CERT_VALID, getRSAPublicKey(PEM_KEY_VALID).toString(), CLIENT_ID, ADFS_URL))
        .isInstanceOf(AdfsAuthException.class);
  }
}
