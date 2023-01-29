/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ElastigroupFixedInstancesTest extends CategoryTest {
  @Test
  @Owner(developers = {VITALIE})
  @Category(UnitTests.class)
  public void getTypeTest() {
    ElastigroupFixedInstances instance = ElastigroupFixedInstances.builder()
                                             .max(ParameterField.createValueField(10))
                                             .min(ParameterField.createValueField(1))
                                             .desired(ParameterField.createValueField(2))
                                             .build();

    assertThat(instance.getType()).isEqualTo(ElastigroupInstancesType.FIXED);
  }
}
