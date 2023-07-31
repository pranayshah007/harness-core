/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(HarnessTeam.PIPELINE)
public class YamlFunctorTest extends CategoryTest {
  @InjectMocks private YamlEvaluatorFunctor yamlEvaluator;

  String yaml = "pipeline:\n"
      + "  identifier: trialselective\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: Test1\n"
      + "        type: Custom\n"
      + "        spec:\n"
      + "          execution:\n"
      + "            steps:\n"
      + "              - step:\n"
      + "                  identifier: Wait_1\n"
      + "                  type: Wait\n"
      + "                  spec:\n"
      + "                    duration: 1m\n"
      + "    - parallel:\n"
      + "        - stage:\n"
      + "            identifier: test2\n"
      + "            type: Custom\n"
      + "            spec:\n"
      + "              execution:\n"
      + "                steps:\n"
      + "                  - step:\n"
      + "                      identifier: Wait_1\n"
      + "                      type: Wait\n"
      + "                      spec:\n"
      + "                        duration: 1m\n";

  @Before
  public void setUp() {
    yamlEvaluator = new YamlEvaluatorFunctor(yaml);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testBind() {
    Map<String, Object> map = (Map<String, Object>) yamlEvaluator.bind();
    assertThat(map).isNotNull();
    assertThat(map.containsKey("pipeline")).isTrue();
  }
}
