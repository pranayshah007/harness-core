/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class SecretCrudListenerTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void aDummyTest() {
    Assertions.assertThatThrownBy(() -> { throw new IllegalStateException("Random Error"); })
        .isInstanceOf(IllegalStateException.class);
  }
}
