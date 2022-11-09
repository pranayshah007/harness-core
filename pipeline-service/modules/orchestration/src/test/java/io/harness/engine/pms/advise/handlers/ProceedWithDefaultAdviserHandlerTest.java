/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.handlers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.interrupts.InterruptManager;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PIPELINE)
public class ProceedWithDefaultAdviserHandlerTest extends OrchestrationTestBase {
  @Mock private InterruptManager interruptManager;
  @InjectMocks @Inject private ProceedWithDefaultAdviserHandler proceedWithDefaultAdviserHandler;

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void shouldTestHandleAdvise() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
                                      .build();
    proceedWithDefaultAdviserHandler.handleAdvise(nodeExecution, AdviserResponse.newBuilder().build());
    ArgumentCaptor<InterruptPackage> argumentCaptor = ArgumentCaptor.forClass(InterruptPackage.class);
    verify(interruptManager, times(1)).register(argumentCaptor.capture());

    InterruptPackage interruptPackage = argumentCaptor.getValue();
    assertThat(interruptPackage.getInterruptType()).isEqualTo(InterruptType.PROCEED_WITH_DEFAULT);
    assertThat(interruptPackage.getNodeExecutionId()).isEqualTo(nodeExecutionId);
    assertThat(interruptPackage.getPlanExecutionId()).isEqualTo(planExecutionId);
    assertThat(interruptPackage.getInterruptConfig().getIssuedBy().getAdviserIssuer().getAdviserType())
        .isEqualTo(AdviseType.PROCEED_WITH_DEFAULT);
  }
}
