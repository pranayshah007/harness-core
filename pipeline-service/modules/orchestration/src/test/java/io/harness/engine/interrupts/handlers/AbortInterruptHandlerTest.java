/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.interrupts.Interrupt.State.DISCARDED;
import static io.harness.interrupts.Interrupt.State.PROCESSED_SUCCESSFULLY;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.interrupts.InterruptProcessingFailedException;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.interrupts.helpers.AbortHelper;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.Interrupt.State;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.EnumSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PIPELINE)
public class AbortInterruptHandlerTest extends OrchestrationTestBase {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private AbortHelper abortHelper;
  @Mock private InterruptService interruptService;
  @Inject @InjectMocks private AbortInterruptHandler abortInterruptHandler;
  @Inject private MongoTemplate mongoTemplate;

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testHandleInterrupt() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.REGISTERED)
                              .build();

    assertThatThrownBy(() -> abortInterruptHandler.handleInterrupt(interrupt))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldTestRegisterInterrupt() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.REGISTERED)
                              .build();

    assertThatThrownBy(() -> abortInterruptHandler.registerInterrupt(interrupt))
        .isInstanceOf(InterruptProcessingFailedException.class);

    // Interrupt with node execution id
    planExecutionId = generateUuid();
    interruptUuid = generateUuid();
    Interrupt interruptWithNodeExecutionId = Interrupt.builder()
                                                 .uuid(interruptUuid)
                                                 .nodeExecutionId("nodeExecutionId")
                                                 .type(InterruptType.ABORT_ALL)
                                                 .interruptConfig(InterruptConfig.newBuilder().build())
                                                 .planExecutionId(planExecutionId)
                                                 .state(State.REGISTERED)
                                                 .build();

    when(nodeExecutionService.updateStatusWithOps(interruptWithNodeExecutionId.getNodeExecutionId(),
             Status.DISCONTINUING, null, EnumSet.noneOf(Status.class)))
        .thenReturn(NodeExecution.builder().uuid("neUuid").build());
    when(interruptService.save(interruptWithNodeExecutionId)).thenReturn(interruptWithNodeExecutionId);
    when(interruptService.markProcessed(interruptUuid, PROCESSED_SUCCESSFULLY))
        .thenReturn(interruptWithNodeExecutionId);
    Interrupt handledInterrupt = abortInterruptHandler.registerInterrupt(interruptWithNodeExecutionId);

    ArgumentCaptor<NodeExecution> nodeExecutionArgumentCaptor = ArgumentCaptor.forClass(NodeExecution.class);
    ArgumentCaptor<Interrupt> interruptArgumentCaptor = ArgumentCaptor.forClass(Interrupt.class);
    verify(abortHelper)
        .discontinueMarkedInstance(nodeExecutionArgumentCaptor.capture(), interruptArgumentCaptor.capture());
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptUuid);

    assertThat(nodeExecutionArgumentCaptor.getValue().getUuid()).isEqualTo("neUuid");
    assertThat(interruptArgumentCaptor.getValue().getUuid()).isEqualTo(interruptUuid);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testSaveAndValidate() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.REGISTERED)
                              .build();

    assertThatThrownBy(() -> abortInterruptHandler.validateAndSave(interrupt))
        .isInstanceOf(InterruptProcessingFailedException.class);

    // with nodeExecution id
    String neUuid = generateUuid();
    Interrupt interruptWithNodeUuid = Interrupt.builder()
                                          .uuid(interruptUuid)
                                          .type(InterruptType.ABORT_ALL)
                                          .nodeExecutionId(neUuid)
                                          .interruptConfig(InterruptConfig.newBuilder().build())
                                          .planExecutionId(planExecutionId)
                                          .state(State.PROCESSING)
                                          .build();

    when(interruptService.fetchActiveInterruptsForNodeExecution(any(), any()))
        .thenReturn(Collections.singletonList(interrupt));
    abortInterruptHandler.validateAndSave(interruptWithNodeUuid);
    verify(interruptService, times(1)).markProcessed(interruptUuid, DISCARDED);

    // using interrupt with state as processing
    when(interruptService.fetchActiveInterruptsForNodeExecution(any(), any()))
        .thenReturn(Collections.singletonList(interruptWithNodeUuid));
    abortInterruptHandler.validateAndSave(interruptWithNodeUuid);
    verify(interruptService, times(1)).markProcessed(interruptUuid, PROCESSED_SUCCESSFULLY);
  }
}
