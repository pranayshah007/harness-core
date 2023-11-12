/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.harness;

import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.exception.runtime.InvalidYamlRuntimeException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.harness.HarnessApprovalSpecParameters;
import io.harness.steps.approval.step.harness.HarnessApprovalUtils;
import io.harness.steps.approval.step.harness.beans.Approvers;
import io.harness.steps.approval.step.harness.beans.AutoApprovalAction;
import io.harness.steps.approval.step.harness.beans.AutoApprovalParams;
import io.harness.steps.approval.step.harness.beans.ScheduledDeadline;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HarnessApprovalUtilsTest {
  public static final String APPROVAL_MESSAGE = "Approval_Message";
  public static final String USER_GROUP = "User_Group";
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testExecuteAsync() {
    StepElementParameters parameters = getStepElementParameters();

    HarnessApprovalSpecParameters specParameters = (HarnessApprovalSpecParameters) parameters.getSpec();
    AutoApprovalParams autoApprovalParams =
        AutoApprovalParams.builder()
            .action(AutoApprovalAction.APPROVE)
            .scheduledDeadline(ScheduledDeadline.builder()
                                   .time(ParameterField.createValueField("2023-05-05 04:24 am"))
                                   .timeZone(ParameterField.createValueField("Asia/Kolkata"))
                                   .build())
            .comments(ParameterField.<String>builder().value("comments").build())
            .build();
    specParameters.setAutoApproval(autoApprovalParams);
    parameters.setSpec(specParameters);

    assertThatThrownBy(() -> HarnessApprovalUtils.validateTimestampForAutoApproval(specParameters))
        .isInstanceOf(InvalidYamlRuntimeException.class)
        .hasMessage(
            "Time given for auto approval in approval step %s should be greater than 15 minutes with respect to current time");
  }
  private StepElementParameters getStepElementParameters() {
    return StepElementParameters.builder()
        .identifier("_id")
        .type("HARNESS_APPROVAL")
        .spec(
            HarnessApprovalSpecParameters.builder()
                .approvalMessage(ParameterField.<String>builder().value(APPROVAL_MESSAGE).build())
                .includePipelineExecutionHistory(ParameterField.<Boolean>builder().value(false).build())
                .approvers(
                    Approvers.builder()
                        .userGroups(
                            ParameterField.<List<String>>builder().value(Collections.singletonList(USER_GROUP)).build())
                        .minimumCount(ParameterField.<Integer>builder().value(1).build())
                        .disallowPipelineExecutor(ParameterField.<Boolean>builder().value(false).build())
                        .build())
                .isAutoRejectEnabled(ParameterField.<Boolean>builder().value(false).build())
                .build())
        .build();
  }
}
