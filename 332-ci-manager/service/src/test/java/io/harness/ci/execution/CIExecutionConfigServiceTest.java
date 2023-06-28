/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution;

import static io.harness.rule.OwnerRule.RUTVIJ_MEHTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.DockerStepInfo;
import io.harness.beans.steps.stepinfo.ECRStepInfo;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.PluginSettingUtils;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.CIStepConfig;
import io.harness.ci.config.ContainerlessPluginConfig;
import io.harness.ci.config.VmContainerlessStepConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CIExecutionConfigServiceTest {
  @Mock private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock private PluginSettingUtils pluginSettingUtils;
  @InjectMocks private CIExecutionConfigService ciExecutionConfigService;

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetContainerlessPluginNameForVMDockerBuildx() {
    CIStepConfig ciStepConfig =
        CIStepConfig.builder()
            .vmContainerlessStepConfig(
                VmContainerlessStepConfig.builder()
                    .dockerBuildxConfig(ContainerlessPluginConfig.builder().name("dockerBuildxConfig").build())
                    .build())
            .build();
    DockerStepInfo dockerStepInfo = DockerStepInfo.builder()
                                        .repo(ParameterField.createValueField("harness"))
                                        .tags(ParameterField.createValueField(Arrays.asList("tag1", "tag2")))
                                        .caching(ParameterField.createValueField(true))
                                        .build();
    when(ciExecutionServiceConfig.getStepConfig()).thenReturn(ciStepConfig);
    when(pluginSettingUtils.buildxRequired(dockerStepInfo)).thenReturn(true);
    String pluginName = ciExecutionConfigService.getContainerlessPluginNameForVM(CIStepInfoType.DOCKER, dockerStepInfo);
    assertThat(pluginName).isNotEmpty();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetContainerlessPluginNameForVMEcrBuildx() {
    CIStepConfig ciStepConfig =
        CIStepConfig.builder()
            .vmContainerlessStepConfig(
                VmContainerlessStepConfig.builder()
                    .dockerBuildxEcrConfig(ContainerlessPluginConfig.builder().name("dockerBuildxEcrConfig").build())
                    .build())
            .build();
    ECRStepInfo ecrStepInfo = ECRStepInfo.builder()
                                  .imageName(ParameterField.createValueField("harness"))
                                  .tags(ParameterField.createValueField(Arrays.asList("tag1", "tag2")))
                                  .caching(ParameterField.createValueField(true))
                                  .build();
    when(ciExecutionServiceConfig.getStepConfig()).thenReturn(ciStepConfig);
    when(pluginSettingUtils.buildxRequired(ecrStepInfo)).thenReturn(true);
    String pluginName = ciExecutionConfigService.getContainerlessPluginNameForVM(CIStepInfoType.ECR, ecrStepInfo);
    assertThat(pluginName).isNotEmpty();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetContainerlessPluginNameForVMDocker() {
    CIStepConfig ciStepConfig =
        CIStepConfig.builder()
            .vmContainerlessStepConfig(
                VmContainerlessStepConfig.builder()
                    .dockerBuildxConfig(ContainerlessPluginConfig.builder().name("dockerBuildxConfig").build())
                    .build())
            .build();
    DockerStepInfo dockerStepInfo = DockerStepInfo.builder()
                                        .repo(ParameterField.createValueField("harness"))
                                        .tags(ParameterField.createValueField(Arrays.asList("tag1", "tag2")))
                                        .caching(ParameterField.createValueField(true))
                                        .build();
    when(ciExecutionServiceConfig.getStepConfig()).thenReturn(ciStepConfig);
    when(pluginSettingUtils.buildxRequired(dockerStepInfo)).thenReturn(false);
    String pluginName = ciExecutionConfigService.getContainerlessPluginNameForVM(CIStepInfoType.DOCKER, dockerStepInfo);
    assertThat(pluginName).isNull();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetContainerlessPluginNameForVMEcr() {
    CIStepConfig ciStepConfig =
        CIStepConfig.builder()
            .vmContainerlessStepConfig(
                VmContainerlessStepConfig.builder()
                    .dockerBuildxEcrConfig(ContainerlessPluginConfig.builder().name("dockerBuildxEcrConfig").build())
                    .build())
            .build();
    ECRStepInfo ecrStepInfo = ECRStepInfo.builder()
                                  .imageName(ParameterField.createValueField("harness"))
                                  .tags(ParameterField.createValueField(Arrays.asList("tag1", "tag2")))
                                  .caching(ParameterField.createValueField(true))
                                  .build();
    when(ciExecutionServiceConfig.getStepConfig()).thenReturn(ciStepConfig);
    when(pluginSettingUtils.buildxRequired(ecrStepInfo)).thenReturn(false);
    String pluginName = ciExecutionConfigService.getContainerlessPluginNameForVM(CIStepInfoType.ECR, ecrStepInfo);
    assertThat(pluginName).isNull();
  }
}
