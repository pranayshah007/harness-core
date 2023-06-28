/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.registrars;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.sdk.core.execution.events.node.facilitate.Facilitator;
import io.harness.rule.Owner;
import io.harness.steps.approval.ApprovalFacilitator;
import io.harness.steps.resourcerestraint.ResourceRestraintFacilitator;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PipelineServiceFacilitatorRegistrarTest extends CategoryTest {
  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetEngineFacilitators() {
    Map<FacilitatorType, Class<? extends Facilitator>> facilitatorTypeClassMap =
        PipelineServiceFacilitatorRegistrar.getEngineFacilitators();
    assertEquals(2L, facilitatorTypeClassMap.size());
    assertEquals(true, facilitatorTypeClassMap.containsValue(ResourceRestraintFacilitator.class));
    assertEquals(true, facilitatorTypeClassMap.containsValue(ApprovalFacilitator.class));
  }
}
