/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.SAINATH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.category.element.UnitTests;
import io.harness.cdng.aws.sam.beans.AwsSamValuesYamlDataOutcome;
import io.harness.cdng.infra.beans.AwsSamInfrastructureOutcome;
import io.harness.cdng.manifest.yaml.AwsSamDirectoryManifestOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.delegate.beans.instancesync.info.AwsSamServerInstanceInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plugin.ContainerStepExecutionResponseHelper;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class AwsSamStepHelperTest extends CategoryTest {
  @Mock private OutcomeService outcomeService;

  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;

  @Mock private ContainerStepExecutionResponseHelper containerStepExecutionResponseHelper;

  @InjectMocks @Spy AwsSamStepHelper awsSamStepHelper;

  protected final Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "accountid").build();

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testGetInfrastructureOutcome() {
    Ambiance ambiance = Ambiance.newBuilder().build();

    AwsSamInfrastructureOutcome awsSamInfrastructureOutcome = AwsSamInfrastructureOutcome.builder().build();
    doReturn(awsSamInfrastructureOutcome).when(outcomeService).resolve(any(), any());
    assertThat(awsSamStepHelper.getInfrastructureOutcome(ambiance)).isEqualTo(awsSamInfrastructureOutcome);
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testUpdateServerInstanceInfoList() {
    AwsSamInfrastructureOutcome awsSamInfrastructureOutcome =
        AwsSamInfrastructureOutcome.builder().infrastructureKey("infrastructureKey").region("region").build();

    AwsSamServerInstanceInfo awsSamServerInstanceInfo = AwsSamServerInstanceInfo.builder().build();

    awsSamStepHelper.updateServerInstanceInfoList(Arrays.asList(awsSamServerInstanceInfo), awsSamInfrastructureOutcome);

    assertThat(awsSamServerInstanceInfo.getRegion()).isEqualTo(awsSamInfrastructureOutcome.getRegion());
    assertThat(awsSamServerInstanceInfo.getInfraStructureKey())
        .isEqualTo(awsSamInfrastructureOutcome.getInfrastructureKey());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testVerifyPluginImageIsProvider() {
    awsSamStepHelper.verifyPluginImageIsProvider(null);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testVerifyPluginImageIsProviderWhenNull() {
    awsSamStepHelper.verifyPluginImageIsProvider(ParameterField.ofNull());
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testPutValuesYamlEnvVars() {
    AwsSamBuildStepParameters stepParameters =
        AwsSamBuildStepParameters.infoBuilder().image(ParameterField.<String>builder().value("sdaf").build()).build();
    Map<String, String> envVarMap = new HashMap<>();

    Mockito.mockStatic(RefObjectUtils.class);
    when(RefObjectUtils.getSweepingOutputRefObject(any())).thenReturn(RefObject.newBuilder().build());

    String valuesYamlContent = "content";
    String valuesYamlPath = "path";
    AwsSamValuesYamlDataOutcome awsSamValuesYamlDataOutcome = AwsSamValuesYamlDataOutcome.builder()
                                                                  .valuesYamlContent(valuesYamlContent)
                                                                  .valuesYamlPath(valuesYamlPath)
                                                                  .build();

    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(awsSamValuesYamlDataOutcome).build());

    awsSamStepHelper.putValuesYamlEnvVars(ambiance, stepParameters, envVarMap);
    assertThat(envVarMap.containsKey("PLUGIN_VALUES_YAML_CONTENT")).isTrue();
    assertThat(envVarMap.get("PLUGIN_VALUES_YAML_CONTENT")).isEqualTo(valuesYamlContent);
    assertThat(envVarMap.containsKey("PLUGIN_VALUES_YAML_FILE_PATH")).isTrue();
    assertThat(envVarMap.get("PLUGIN_VALUES_YAML_FILE_PATH")).isEqualTo(valuesYamlPath);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testPutK8sServiceAccountEnvVars() {
    K8sDirectInfraYaml k8sDirectInfraYaml =
        K8sDirectInfraYaml.builder()
            .spec(K8sDirectInfraYamlSpec.builder()
                      .serviceAccountName(ParameterField.createValueField("serviceAccount"))
                      .build())
            .build();
    K8StageInfraDetails k8StageInfraDetails = K8StageInfraDetails.builder().infrastructure(k8sDirectInfraYaml).build();

    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(k8StageInfraDetails).build());
    Map<String, String> samDeployEnvironmentVariablesMap = new HashMap<>();
    awsSamStepHelper.putK8sServiceAccountEnvVars(ambiance, samDeployEnvironmentVariablesMap);
    assertThat(samDeployEnvironmentVariablesMap.containsKey("PLUGIN_USE_IRSA")).isTrue();
    assertThat(samDeployEnvironmentVariablesMap.get("PLUGIN_USE_IRSA")).isEqualTo("true");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetAwsSamDirectoryManifestOutcome() {
    List<ManifestOutcome> manifestOutcomeList = new ArrayList<>();
    AwsSamDirectoryManifestOutcome awsSamDirectoryManifestOutcome = AwsSamDirectoryManifestOutcome.builder().build();
    manifestOutcomeList.add(awsSamDirectoryManifestOutcome);
    ManifestOutcome manifestOutcome = awsSamStepHelper.getAwsSamDirectoryManifestOutcome(manifestOutcomeList);
    assertThat(manifestOutcome).isEqualTo(awsSamDirectoryManifestOutcome);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetAwsSamDirectoryManifestOutcomeWhenEmpty() {
    List<ManifestOutcome> manifestOutcomeList = new ArrayList<>();
    awsSamStepHelper.getAwsSamDirectoryManifestOutcome(manifestOutcomeList);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetAwsSamDirectoryManifestOutcomeWhenMoreThanOne() {
    List<ManifestOutcome> manifestOutcomeList = new ArrayList<>();
    AwsSamDirectoryManifestOutcome awsSamDirectoryManifestOutcome = AwsSamDirectoryManifestOutcome.builder().build();
    AwsSamDirectoryManifestOutcome awsSamDirectoryManifestOutcome1 = AwsSamDirectoryManifestOutcome.builder().build();
    manifestOutcomeList.add(awsSamDirectoryManifestOutcome);
    manifestOutcomeList.add(awsSamDirectoryManifestOutcome1);
    awsSamStepHelper.getAwsSamDirectoryManifestOutcome(manifestOutcomeList);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetValuesManifestOutcome() {
    List<ManifestOutcome> manifestOutcomeList = new ArrayList<>();
    ValuesManifestOutcome valuesManifestOutcome = ValuesManifestOutcome.builder().build();
    manifestOutcomeList.add(valuesManifestOutcome);
    ManifestOutcome manifestOutcome = awsSamStepHelper.getAwsSamValuesManifestOutcome(manifestOutcomeList);
    assertThat(manifestOutcome).isEqualTo(valuesManifestOutcome);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetValuesManifestOutcomeWhenEmpty() {
    List<ManifestOutcome> manifestOutcomeList = new ArrayList<>();
    ManifestOutcome manifestOutcome = awsSamStepHelper.getAwsSamValuesManifestOutcome(manifestOutcomeList);
    assertThat(manifestOutcome).isNull();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetValuesManifestOutcomeWhenMoreThanOne() {
    List<ManifestOutcome> manifestOutcomeList = new ArrayList<>();
    ValuesManifestOutcome valuesManifestOutcome = ValuesManifestOutcome.builder().build();
    ValuesManifestOutcome valuesManifestOutcome1 = ValuesManifestOutcome.builder().build();
    manifestOutcomeList.add(valuesManifestOutcome);
    manifestOutcomeList.add(valuesManifestOutcome1);
    awsSamStepHelper.getAwsSamValuesManifestOutcome(manifestOutcomeList);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetValuesPathFromValuesManifestOutcome() {
    String identifier = "iden";
    ValuesManifestOutcome valuesManifestOutcome = mock(ValuesManifestOutcome.class);
    GitStoreConfig gitStoreConfig = mock(GitStoreConfig.class);
    doReturn(identifier).when(valuesManifestOutcome).getIdentifier();
    doReturn(gitStoreConfig).when(valuesManifestOutcome).getStore();
    String path = "values.yml";
    doReturn(ParameterField.createValueField(Arrays.asList(path))).when(gitStoreConfig).getPaths();
    String result = awsSamStepHelper.getValuesPathFromValuesManifestOutcome(valuesManifestOutcome);
    assertThat(result).isEqualTo("/harness/" + identifier + "/" + path);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetSamDirectoryPathFromAwsSamDirectoryManifestOutcome() {
    String identifier = "iden";
    AwsSamDirectoryManifestOutcome awsSamDirectoryManifestOutcome = mock(AwsSamDirectoryManifestOutcome.class);
    GitStoreConfig gitStoreConfig = mock(GitStoreConfig.class);
    doReturn(identifier).when(awsSamDirectoryManifestOutcome).getIdentifier();
    doReturn(gitStoreConfig).when(awsSamDirectoryManifestOutcome).getStore();
    String path = "values.yml";
    doReturn(ParameterField.createValueField(Arrays.asList(path))).when(gitStoreConfig).getPaths();
    String result =
        awsSamStepHelper.getSamDirectoryPathFromAwsSamDirectoryManifestOutcome(awsSamDirectoryManifestOutcome);
    assertThat(result).isEqualTo(identifier + "/" + path);
  }
}
