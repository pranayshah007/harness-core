/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.VINIT_KUMAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.InputSetValidatorType;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.validation.InputSetValidator;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
public class ApproversDTOTest extends CategoryTest {
  InputSetValidator validator = new InputSetValidator(InputSetValidatorType.REGEX, "");

  private Approvers createValidApproversBean() {
    return Approvers.builder()
        .userGroups(ParameterField.<List<String>>builder().value(Collections.singletonList("userGroup")).build())
        .minimumCount(ParameterField.<Integer>builder().value(1).build())
        .disallowPipelineExecutor(ParameterField.<Boolean>builder().value(false).build())
        .build();
  }

  private Approvers createApproversBeanWithUnResolvedBooleanParameter() {
    return Approvers.builder()
        .userGroups(ParameterField.<List<String>>builder().value(Collections.singletonList("userGroup")).build())
        .minimumCount(ParameterField.<Integer>builder().value(1).build())
        .disallowPipelineExecutor(ParameterField.createExpressionField(false, null, validator, false))
        .build();
  }

  private Approvers createApproversBeanWithUnResolvedUserGroups() {
    return Approvers.builder()
        .userGroups(ParameterField.createExpressionField(true, "<+pipleine.var.var1>", validator, false))
        .minimumCount(ParameterField.<Integer>builder().value(1).build())
        .disallowPipelineExecutor(ParameterField.<Boolean>builder().value(false).build())
        .build();
  }

  private Approvers createInValidApproversBean() {
    return Approvers.builder()
        .userGroups(ParameterField.<List<String>>builder().value(Collections.singletonList("userGroup")).build())
        .minimumCount(ParameterField.<Integer>builder().value(0).build())
        .disallowPipelineExecutor(ParameterField.<Boolean>builder().value(false).build())
        .build();
  }
  private Approvers createApprovweWithNULLUserGroup() {
    return Approvers.builder()
        .userGroups(ParameterField.<List<String>>builder().value(null).build())
        .minimumCount(ParameterField.<Integer>builder().value(1).build())
        .disallowPipelineExecutor(ParameterField.<Boolean>builder().value(false).build())
        .build();
  }
  private Approvers createApproveWithEmptyListUserGroup() {
    return Approvers.builder()
        .userGroups(ParameterField.<List<String>>builder().value(Collections.emptyList()).build())
        .minimumCount(ParameterField.<Integer>builder().value(1).build())
        .disallowPipelineExecutor(ParameterField.<Boolean>builder().value(false).build())
        .build();
  }
  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void fromValidApproversBeans() {
    assertThat(ApproversDTO.fromApprovers(createValidApproversBean())).isNotNull();
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void fromInValidApproversBeansWithUnresolvedUserGroups() {
    assertThatThrownBy(() -> ApproversDTO.fromApprovers(createApproversBeanWithUnResolvedUserGroups()))
        .isInstanceOf(InvalidRequestException.class);
  }
  @Test
  @Owner(developers = VINIT_KUMAR)
  @Category(UnitTests.class)
  public void fromApproversWithNullUserGroups() {
    assertThatThrownBy(() -> ApproversDTO.fromApprovers(createApprovweWithNULLUserGroup()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("At least 1 user group is required");
  }
  @Test
  @Owner(developers = VINIT_KUMAR)
  @Category(UnitTests.class)
  public void fromApproversWithEmptyUserGroupsList() {
    assertThatThrownBy(() -> ApproversDTO.fromApprovers(createApproveWithEmptyListUserGroup()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("At least 1 user group is required");
  }
  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void fromInValidApproversBeansWithInvalidMinCount() {
    assertThatThrownBy(() -> ApproversDTO.fromApprovers(createInValidApproversBean()))
        .isInstanceOf(InvalidRequestException.class);
  }
  @Test
  @Owner(developers = VINIT_KUMAR)
  @Category(UnitTests.class)
  public void fromApproversWithInvalidMinimumCountValue() {
    assertThatThrownBy(() -> ApproversDTO.fromApprovers(createInValidApproversBean()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Minimum count should be > 0");
  }
  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testFromApprovers_NullInput() {
    ApproversDTO dto = ApproversDTO.fromApprovers(null);
    assertThat(dto).isNull();
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testFromApproversWithUnResolvedBooleanParameter() {
    ApproversDTO dto = ApproversDTO.fromApprovers(createApproversBeanWithUnResolvedBooleanParameter());
    assertThat(dto).isNotNull();
  }
}
