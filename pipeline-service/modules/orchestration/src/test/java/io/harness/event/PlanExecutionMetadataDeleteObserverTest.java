/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanExecutionMetadataDeleteObserverTest extends CategoryTest {
  @Mock PlanExecutionMetadataService planExecutionMetadataService;
  @Mock PlanService planService;
  @InjectMocks PlanExecutionMetadataDeleteObserver planExecutionMetadataDeleteObserver;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testOnPlanExecutionsDelete() {
    planExecutionMetadataDeleteObserver.onPlanExecutionsDelete(Collections.emptyList(), false);

    verify(planService, times(1)).deletePlansForGivenIds(any());
    verify(planExecutionMetadataService, times(1)).deleteMetadataForGivenPlanExecutionIds(any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testRetainPipelineExecutionDetailsAfterDeleteTrue() {
    planExecutionMetadataDeleteObserver.onPlanExecutionsDelete(Collections.emptyList(), true);

    verify(planService, times(1)).deletePlansForGivenIds(any());
    verify(planExecutionMetadataService, times(0)).deleteMetadataForGivenPlanExecutionIds(any());
  }
}
