/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.core.failurestrategy;

import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class NGFailureActionTypeTest extends CategoryTest {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testNGFailureTypes() {
    for (NGFailureActionType ngFailureActionType : NGFailureActionType.values()) {
      assertThat(ngFailureActionType.getYamlName()).isNotBlank();
      assertThat(NGFailureActionType.getFailureActionType(ngFailureActionType.getYamlName()))
          .isEqualTo(ngFailureActionType);
    }

    assertThatThrownBy(() -> NGFailureActionType.getFailureActionType("random_123"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
