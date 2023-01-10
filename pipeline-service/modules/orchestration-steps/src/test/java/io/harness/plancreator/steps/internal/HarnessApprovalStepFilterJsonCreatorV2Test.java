/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.exception.runtime.InvalidYamlRuntimeException;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.approval.step.harness.HarnessApprovalStepInfo;
import io.harness.steps.approval.step.harness.HarnessApprovalStepNode;
import io.harness.steps.approval.step.harness.beans.Approvers;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class HarnessApprovalStepFilterJsonCreatorV2Test extends CategoryTest {
  private HarnessApprovalStepFilterJsonCreatorV2 creator = new HarnessApprovalStepFilterJsonCreatorV2();

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSupportedStepTypes() {
    Set<String> supportedTypes = creator.getSupportedStepTypes();
    assertThat(supportedTypes.size()).isEqualTo(1);
    assertThat(supportedTypes.contains(StepSpecTypeConstants.HARNESS_APPROVAL)).isTrue();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testHarnessApprovalFilterJsonWithAccLevelContext() {
    FilterCreationContext context = FilterCreationContext.builder()
                                        .currentField(new YamlField("harnessApproval", new YamlNode(null)))
                                        .setupMetadata(SetupMetadata.newBuilder().setAccountId("accId").build())
                                        .build();
    HarnessApprovalStepNode harnessApprovalStepNode = new HarnessApprovalStepNode();

    harnessApprovalStepNode.setHarnessApprovalStepInfo(
        HarnessApprovalStepInfo.builder()
            .approvers(Approvers.builder()
                           .minimumCount(ParameterField.createValueField(1))
                           .disallowPipelineExecutor(ParameterField.createValueField(false))
                           .userGroups(ParameterField.<List<String>>builder()
                                           .value(Arrays.asList(
                                               "projLevel", "org.orgLevel", "account.acccLevel", "invalid.xxx"))
                                           .build())
                           .build())
            .build());
    Assertions.assertThatThrownBy(() -> creator.handleNode(context, harnessApprovalStepNode))
        .isInstanceOf(InvalidYamlRuntimeException.class)
        .hasMessageContaining(
            "User groups [projLevel, org.orgLevel, invalid.xxx] provided for step  are either in invalid format or belong to scope higher than the current scope. Please correct them & try again.");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testHarnessApprovalFilterJsonWithOrgLevelContext() {
    FilterCreationContext context =
        FilterCreationContext.builder()
            .currentField(new YamlField("harnessApproval", new YamlNode(null)))
            .setupMetadata(SetupMetadata.newBuilder().setAccountId("accId").setOrgId("orgId").build())
            .build();
    HarnessApprovalStepNode harnessApprovalStepNode = new HarnessApprovalStepNode();

    harnessApprovalStepNode.setHarnessApprovalStepInfo(
        HarnessApprovalStepInfo.builder()
            .approvers(Approvers.builder()
                           .minimumCount(ParameterField.createValueField(1))
                           .disallowPipelineExecutor(ParameterField.createValueField(false))
                           .userGroups(ParameterField.<List<String>>builder()
                                           .value(Arrays.asList(
                                               "projLevel", "org.orgLevel", "account.acccLevel", "invalid.xxx"))
                                           .build())
                           .build())
            .build());
    Assertions.assertThatThrownBy(() -> creator.handleNode(context, harnessApprovalStepNode))
        .isInstanceOf(InvalidYamlRuntimeException.class)
        .hasMessageContaining(
            "User groups [projLevel, invalid.xxx] provided for step  are either in invalid format or belong to scope higher than the current scope. Please correct them & try again.");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testHarnessApprovalFilterJsonWithProjectLevelContext() {
    FilterCreationContext context =
        FilterCreationContext.builder()
            .currentField(new YamlField("harnessApproval", new YamlNode(null)))
            .setupMetadata(
                SetupMetadata.newBuilder().setAccountId("accId").setOrgId("orgId").setProjectId("projId").build())
            .build();
    HarnessApprovalStepNode harnessApprovalStepNode = new HarnessApprovalStepNode();

    harnessApprovalStepNode.setHarnessApprovalStepInfo(
        HarnessApprovalStepInfo.builder()
            .approvers(Approvers.builder()
                           .minimumCount(ParameterField.createValueField(1))
                           .disallowPipelineExecutor(ParameterField.createValueField(false))
                           .userGroups(ParameterField.<List<String>>builder()
                                           .value(Arrays.asList(
                                               "projLevel", "org.orgLevel", "account.acccLevel", "invalid.xxx"))
                                           .build())
                           .build())
            .build());
    Assertions.assertThatThrownBy(() -> creator.handleNode(context, harnessApprovalStepNode))
        .isInstanceOf(InvalidYamlRuntimeException.class)
        .hasMessageContaining(
            "User groups [invalid.xxx] provided for step  are either in invalid format or belong to scope higher than the current scope. Please correct them & try again.");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testHarnessApprovalFilterJsonWithProjectLevelContextAndRunTimeInput() {
    FilterCreationContext context =
        FilterCreationContext.builder()
            .currentField(new YamlField("harnessApproval", new YamlNode(null)))
            .setupMetadata(
                SetupMetadata.newBuilder().setAccountId("accId").setOrgId("orgId").setProjectId("projId").build())
            .build();
    HarnessApprovalStepNode harnessApprovalStepNode = new HarnessApprovalStepNode();

    harnessApprovalStepNode.setHarnessApprovalStepInfo(
        HarnessApprovalStepInfo.builder()
            .approvers(Approvers.builder()
                           .minimumCount(ParameterField.createExpressionField(true, "<+input>", null, true))
                           .disallowPipelineExecutor(ParameterField.createValueField(false))
                           .userGroups(ParameterField.createExpressionField(true, "<+input>", null, true))
                           .build())
            .build());
    creator.handleNode(context, harnessApprovalStepNode);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testHarnessApprovalFilterJsonWithProjectLevelContextAnExpressionTimeInput() {
    FilterCreationContext context =
        FilterCreationContext.builder()
            .currentField(new YamlField("harnessApproval", new YamlNode(null)))
            .setupMetadata(
                SetupMetadata.newBuilder().setAccountId("accId").setOrgId("orgId").setProjectId("projId").build())
            .build();
    HarnessApprovalStepNode harnessApprovalStepNode = new HarnessApprovalStepNode();

    harnessApprovalStepNode.setHarnessApprovalStepInfo(
        HarnessApprovalStepInfo.builder()
            .approvers(
                Approvers.builder()
                    .minimumCount(ParameterField.createExpressionField(true, "<+input>", null, true))
                    .disallowPipelineExecutor(ParameterField.createValueField(false))
                    .userGroups(ParameterField.<List<String>>builder()
                                    .value(Arrays.asList("projLevel", "org.orgLevel", "account.acccLevel", "<+b>"))
                                    .build())
                    .build())
            .build());
    creator.handleNode(context, harnessApprovalStepNode);
  }
}
