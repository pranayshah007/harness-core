/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.TrustManagerBuilderException;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class X509TrustManagerBuilderTest extends CategoryTest {
  private static final X509Certificate[] BASIC_SELF_SIGNED_CERT =
      loadCertificates("/io/harness/security/certs/cert-valid.pem");

  private static X509Certificate[] loadCertificates(String resourcePath) {
    try {
      byte[] certAsPem = Resources.toByteArray(X509KeyManagerBuilderTest.class.getResource(resourcePath));
      CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
      return certificateFactory.generateCertificates(new ByteArrayInputStream(certAsPem))
          .stream()
          .map(cert -> (X509Certificate) cert)
          .toArray(X509Certificate[] ::new);
    } catch (Exception ex) {
      return null;
    }
  }

  @Test
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testTrustAllCerts() throws Exception {
    X509TrustManager manager = new X509TrustManagerBuilder().trustAllCertificates().build();

    assertThat(manager.getAcceptedIssuers().length).isEqualTo(0);
    manager.checkServerTrusted(BASIC_SELF_SIGNED_CERT, "");
  }

  @Test(expected = CertificateException.class)
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testTrustDefaultTrustStore() throws Exception {
    X509TrustManager manager = new X509TrustManagerBuilder().trustDefaultTrustStore().build();

    assertThat(manager.getAcceptedIssuers().length).isGreaterThan(0);
    manager.checkServerTrusted(BASIC_SELF_SIGNED_CERT, "RSA");
  }

  @Test(expected = TrustManagerBuilderException.class)
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testTrustAllCertsFailsIfAlreadyTrustDefaultTrustStore() throws Exception {
    new X509TrustManagerBuilder().trustDefaultTrustStore().trustAllCertificates().build();
  }

  @Test(expected = TrustManagerBuilderException.class)
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testTrustDefaultTrustStoreFailsIfAlreadyTrustAllCerts() throws Exception {
    new X509TrustManagerBuilder().trustAllCertificates().trustDefaultTrustStore().build();
  }

  @Test(expected = TrustManagerBuilderException.class)
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testBuildWithoutTrustedCertificateFails() throws Exception {
    new X509TrustManagerBuilder().build();
  }
}
