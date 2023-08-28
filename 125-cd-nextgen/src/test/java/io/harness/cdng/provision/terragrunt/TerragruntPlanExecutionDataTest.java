/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terragrunt;

import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.manifest.yaml.storeConfig.moduleSource.ModuleSource;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.TaskRequestsUtils;
import io.harness.yaml.core.variables.StringNGVariable;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.core.classloader.annotations.PrepareForTest;

@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({TaskRequestsUtils.class})
@OwnedBy(HarnessTeam.CDP)
public class TerragruntPlanExecutionDataTest {
  @InjectMocks TerragruntPlanExecutionData terragruntPlanExecutionData;

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testToStepParameters() {
    TerragruntModuleConfig terragruntModuleConfig = new TerragruntModuleConfig();
    terragruntModuleConfig.terragruntRunType = TerragruntRunType.RUN_MODULE;
    terragruntModuleConfig.path = ParameterField.createValueField("test-path");
    TerragruntConfigFilesWrapper terragruntConfigFilesWrapper = new TerragruntConfigFilesWrapper();
    terragruntConfigFilesWrapper.store = StoreConfigWrapper.builder()
                                             .type(StoreConfigType.GITHUB)
                                             .spec(GithubStore.builder()
                                                       .branch(ParameterField.createValueField("master"))
                                                       .folderPath(ParameterField.createValueField("folderPath"))
                                                       .paths(ParameterField.createValueField(List.of("path")))
                                                       .gitFetchType(FetchType.BRANCH)
                                                       .build())
                                             .build();
    terragruntConfigFilesWrapper.moduleSource =
        ModuleSource.builder().useConnectorCredentials(ParameterField.createValueField(true)).build();
    terragruntPlanExecutionData =
        TerragruntPlanExecutionData.builder()
            .terragruntModuleConfig(terragruntModuleConfig)
            .workspace(ParameterField.createValueField("workspace"))
            .terragruntBackendConfig(
                TerragruntBackendConfig.builder().spec(new InlineTerragruntBackendConfigSpec()).build())
            .targets(ParameterField.createValueField(List.of("target")))
            .terragruntVarFiles(List.of(new TerragruntVarFileWrapper()))
            .environmentVariables(List.of(
                StringNGVariable.builder().name("var1").value(ParameterField.createValueField("value")).build()))
            .terragruntConfigFilesWrapper(terragruntConfigFilesWrapper)
            .command(TerragruntPlanCommand.APPLY)
            .secretManagerRef(ParameterField.createValueField("secretManagerRef"))
            .exportTerragruntPlanJson(ParameterField.createValueField(true))
            .build();

    TerragruntPlanExecutionDataParameters terragruntPlanExecutionDataParameters =
        terragruntPlanExecutionData.toStepParameters();

    assertThat(terragruntPlanExecutionDataParameters).isNotNull();
    assertThat(terragruntPlanExecutionDataParameters.moduleConfig).isNotNull();
    assertThat(terragruntPlanExecutionDataParameters.moduleConfig.path.getValue()).isEqualTo("test-path");
    assertThat(terragruntPlanExecutionDataParameters.terragruntModuleConfig).isNotNull();
    assertThat(terragruntPlanExecutionDataParameters.terragruntModuleConfig.path.getValue()).isEqualTo("test-path");
    assertThat(terragruntPlanExecutionDataParameters.workspace.getValue()).isEqualTo("workspace");
    assertThat(terragruntPlanExecutionDataParameters.command).isEqualTo(TerragruntPlanCommand.APPLY);
    assertThat(terragruntPlanExecutionDataParameters.targets.getValue().size()).isEqualTo(1);
    assertThat(terragruntPlanExecutionDataParameters.targets.getValue().get(0)).isEqualTo("target");
  }
}
