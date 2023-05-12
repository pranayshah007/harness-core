/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;

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
import io.harness.steps.shellscript.ExecutionTarget;
import io.harness.steps.shellscript.ShellScriptStepInfo;
import io.harness.steps.shellscript.ShellScriptStepNode;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class ShellScriptStepFilterJsonCreatorV2Test extends CategoryTest {
  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testShellScriptFilterJson() {
    FilterCreationContext context = FilterCreationContext.builder()
                                        .currentField(new YamlField("script", new YamlNode(null)))
                                        .setupMetadata(SetupMetadata.newBuilder()
                                                           .setAccountId("accountId")
                                                           .setOrgId("orgId")
                                                           .setProjectId("projectId")
                                                           .build())
                                        .build();
    ShellScriptStepFilterJsonCreatorV2 creator = new ShellScriptStepFilterJsonCreatorV2();
    ShellScriptStepNode scriptStepNode = new ShellScriptStepNode();

    scriptStepNode.setShellScriptStepInfo(
        ShellScriptStepInfo.infoBuilder().onDelegate(ParameterField.createValueField(false)).build());
    Assertions.assertThatThrownBy(() -> creator.handleNode(context, scriptStepNode))
        .isInstanceOf(InvalidYamlRuntimeException.class)
        .hasMessageContaining("Execution target details cannot be null for step ");

    scriptStepNode.setShellScriptStepInfo(ShellScriptStepInfo.infoBuilder()
                                              .onDelegate(ParameterField.createValueField(false))
                                              .executionTarget(ExecutionTarget.builder().build())
                                              .build());
    Assertions.assertThatThrownBy(() -> creator.handleNode(context, scriptStepNode))
        .isInstanceOf(InvalidYamlRuntimeException.class)
        .hasMessageContaining("Execution target host details cannot be null for step ");

    scriptStepNode.setShellScriptStepInfo(
        ShellScriptStepInfo.infoBuilder()
            .onDelegate(ParameterField.createValueField(false))
            .executionTarget(ExecutionTarget.builder().host(ParameterField.createValueField("localhost")).build())
            .build());
    Assertions.assertThatThrownBy(() -> creator.handleNode(context, scriptStepNode))
        .isInstanceOf(InvalidYamlRuntimeException.class)
        .hasMessageContaining("Execution target ssh connection attribute details cannot be null for step ");

    scriptStepNode.setShellScriptStepInfo(
        ShellScriptStepInfo.infoBuilder()
            .onDelegate(ParameterField.createValueField(false))
            .executionTarget(ExecutionTarget.builder()
                                 .host(ParameterField.createExpressionField(true, "<+input>", null, true))
                                 .build())
            .build());
    Assertions.assertThatThrownBy(() -> creator.handleNode(context, scriptStepNode))
        .isInstanceOf(InvalidYamlRuntimeException.class)
        .hasMessageContaining("Execution target ssh connection attribute details cannot be null for step ");
  }
}
