/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.rule.OwnerRule.AMAN;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.harness.agent.sdk.HarnessHierarchy;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.testing.TestExecution;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class CiBeansComponentTest extends CiBeansTestBase {
  @Inject private Map<String, TestExecution> tests;

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  @HarnessHierarchy(TestExecution.class)
  public void componentCiBeansTests() {
    for (Entry<String, TestExecution> test : tests.entrySet()) {
      assertThatCode(() -> test.getValue().run()).as(test.getKey()).doesNotThrowAnyException();
      log.info("{} passed", test.getKey());
    }
  }
}
