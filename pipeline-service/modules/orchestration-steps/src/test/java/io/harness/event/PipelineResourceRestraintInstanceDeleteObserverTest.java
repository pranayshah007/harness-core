/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.rule.OwnerRule.SHALINI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.steps.resourcerestraint.service.ResourceRestraintInstanceService;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineResourceRestraintInstanceDeleteObserverTest extends CategoryTest {
  @Mock private ResourceRestraintInstanceService resourceRestraintInstanceService;
  @InjectMocks PipelineResourceRestraintInstanceDeleteObserver pipelineResourceRestraintInstanceDeleteObserver;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testOnPlanExecutionsDelete() {
    pipelineResourceRestraintInstanceDeleteObserver.onPlanExecutionsDelete(Collections.emptyList());

    verify(resourceRestraintInstanceService, times(1)).deleteInstancesForGivenReleaseType(any(), any());
  }
}
