/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.data.stepdetails;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PmsStepDetailsTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testParseJson() {
    String emptyMap = "{}";
    PmsStepDetails parsedEmptyMap = PmsStepDetails.parse(emptyMap);
    assertThat(parsedEmptyMap).hasSize(0);

    String oneKeyMap = "{\"a\":\"b\"}";
    PmsStepDetails parsedOneKeyMap = PmsStepDetails.parse(oneKeyMap);
    assertThat(parsedOneKeyMap).isNotNull();
    assertThat(parsedOneKeyMap).hasSize(1);
    assertThat(parsedOneKeyMap.get("a")).isEqualTo("b");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testParseMap() {
    Map<String, Object> emptyMap = new HashMap<>();
    PmsStepDetails emptyOutput = PmsStepDetails.parse(emptyMap);
    assertThat(emptyOutput).hasSize(0);

    Map<String, Object> oneKeyMap = Collections.singletonMap("a", "b");
    PmsStepDetails oneKeyOutput = PmsStepDetails.parse(oneKeyMap);
    assertThat(oneKeyOutput).isNotNull();
    assertThat(oneKeyOutput).hasSize(1);
    assertThat(oneKeyOutput.get("a")).isEqualTo("b");
  }
}
