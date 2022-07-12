/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.interrupts.InterruptManager;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.execution.NodeExecution;
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

@OwnedBy(HarnessTeam.PIPELINE)
public class IgnoreFailureAdviseHandlerTest extends OrchestrationTestBase {
  @Mock private InterruptManager interruptManager;
  @Inject @InjectMocks private IgnoreFailureAdviseHandler handler;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void handleAdvise() {
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();

    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
                                      .build();

    handler.handleAdvise(nodeExecution, AdviserResponse.newBuilder().build());

    ArgumentCaptor<InterruptPackage> interruptPackageArgumentCaptor = ArgumentCaptor.forClass(InterruptPackage.class);

    verify(interruptManager).register(interruptPackageArgumentCaptor.capture());

    InterruptPackage interruptPackage = interruptPackageArgumentCaptor.getValue();
    assertThat(interruptPackage).isNotNull();
    assertThat(interruptPackage.getInterruptType()).isEqualTo(InterruptType.IGNORE);
    assertThat(interruptPackage.getPlanExecutionId()).isEqualTo(planExecutionId);
    assertThat(interruptPackage.getNodeExecutionId()).isEqualTo(nodeExecutionId);
  }
}
