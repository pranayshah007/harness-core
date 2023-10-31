/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.expression;

import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.encoding.EncodingUtils;
import io.harness.rule.Owner;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class SecretDelegateFunctorTest extends CategoryTest {
  private SecretDelegateFunctor secretDelegateFunctor;
  Map<String, char[]> secrets = new HashMap<>();
  char[] rawSecret = {'m', 'y', 'S', 'e', 'c', 'r', 'e', 't'};
  String uuid = "key1";

  @Before
  public void before() {
    secrets.put(uuid, rawSecret);
    secretDelegateFunctor = SecretDelegateFunctor.builder().secrets(secrets).expressionFunctorToken(1234).build();
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testObtainBase64() {
    int token = 1234;
    for (int i = 0; i < 3; i++) {
      secretDelegateFunctor.obtainBase64(uuid, token);
    }
    String s = (String) secretDelegateFunctor.obtainBase64(uuid, token);
    assertThat(s).isEqualTo("bXlTZWNyZXQ=");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testObtainBase64ISO_8859_1() {
    Map<String, char[]> secrets = new HashMap<>();
    byte[] secret = "this is secret".getBytes(StandardCharsets.ISO_8859_1);
    secrets.put("secretRef", new String(secret, StandardCharsets.ISO_8859_1).toCharArray());
    SecretDelegateFunctor delegateFunctor =
        SecretDelegateFunctor.builder().secrets(secrets).expressionFunctorToken(1234).build();
    String s = (String) delegateFunctor.obtainBase64("secretRef", 1234);

    assertThat(EncodingUtils.decodeBase64(s)).isEqualTo(secret);
  }
}
