/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.exception.EngineFunctorException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.resolver.outcome.mapper.PmsOutcomeMapper;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.custom.CustomApprovalOutcome;
import io.harness.steps.approval.step.harness.HarnessApprovalOutcome;
import io.harness.steps.approval.step.harness.HarnessApprovalStep;
import io.harness.steps.approval.step.harness.beans.EmbeddedUserDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityDTO;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class ApprovalFunctorTest extends CategoryTest {
  public static final String APPROVAL_NAME = "Admin";
  public static final String APPROVAL_EMAIL = "admin@harness.io";
  public static final String APPROVAL_COMMENT = "Approval comment";
  @Mock private PmsOutcomeService pmsOutcomeService;
  @InjectMocks private ApprovalFunctor approvalFunctor;
  private final Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("execution_id").build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testBind() {
    on(approvalFunctor).set("ambiance", ambiance);
    when(pmsOutcomeService.fetchOutcomesByStepTypeAndCategory(ambiance.getPlanExecutionId(),
             HarnessApprovalStep.STEP_TYPE.getType(), HarnessApprovalStep.STEP_TYPE.getStepCategory().name()))
        .thenReturn(List.of(PmsOutcomeMapper.convertOutcomeValueToJson(
            HarnessApprovalOutcome.builder()
                .approvalActivities(
                    List.of(HarnessApprovalActivityDTO.builder()
                                .user(EmbeddedUserDTO.builder().name(APPROVAL_NAME).email(APPROVAL_EMAIL).build())
                                .comments(APPROVAL_COMMENT)
                                .build()))
                .build())));

    Object resolvedObject = approvalFunctor.bind();
    assertThat(resolvedObject).isInstanceOf(HarnessApprovalOutcome.class);
    HarnessApprovalOutcome harnessApprovalOutcome = (HarnessApprovalOutcome) resolvedObject;
    assertThat(harnessApprovalOutcome.getApprovalActivities().get(0).getUser().getName()).isEqualTo(APPROVAL_NAME);
    assertThat(harnessApprovalOutcome.getApprovalActivities().get(0).getUser().getEmail()).isEqualTo(APPROVAL_EMAIL);
    assertThat(harnessApprovalOutcome.getApprovalActivities().get(0).getComments()).isEqualTo(APPROVAL_COMMENT);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testBindWithNotValidOutcome() {
    on(approvalFunctor).set("ambiance", ambiance);
    when(pmsOutcomeService.fetchOutcomesByStepTypeAndCategory(ambiance.getPlanExecutionId(),
             HarnessApprovalStep.STEP_TYPE.getType(), HarnessApprovalStep.STEP_TYPE.getStepCategory().name()))
        .thenReturn(List.of(PmsOutcomeMapper.convertOutcomeValueToJson(CustomApprovalOutcome.builder().build())));

    assertThatThrownBy(() -> approvalFunctor.bind())
        .hasMessage(
            "Found invalid outcome for approval expression, type: io.harness.steps.approval.step.custom.CustomApprovalOutcome")
        .isInstanceOf(EngineFunctorException.class);
  }
}
