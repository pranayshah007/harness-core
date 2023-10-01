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
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineExecutionSummaryDeleteObserverTest extends CategoryTest {
  @Mock PmsExecutionSummaryService pmsExecutionSummaryService;
  @InjectMocks PipelineExecutionSummaryDeleteObserver pipelineExecutionSummaryDeleteObserver;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testOnPlanExecutionsDelete() {
    pipelineExecutionSummaryDeleteObserver.onPlanExecutionsDelete(Collections.emptyList(), false);

    verify(pmsExecutionSummaryService, times(1)).deleteAllSummaryForGivenPlanExecutionIds(any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testRetainPipelineExecutionDetailsAfterDeleteTrue() {
    pipelineExecutionSummaryDeleteObserver.onPlanExecutionsDelete(Collections.emptyList(), true);

    verify(pmsExecutionSummaryService, times(0)).deleteAllSummaryForGivenPlanExecutionIds(any());
  }
}
