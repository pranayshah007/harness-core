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
public class TerragruntExecutionDataTest {
  @InjectMocks TerragruntExecutionData terragruntExecutionData;
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
    terragruntExecutionData = new TerragruntExecutionData();

    terragruntExecutionData.setTerragruntModuleConfig(terragruntModuleConfig);
    terragruntExecutionData.setWorkspace(ParameterField.createValueField("workspace"));
    terragruntExecutionData.setTerragruntBackendConfig(
        TerragruntBackendConfig.builder().spec(new InlineTerragruntBackendConfigSpec()).build());
    terragruntExecutionData.setTargets(ParameterField.createValueField(List.of("target")));
    terragruntExecutionData.setTerragruntVarFiles(List.of(new TerragruntVarFileWrapper()));
    terragruntExecutionData.setEnvironmentVariables(
        List.of(StringNGVariable.builder().name("var1").value(ParameterField.createValueField("value")).build()));
    terragruntExecutionData.setTerragruntConfigFilesWrapper(terragruntConfigFilesWrapper);

    TerragruntExecutionDataParameters terragruntExecutionDataParameters = terragruntExecutionData.toStepParameters();

    assertThat(terragruntExecutionDataParameters).isNotNull();
    assertThat(terragruntExecutionDataParameters.moduleConfig).isNotNull();
    assertThat(terragruntExecutionDataParameters.moduleConfig.path.getValue()).isEqualTo("test-path");
    assertThat(terragruntExecutionDataParameters.terragruntModuleConfig).isNotNull();
    assertThat(terragruntExecutionDataParameters.terragruntModuleConfig.path.getValue()).isEqualTo("test-path");
    assertThat(terragruntExecutionDataParameters.workspace.getValue()).isEqualTo("workspace");
    assertThat(terragruntExecutionDataParameters.targets.getValue().size()).isEqualTo(1);
    assertThat(terragruntExecutionDataParameters.targets.getValue().get(0)).isEqualTo("target");
  }
}
