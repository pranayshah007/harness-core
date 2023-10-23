/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.idtoken;

import static io.harness.oidc.idtoken.OidcIdTokenUtility.generateOidcIdToken;
import static io.harness.rule.OwnerRule.RAGHAV_MURALI;

import static java.lang.System.currentTimeMillis;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rsa.RSAKeysUtils;
import io.harness.rule.Owner;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class OidcIdTokenUtilityTest extends CategoryTest {
  private static final String oidc_sub = "Provider:Harness:Account:kmpySmUISimoRrJL6NL73w";
  private static final String oidc_aud =
      "https://iam.googleapis.com/projects/781732827384/locations/global/workloadIdentityPools/oidc_test/providers/harness";
  private static final String oidc_iss = "https://token.oidc.harness.io/account/kmpySmUISimoRrJL6NL73w";
  OidcIdTokenHeaderStructure oidcIdTokenHeaderStructure;
  OidcIdTokenPayloadStructure oidcIdTokenPayloadStructure;
  RSAKeysUtils rsaKeysUtils;

  @Before
  public void setup() {
    oidcIdTokenHeaderStructure = OidcIdTokenHeaderStructure.builder().typ("JWT").alg("RS256").kid("1234567").build();

    Long base = currentTimeMillis();
    oidcIdTokenPayloadStructure = OidcIdTokenPayloadStructure.builder()
                                      .sub(oidc_sub)
                                      .aud(oidc_aud)
                                      .iss(oidc_iss)
                                      .iat(Long.toString(base))
                                      .exp(base + 3599)
                                      .account_id("kmpySmUISimoRrJL6NL73w")
                                      .build();

    rsaKeysUtils = new RSAKeysUtils();
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void generateOidcIdToken_idToken_success() {
    // Get the RSA Key Pair
    KeyPair keyPair = rsaKeysUtils.generateKeyPair();
    String privateKeyPEM = rsaKeysUtils.convertToPem(rsaKeysUtils.PRIVATE_KEY, keyPair.getPrivate());
    String idToken = generateOidcIdToken(oidcIdTokenHeaderStructure, oidcIdTokenPayloadStructure, privateKeyPEM);

    try {
      Jws<Claims> jws = Jwts.parser().setSigningKey(keyPair.getPublic()).parseClaimsJws(idToken);

      // Get the parsed claims
      Claims claims = jws.getBody();

      // Assert the claims
      assertThat(claims.getSubject()).isEqualTo(oidc_sub);
      assertThat(claims.getAudience()).isEqualTo(oidc_aud);
      assertThat(claims.getIssuer()).isEqualTo(oidc_iss);
    } catch (Exception ex) {
      log.error("Error is {} ", ex);
    }
  }
}
