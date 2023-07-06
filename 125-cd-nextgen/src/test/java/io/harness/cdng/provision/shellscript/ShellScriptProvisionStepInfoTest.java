/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.shellscript;

import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.shellscript.HarnessFileStoreSource;
import io.harness.steps.shellscript.ShellScriptInlineSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.StringNGVariable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class ShellScriptProvisionStepInfoTest extends CategoryTest {
  private final String FILE_PATH = "source.spec.file";
  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetSpecParameters() {
    ParameterField<String> value = ParameterField.<String>builder().value("value").build();
    List<NGVariable> variables = Arrays.asList(StringNGVariable.builder().name("key").value(value).build());
    ShellScriptInlineSource source =
        ShellScriptInlineSource.builder().script(ParameterField.<String>builder().value("test").build()).build();
    ShellScriptProvisionStepInfo shellScriptProvisionStepInfo =
        ShellScriptProvisionStepInfo.infoBuilder()
            .environmentVariables(variables)
            .source(ShellScriptSourceWrapper.builder().type("inline").spec(source).build())
            .build();

    SpecParameters specParameters = shellScriptProvisionStepInfo.getSpecParameters();

    ShellScriptProvisionStepParameters stepParams = (ShellScriptProvisionStepParameters) specParameters;
    assertThat(stepParams.getSource().getSpec()).isEqualTo(source);
    assertThat(stepParams.getEnvironmentVariables().get("key")).isEqualTo(value);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    ShellScriptProvisionStepInfo shellScriptProvisionStepInfo = new ShellScriptProvisionStepInfo();
    String response = shellScriptProvisionStepInfo.getFacilitatorType();
    assertThat(response).isEqualTo("TASK");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetStepType() {
    ShellScriptProvisionStepInfo shellScriptProvisionStepInfo = new ShellScriptProvisionStepInfo();
    StepType response = shellScriptProvisionStepInfo.getStepType();
    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ExecutionNodeType.SHELL_SCRIPT_PROVISION.getYamlType());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExtractRefs() {
    ShellScriptProvisionStepInfo shellScriptProvisionStepInfo =
        ShellScriptProvisionStepInfo.infoBuilder()
            .source(
                ShellScriptSourceWrapper.builder()
                    .type("inline")
                    .spec(HarnessFileStoreSource.builder().file(ParameterField.createValueField("/script.sh")).build())
                    .build())
            .build();
    Map<String, ParameterField<List<String>>> fileMap;
    fileMap = shellScriptProvisionStepInfo.extractFileRefs();
    assertThat(fileMap.get(FILE_PATH).getValue().size()).isEqualTo(1);
    assertThat(fileMap.get(FILE_PATH).getValue().get(0)).isEqualTo("/script.sh");
  }
}
