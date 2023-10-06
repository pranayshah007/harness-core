/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc;

import static io.harness.oidc.RSAKeysUtils.PRIVATE_KEY;
import static io.harness.oidc.RSAKeysUtils.PUBLIC_KEY;
import static io.harness.rule.OwnerRule.TEJAS;

import static junit.framework.TestCase.*;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class RSAKeysUtilsTest extends CategoryTest {
  private final String BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----";
  private final String END_PRIVATE_KEY = "-----END PRIVATE KEY-----\n";
  private final String BEGIN_PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----";
  private final String END_PUBLIC_KEY = "-----END PUBLIC KEY-----\n";
  RSAKeysUtils rsaKeysUtils;

  @Before
  public void setup() {
    rsaKeysUtils = new RSAKeysUtils();
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testKeyPairPEMGeneration() {
    RSAKeyPairPEM rsaKeyPairPEM = rsaKeysUtils.generateKeyPairPEM();

    String publicKeyPEM = rsaKeyPairPEM.getPublicKeyPem();
    assertTrue(StringUtils.isNotEmpty(publicKeyPEM));
    assertTrue(publicKeyPEM.startsWith(BEGIN_PUBLIC_KEY));
    assertTrue(publicKeyPEM.endsWith(END_PUBLIC_KEY));

    String privateKeyPEM = rsaKeyPairPEM.getPrivateKeyPem();
    assertTrue(StringUtils.isNotEmpty(privateKeyPEM));
    assertTrue(privateKeyPEM.startsWith(BEGIN_PRIVATE_KEY));
    assertTrue(privateKeyPEM.endsWith(END_PRIVATE_KEY));
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testReadPemFile() {
    RSAKeyPairPEM rsaKeyPairPEM = rsaKeysUtils.generateKeyPairPEM();

    RSAPublicKey publicKey = (RSAPublicKey) rsaKeysUtils.readPemFile(rsaKeyPairPEM.getPublicKeyPem());
    assertNotNull(publicKey);
    assertNotNull(publicKey.getPublicExponent());
    assertNotNull(publicKey.getModulus());
    assertEquals(rsaKeyPairPEM.getPublicKeyPem(), rsaKeysUtils.convertToPem(PUBLIC_KEY, publicKey));

    RSAPrivateKey privateKey = (RSAPrivateKey) rsaKeysUtils.readPemFile(rsaKeyPairPEM.getPrivateKeyPem());
    assertNotNull(privateKey);
    assertNotNull(privateKey.getModulus());
    assertNotNull(privateKey.getPrivateExponent());
    assertEquals(rsaKeyPairPEM.getPrivateKeyPem(), rsaKeysUtils.convertToPem(PRIVATE_KEY, privateKey));
  }
}
