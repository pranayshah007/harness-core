/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionGraph;
import io.harness.beans.OrchestrationGraph;
import io.harness.category.element.UnitTests;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.dto.converter.OrchestrationGraphDTOConverter;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.pms.pipeline.mappers.ExecutionGraphMapper;
import io.harness.rule.Owner;
import io.harness.service.GraphGenerationService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.util.CloseableIterator;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(PIPELINE)
public class ExecutionGraphServiceImplTest extends CategoryTest {
  @InjectMocks private ExecutionGraphServiceImpl executionGraphService;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock GraphGenerationService graphGenerationService;
  @Mock OrchestrationGraphDTOConverter orchestrationGraphDTOConverter;
  private AutoCloseable mocks;

  @Before
  public void setUp() throws IOException {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetNodeExecutionSubGraph() {
    CloseableIterator<NodeExecution> emptyIterator = createCloseableIterator(Collections.emptyListIterator());
    List<String> parentIds = new ArrayList<>();
    parentIds.add("nodeExecutionId");
    when(nodeExecutionService.fetchChildrenNodeExecutionsRecursivelyFromGivenParentIdWithoutOldRetries(
             "planExecutionId", parentIds))
        .thenReturn(new LinkedList<>());
    MockedStatic<OrchestrationGraphDTOConverter> aStatic = Mockito.mockStatic(OrchestrationGraphDTOConverter.class);
    aStatic.when(() -> OrchestrationGraphDTOConverter.convertFrom(any(OrchestrationGraph.class)))
        .thenReturn(OrchestrationGraphDTO.builder().build());
    MockedStatic<ExecutionGraphMapper> bStatic = Mockito.mockStatic(ExecutionGraphMapper.class);
    bStatic.when(() -> ExecutionGraphMapper.toExecutionGraph(any(OrchestrationGraphDTO.class)))
        .thenReturn(ExecutionGraph.builder().build());
    executionGraphService.getNodeExecutionSubGraph("nodeExecutionId", "planExecutionId");
  }

  public static <T> CloseableIterator<T> createCloseableIterator(Iterator<T> iterator) {
    return new CloseableIterator<T>() {
      @Override
      public void close() {}

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public T next() {
        return iterator.next();
      }
    };
  }
}
