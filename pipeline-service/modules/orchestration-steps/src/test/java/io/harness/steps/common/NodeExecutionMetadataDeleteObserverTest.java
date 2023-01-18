/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.common;

import static io.harness.rule.OwnerRule.ARCHIT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.execution.NodeExecution;
import io.harness.rule.Owner;
import io.harness.steps.resourcerestraint.service.ResourceRestraintInstanceService;
import io.harness.timeout.TimeoutEngine;
import io.harness.waiter.persistence.SpringPersistenceWrapper;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionMetadataDeleteObserverTest extends CategoryTest {
  @Mock private SpringPersistenceWrapper springPersistenceWrapper;
  @Mock private TimeoutEngine timeoutEngine;
  @Mock private ResourceRestraintInstanceService resourceRestraintInstanceService;
  @InjectMocks NodeExecutionMetadataDeleteObserver nodeExecutionMetadataDeleteObserver;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestNodesDelete() {
    nodeExecutionMetadataDeleteObserver.onNodesDelete(Collections.emptyList());
    verify(springPersistenceWrapper, times(0)).deleteWaitInstancesAndMetadata(any());
    verify(timeoutEngine, times(0)).deleteTimeouts(any());
    verify(resourceRestraintInstanceService, times(0)).deleteInstancesForGivenReleaseType(any(), any());

    List<NodeExecution> nodeExecutionList = new LinkedList<>();
    nodeExecutionList.add(NodeExecution.builder().uuid(UUIDGenerator.generateUuid()).build());
    nodeExecutionList.add(NodeExecution.builder().uuid(UUIDGenerator.generateUuid()).build());
    nodeExecutionList.add(NodeExecution.builder().uuid(UUIDGenerator.generateUuid()).build());
    nodeExecutionList.add(NodeExecution.builder().uuid(UUIDGenerator.generateUuid()).build());
    nodeExecutionMetadataDeleteObserver.onNodesDelete(nodeExecutionList);
    verify(springPersistenceWrapper, times(1)).deleteWaitInstancesAndMetadata(any());
    verify(timeoutEngine, times(1)).deleteTimeouts(any());
    verify(resourceRestraintInstanceService, times(1)).deleteInstancesForGivenReleaseType(any(), any());
  }
}
