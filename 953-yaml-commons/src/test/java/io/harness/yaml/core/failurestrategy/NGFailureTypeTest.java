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
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.rule.Owner;

import java.util.EnumSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class NGFailureTypeTest extends CategoryTest {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testNGFailureTypes() {
    EnumSet<FailureType> failureTypes = EnumSet.noneOf(FailureType.class);
    for (NGFailureType ngFailureType : NGFailureType.values()) {
      assertThat(ngFailureType.getFailureTypes()).isNotEmpty();
      assertThat(ngFailureType.getYamlName()).isNotBlank();
      assertThat(NGFailureType.getFailureTypes(ngFailureType.getYamlName())).isEqualTo(ngFailureType);
      failureTypes.addAll(ngFailureType.getFailureTypes());
    }

    assertThat(NGFailureType.getAllFailureTypes()).isEqualTo(failureTypes);
    assertThatThrownBy(() -> NGFailureType.getFailureTypes("random_123")).isInstanceOf(IllegalArgumentException.class);
  }
}
