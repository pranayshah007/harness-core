/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.security.SecureRandom;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class X509SslContextBuilderTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testBuildWithNoFieldsSet() throws Exception {
    new X509SslContextBuilder().build();
  }

  @Test
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testBuildWithFieldsSet() throws Exception {
    new X509SslContextBuilder()
        .keyManager(new X509KeyManagerBuilder().build())
        .trustManager(new X509TrustManagerBuilder().trustAllCertificates().build())
        .secureRandom(new SecureRandom())
        .build();
  }
}
