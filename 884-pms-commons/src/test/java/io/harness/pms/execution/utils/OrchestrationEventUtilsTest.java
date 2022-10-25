/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.execution.utils;

import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.OrchestrationEvent;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.MDC;

@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationEventUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testObtainLogContext() {
    try (AutoLogContext ignored = OrchestrationEventUtils.obtainLogContext(
             OrchestrationEvent.newBuilder()
                 .setEventType(OrchestrationEventType.ORCHESTRATION_START)
                 .setAmbiance(Ambiance.newBuilder().putSetupAbstractions("k", "v").build())
                 .build())) {
      assertThat(MDC.get("eventType")).isEqualTo("ORCHESTRATION_START");
      assertThat(MDC.get("k")).isEqualTo("v");
    }
  }
}
