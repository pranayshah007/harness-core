/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PIPELINE)
public class ExpireAllInterruptHandlerTest extends OrchestrationTestBase {
  @Mock private PlanExecutionService planExecutionService;
  @Inject @InjectMocks private ExpireAllInterruptHandler expireAllInterruptHandler;
  @Inject private MongoTemplate mongoTemplate;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testRegisterInterruptAbortAllPresent() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(Interrupt.State.REGISTERED)
                              .build();

    mongoTemplate.save(interrupt);

    assertThatThrownBy(
        ()
            -> expireAllInterruptHandler.registerInterrupt(Interrupt.builder()
                                                               .uuid(generateUuid())
                                                               .type(InterruptType.EXPIRE_ALL)
                                                               .interruptConfig(InterruptConfig.newBuilder().build())
                                                               .planExecutionId(planExecutionId)
                                                               .state(Interrupt.State.REGISTERED)
                                                               .build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Execution already has ABORT_ALL interrupt");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestRegisterInterruptExpireAllPresent() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.EXPIRE_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(Interrupt.State.REGISTERED)
                              .build();

    mongoTemplate.save(interrupt);
    when(planExecutionService.get(planExecutionId)).thenReturn(PlanExecution.builder().status(Status.RUNNING).build());
    assertThatThrownBy(
        ()
            -> expireAllInterruptHandler.registerInterrupt(Interrupt.builder()
                                                               .uuid(generateUuid())
                                                               .type(InterruptType.EXPIRE_ALL)
                                                               .interruptConfig(InterruptConfig.newBuilder().build())
                                                               .planExecutionId(planExecutionId)
                                                               .state(Interrupt.State.REGISTERED)
                                                               .build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Execution already has EXPIRE_ALL interrupt");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestRegisterInterruptExpireAllPresentForNode() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    String nodeExecutionId = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.EXPIRE_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .nodeExecutionId(nodeExecutionId)
                              .state(Interrupt.State.REGISTERED)
                              .build();

    mongoTemplate.save(interrupt);
    when(planExecutionService.get(planExecutionId)).thenReturn(PlanExecution.builder().status(Status.RUNNING).build());
    assertThatThrownBy(
        ()
            -> expireAllInterruptHandler.registerInterrupt(Interrupt.builder()
                                                               .uuid(generateUuid())
                                                               .type(InterruptType.EXPIRE_ALL)
                                                               .interruptConfig(InterruptConfig.newBuilder().build())
                                                               .planExecutionId(planExecutionId)
                                                               .nodeExecutionId(nodeExecutionId)
                                                               .state(Interrupt.State.REGISTERED)
                                                               .build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Execution already has EXPIRE_ALL interrupt for node");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestRegisterInterruptPlanEnded() {
    String planExecutionId = generateUuid();
    when(planExecutionService.get(planExecutionId)).thenReturn(PlanExecution.builder().status(Status.ABORTED).build());
    assertThatThrownBy(
        ()
            -> expireAllInterruptHandler.registerInterrupt(Interrupt.builder()
                                                               .uuid(generateUuid())
                                                               .type(InterruptType.EXPIRE_ALL)
                                                               .interruptConfig(InterruptConfig.newBuilder().build())
                                                               .planExecutionId(planExecutionId)
                                                               .state(Interrupt.State.REGISTERED)
                                                               .build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Plan Execution is already finished");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestRegisterInterruptSuccessful() {
    String planExecutionId = generateUuid();
    String interruptId = generateUuid();
    when(planExecutionService.get(planExecutionId)).thenReturn(PlanExecution.builder().status(Status.RUNNING).build());
    Interrupt interrupt =
        expireAllInterruptHandler.registerInterrupt(Interrupt.builder()
                                                        .uuid(interruptId)
                                                        .type(InterruptType.EXPIRE_ALL)
                                                        .interruptConfig(InterruptConfig.newBuilder().build())
                                                        .planExecutionId(planExecutionId)
                                                        .state(Interrupt.State.REGISTERED)
                                                        .build());

    assertThat(interrupt).isNotNull();
    assertThat(interrupt.getUuid()).isEqualTo(interruptId);
  }
}
