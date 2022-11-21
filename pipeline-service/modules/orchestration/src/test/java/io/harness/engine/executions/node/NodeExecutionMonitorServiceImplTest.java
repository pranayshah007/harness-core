/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.node;

import static io.harness.rule.OwnerRule.SRIDHAR;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.execution.NodeExecution;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.LinkedList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionMonitorServiceImplTest extends OrchestrationTestBase {
  @Mock NodeExecutionService nodeExecutionService;
  @Mock MetricService metricService;
  @Inject @InjectMocks NodeExecutionMonitorService nodeExecutionMonitorService;

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testRegisterActiveExecutionMetrics() {
    List<NodeExecution> nodeExecutionList = new LinkedList<>();
    Pageable pageable = PageRequest.of(0, 1000);
    nodeExecutionList.add(NodeExecution.builder()
                              .uuid("UUID1")
                              .ambiance(Ambiance.newBuilder()
                                            .putAllSetupAbstractions(ImmutableMap.of(SetupAbstractionKeys.accountId,
                                                "accId1", SetupAbstractionKeys.orgIdentifier, "orgId1",
                                                SetupAbstractionKeys.projectIdentifier, "projId1"))
                                            .build())
                              .build());
    nodeExecutionList.add(NodeExecution.builder()
                              .uuid("UUID2")
                              .ambiance(Ambiance.newBuilder()
                                            .putAllSetupAbstractions(ImmutableMap.of(SetupAbstractionKeys.accountId,
                                                "accId2", SetupAbstractionKeys.orgIdentifier, "orgId2",
                                                SetupAbstractionKeys.projectIdentifier, "projId2"))
                                            .build())
                              .build());
    nodeExecutionList.add(NodeExecution.builder()
                              .uuid("UUID3")
                              .ambiance(Ambiance.newBuilder()
                                            .putAllSetupAbstractions(ImmutableMap.of(SetupAbstractionKeys.accountId,
                                                "accId3", SetupAbstractionKeys.orgIdentifier, "orgId3",
                                                SetupAbstractionKeys.projectIdentifier, "projId3"))
                                            .build())
                              .build());
    Page<NodeExecution> nodeExecutions = new PageImpl<>(nodeExecutionList, pageable, 1);
    doReturn(nodeExecutions).when(nodeExecutionService).fetchAllNodeExecutionsByStatus(any(), any(), any());
    nodeExecutionMonitorService.registerActiveExecutionMetrics();
    verify(metricService, times(3)).recordMetric(anyString(), anyDouble());
  }
}
