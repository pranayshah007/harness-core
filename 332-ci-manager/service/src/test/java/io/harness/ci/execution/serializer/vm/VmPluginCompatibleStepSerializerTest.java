/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.serializer.vm;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.RUTVIJ_MEHTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.stepinfo.DockerStepInfo;
import io.harness.beans.steps.stepinfo.ECRStepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.PluginSettingUtils;
import io.harness.ci.config.CIDockerLayerCachingConfig;
import io.harness.ci.execution.CIDockerLayerCachingConfigService;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.serializer.vm.VmPluginCompatibleStepSerializer;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Set;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CI)
public class VmPluginCompatibleStepSerializerTest {
  @Mock private CIFeatureFlagService ciFeatureFlagService;
  @Mock private PluginSettingUtils pluginSettingUtils;
  @Mock private CIDockerLayerCachingConfigService dockerLayerCachingConfigService;
  @Mock private CIExecutionConfigService ciExecutionConfigService;
  @InjectMocks private VmPluginCompatibleStepSerializer vmPluginStepSerializer;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putAllSetupAbstractions(Maps.of(
            "accountId", "accountId", "projectIdentifier", "projectIdentfier", "orgIdentifier", "orgIdentifier"))
        .build();
  }

  private DockerStepInfo getDockerStepInfo() {
    return DockerStepInfo.builder()
        .repo(ParameterField.createValueField("harness"))
        .tags(ParameterField.createValueField(Arrays.asList("tag1", "tag2")))
        .dockerfile(ParameterField.createValueField("Dockerfile"))
        .context(ParameterField.createValueField("context"))
        .target(ParameterField.createValueField("target"))
        .build();
  }

  private ECRStepInfo getEcrStepInfo() {
    return ECRStepInfo.builder()
        .imageName(ParameterField.createValueField("harness"))
        .tags(ParameterField.createValueField(Arrays.asList("tag1", "tag2")))
        .dockerfile(ParameterField.createValueField("Dockerfile"))
        .context(ParameterField.createValueField("context"))
        .target(ParameterField.createValueField("target"))
        .build();
  }

  private CIDockerLayerCachingConfig getDlcConfig() {
    return CIDockerLayerCachingConfig.builder()
        .endpoint("endpoint")
        .bucket("bucket")
        .accessKey("access_key")
        .secretKey("secret_key")
        .region("region")
        .build();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testPluginStepSerializerDockerDlcEnabled() {
    String accountId = "accountId";
    Ambiance ambiance = getAmbiance();
    DockerStepInfo dockerStepInfo = getDockerStepInfo();
    StageInfraDetails stageInfraDetails = () -> StageInfraDetails.Type.DLITE_VM;
    CIDockerLayerCachingConfig config = getDlcConfig();

    when(ciFeatureFlagService.isEnabled(FeatureName.CI_HOSTED_CONTAINERLESS_OOTB_STEP_ENABLED, accountId))
        .thenReturn(true);
    when(ciExecutionConfigService.getContainerlessPluginNameForVM(any(), any(PluginCompatibleStep.class)))
        .thenReturn("pluginName");
    when(pluginSettingUtils.dlcSetupRequired(dockerStepInfo)).thenReturn(true);
    when(dockerLayerCachingConfigService.getDockerLayerCachingConfig(any())).thenReturn(config);

    Set<String> secretList =
        vmPluginStepSerializer.preProcessStep(ambiance, dockerStepInfo, stageInfraDetails, "identifier");
    assertThat(secretList)
        .contains("endpoint")
        .contains("bucket")
        .contains("access_key")
        .contains("secret_key")
        .contains("region");
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testPluginStepSerializerDockerDlcDisabled() {
    String accountId = "accountId";
    Ambiance ambiance = getAmbiance();
    DockerStepInfo dockerStepInfo = getDockerStepInfo();
    StageInfraDetails stageInfraDetails = () -> StageInfraDetails.Type.DLITE_VM;

    when(ciFeatureFlagService.isEnabled(FeatureName.CI_HOSTED_CONTAINERLESS_OOTB_STEP_ENABLED, accountId))
        .thenReturn(true);
    when(ciExecutionConfigService.getContainerlessPluginNameForVM(any(), any(PluginCompatibleStep.class)))
        .thenReturn("pluginName");
    when(pluginSettingUtils.dlcSetupRequired(dockerStepInfo)).thenReturn(false);

    Set<String> secretList =
        vmPluginStepSerializer.preProcessStep(ambiance, dockerStepInfo, stageInfraDetails, "identifier");
    assertThat(secretList.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testPluginStepSerializerDockerConfigNull() {
    String accountId = "accountId";
    Ambiance ambiance = getAmbiance();
    DockerStepInfo dockerStepInfo = getDockerStepInfo();
    StageInfraDetails stageInfraDetails = () -> StageInfraDetails.Type.DLITE_VM;

    when(ciFeatureFlagService.isEnabled(FeatureName.CI_HOSTED_CONTAINERLESS_OOTB_STEP_ENABLED, accountId))
        .thenReturn(true);
    when(ciExecutionConfigService.getContainerlessPluginNameForVM(any(), any(PluginCompatibleStep.class)))
        .thenReturn("pluginName");
    when(pluginSettingUtils.dlcSetupRequired(dockerStepInfo)).thenReturn(true);
    when(dockerLayerCachingConfigService.getDockerLayerCachingConfig(any())).thenReturn(null);

    Set<String> secretList =
        vmPluginStepSerializer.preProcessStep(ambiance, dockerStepInfo, stageInfraDetails, "identifier");
    assertThat(secretList.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testPluginStepSerializerDockerDlcEnabledEcr() {
    String accountId = "accountId";
    Ambiance ambiance = getAmbiance();
    ECRStepInfo ecrStepInfo = getEcrStepInfo();
    StageInfraDetails stageInfraDetails = () -> StageInfraDetails.Type.DLITE_VM;
    CIDockerLayerCachingConfig config = getDlcConfig();

    when(ciFeatureFlagService.isEnabled(FeatureName.CI_HOSTED_CONTAINERLESS_OOTB_STEP_ENABLED, accountId))
        .thenReturn(true);
    when(ciExecutionConfigService.getContainerlessPluginNameForVM(any(), any(PluginCompatibleStep.class)))
        .thenReturn("pluginName");
    when(pluginSettingUtils.dlcSetupRequired(ecrStepInfo)).thenReturn(true);
    when(dockerLayerCachingConfigService.getDockerLayerCachingConfig(any())).thenReturn(config);

    Set<String> secretList =
        vmPluginStepSerializer.preProcessStep(ambiance, ecrStepInfo, stageInfraDetails, "identifier");
    assertThat(secretList)
        .contains("endpoint")
        .contains("bucket")
        .contains("access_key")
        .contains("secret_key")
        .contains("region");
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testPluginStepSerializerDockerDlcDisabledEcr() {
    String accountId = "accountId";
    Ambiance ambiance = getAmbiance();
    ECRStepInfo ecrStepInfo = getEcrStepInfo();
    StageInfraDetails stageInfraDetails = () -> StageInfraDetails.Type.DLITE_VM;

    when(ciFeatureFlagService.isEnabled(FeatureName.CI_HOSTED_CONTAINERLESS_OOTB_STEP_ENABLED, accountId))
        .thenReturn(true);
    when(ciExecutionConfigService.getContainerlessPluginNameForVM(any(), any(PluginCompatibleStep.class)))
        .thenReturn("pluginName");
    when(pluginSettingUtils.dlcSetupRequired(ecrStepInfo)).thenReturn(false);

    Set<String> secretList =
        vmPluginStepSerializer.preProcessStep(ambiance, ecrStepInfo, stageInfraDetails, "identifier");
    assertThat(secretList.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testPluginStepSerializerDockerConfigNullEcr() {
    String accountId = "accountId";
    Ambiance ambiance = getAmbiance();
    ECRStepInfo ecrStepInfo = getEcrStepInfo();
    StageInfraDetails stageInfraDetails = () -> StageInfraDetails.Type.DLITE_VM;

    when(ciFeatureFlagService.isEnabled(FeatureName.CI_HOSTED_CONTAINERLESS_OOTB_STEP_ENABLED, accountId))
        .thenReturn(true);
    when(ciExecutionConfigService.getContainerlessPluginNameForVM(any(), any(PluginCompatibleStep.class)))
        .thenReturn("pluginName");
    when(pluginSettingUtils.dlcSetupRequired(ecrStepInfo)).thenReturn(true);
    when(dockerLayerCachingConfigService.getDockerLayerCachingConfig(any())).thenReturn(null);

    Set<String> secretList =
        vmPluginStepSerializer.preProcessStep(ambiance, ecrStepInfo, stageInfraDetails, "identifier");
    assertThat(secretList.size()).isEqualTo(0);
  }
}
