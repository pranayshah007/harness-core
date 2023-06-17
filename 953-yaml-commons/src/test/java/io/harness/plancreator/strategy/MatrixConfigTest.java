/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.strategy;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MatrixConfigTest {
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testSerDeser() throws IOException {
    // Handling stringified list as well.
    String yaml = "a: \"['1','2']\"\n"
        + "b: [2,3]\n"
        + "c: <+pipeline>\n"
        + "exclude:\n"
        + "  - a: 1\n"
        + "    b: 2\n"
        + "batchSize: 10";
    MatrixConfig matrixConfig = YamlUtils.read(yaml, MatrixConfig.class);
    assertThat(matrixConfig).isNotNull();
    assertThat(matrixConfig.getAxes().get("a").getAxisValue().getValue().size()).isEqualTo(2);
    assertThat(matrixConfig.getAxes().get("b").getAxisValue().getValue().size()).isEqualTo(2);
    assertThat(matrixConfig.getExpressionAxes().get("c").getExpression().getExpressionValue()).isEqualTo("<+pipeline>");
  }
}
