/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.data.output;

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
public class PmsSweepingOutputTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testParseJson() {
    PmsSweepingOutput parsedEmptyString = PmsSweepingOutput.parse("");
    assertThat(parsedEmptyString).isNull();

    String emptyMap = "{}";
    PmsSweepingOutput parsedEmptyMap = PmsSweepingOutput.parse(emptyMap);
    assertThat(parsedEmptyMap).hasSize(0);

    String oneKeyMap = "{\"a\":\"b\"}";
    PmsSweepingOutput parsedOneKeyMap = PmsSweepingOutput.parse(oneKeyMap);
    assertThat(parsedOneKeyMap).isNotNull();
    assertThat(parsedOneKeyMap).hasSize(1);
    assertThat(parsedOneKeyMap.get("a")).isEqualTo("b");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testParseMap() {
    Map<String, Object> emptyMap = new HashMap<>();
    PmsSweepingOutput emptyOutput = PmsSweepingOutput.parse(emptyMap);
    assertThat(emptyOutput).isNull();

    Map<String, Object> oneKeyMap = Collections.singletonMap("a", "b");
    PmsSweepingOutput oneKeyOutput = PmsSweepingOutput.parse(oneKeyMap);
    assertThat(oneKeyOutput).isNotNull();
    assertThat(oneKeyOutput).hasSize(1);
    assertThat(oneKeyOutput.get("a")).isEqualTo("b");
  }
}
