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
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.exception.EngineFunctorException;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.custom.entities.CustomApprovalInstance;
import io.harness.steps.approval.step.harness.HarnessApprovalOutcome;
import io.harness.steps.approval.step.harness.beans.ApproversDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivity;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;

import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class ApprovalFunctorTest extends CategoryTest {
  private static final String APPROVAL_NAME = "Admin";
  private static final String APPROVAL_EMAIL = "admin@harness.io";
  private static final String APPROVAL_COMMENT = "Approval comment";
  private static final String PLAN_EXECUTION_ID = "execution_id";
  @Mock private ApprovalInstanceService approvalInstanceService;
  @InjectMocks private ApprovalFunctor approvalFunctor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testBind() {
    on(approvalFunctor).set("planExecutionId", PLAN_EXECUTION_ID);
    when(approvalInstanceService.findLatestApprovalInstanceByPlanExecutionIdAndType(
             PLAN_EXECUTION_ID, ApprovalType.HARNESS_APPROVAL))
        .thenReturn(
            Optional.of(HarnessApprovalInstance.builder()
                            .approvalMessage(APPROVAL_COMMENT)
                            .approvalActivities(List.of(
                                HarnessApprovalActivity.builder()
                                    .comments(APPROVAL_COMMENT)
                                    .user(EmbeddedUser.builder().email(APPROVAL_EMAIL).name(APPROVAL_NAME).build())
                                    .build()))
                            .approvers(ApproversDTO.builder().userGroups(List.of(APPROVAL_NAME)).build())
                            .build()));

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
    on(approvalFunctor).set("planExecutionId", PLAN_EXECUTION_ID);
    when(approvalInstanceService.findLatestApprovalInstanceByPlanExecutionIdAndType(
             PLAN_EXECUTION_ID, ApprovalType.HARNESS_APPROVAL))
        .thenReturn(Optional.of(CustomApprovalInstance.builder().build()));

    assertThatThrownBy(() -> approvalFunctor.bind())
        .hasMessage(
            "Found invalid approval instance for approval expression, type: io.harness.steps.approval.step.custom.entities.CustomApprovalInstance")
        .isInstanceOf(EngineFunctorException.class);
  }
}
