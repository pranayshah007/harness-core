/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.HEN;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SHUBHAM;

import static com.mongodb.assertions.Assertions.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.platform.Platform;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class VmInitializeUtilsTest extends CIExecutionTestBase {
  @InjectMocks private VmInitializeUtils vmInitializeUtils;

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void validateStageConfig() {
    IntegrationStageConfig integrationStageConfig = VmInitializeTaskHelper.getIntegrationStageConfig();
    String accountId = "test";

    vmInitializeUtils.validateStageConfig(integrationStageConfig, accountId);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testValidateStageConfigWithStepGroup() throws Exception {
    IntegrationStageConfig integrationStageConfig = VmInitializeTaskHelper.getIntegrationStageConfigWithStepGroup();
    String accountId = "test";

    vmInitializeUtils.validateStageConfig(integrationStageConfig, accountId);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testLinuxOS() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskHelper.getInitializeStepWithLinuxPoolName();
    OSType os = VmInitializeUtils.getOS(initializeStepInfo.getInfrastructure());

    assertThat(os).isEqualTo(OSType.Linux);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testInvalidOSArch() {
    ParameterField os = ParameterField.ofNull();
    os.setValue("invalidValue");
    ParameterField arch = ParameterField.ofNull();
    arch.setValue("invalidValue");
    HostedVmInfraYaml hostedVmInfraYaml =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(Platform.builder().os(os).arch(arch).build()))
                      .build())
            .build();
    assertThatThrownBy(() -> VmInitializeUtils.getOS(hostedVmInfraYaml))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Os type invalidValue is invalid, valid values are : [Linux, MacOS, Windows]");
    assertThatThrownBy(() -> VmInitializeUtils.getArchType(hostedVmInfraYaml))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Arch type invalidValue is invalid, valid values are : [Amd64, Arm64]");
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testMacOS() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskHelper.getInitializeStepWithMacPoolName();
    OSType os = VmInitializeUtils.getOS(initializeStepInfo.getInfrastructure());

    assertThat(os).isEqualTo(OSType.MacOS);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testWorkDir() {
    assertThat(vmInitializeUtils.getWorkDir(OSType.MacOS)).isEqualTo("/tmp/harness");
    assertThat(vmInitializeUtils.getWorkDir(OSType.Linux)).isEqualTo("/harness");
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testGetVolumeToMountPath() {
    ParameterField<List<String>> sharedPaths = ParameterField.createValueField(Arrays.asList("/shared1", "/shared2"));

    Map<String, String> expected = new HashMap<>();
    expected.put("harness", "/tmp/harness");
    expected.put("addon", "/tmp/addon");
    expected.put("shared-0", "/shared1");
    expected.put("shared-1", "/shared2");

    Map<String, String> volToMountPath = vmInitializeUtils.getVolumeToMountPath(sharedPaths, OSType.MacOS);
    assertThat(volToMountPath).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testGetVolumeToMountPathLinux() {
    ParameterField<List<String>> sharedPaths = ParameterField.createValueField(Arrays.asList("/shared1", "/shared2"));

    Map<String, String> expected = new HashMap<>();
    expected.put("harness", "/harness");
    expected.put("addon", "/addon");
    expected.put("shared-0", "/shared1");
    expected.put("shared-1", "/shared2");

    Map<String, String> volToMountPath = vmInitializeUtils.getVolumeToMountPath(sharedPaths, OSType.Linux);
    assertThat(volToMountPath).isEqualTo(expected);
  }

  @Test
  @Owner(developers = HEN)
  @Category(UnitTests.class)
  public void testDebugModeValidation() {
    Infrastructure hostedInfrastructure = VmInitializeTaskHelper.getHostedInfra(OSType.MacOS);
    Infrastructure vmInfrastructure = VmInitializeTaskHelper.getVMInfra(OSType.MacOS);

    Ambiance ambiance =
        Ambiance.newBuilder().setMetadata(ExecutionMetadata.newBuilder().setIsDebug(true).build()).build();

    try {
      vmInitializeUtils.validateDebug(hostedInfrastructure, ambiance);
      fail("Debug should not be supported with mac");
    } catch (Exception e) {
    }

    try {
      vmInitializeUtils.validateDebug(vmInfrastructure, ambiance);
      fail("Debug should not be supported with mac");
    } catch (Exception e) {
    }

    hostedInfrastructure = VmInitializeTaskHelper.getHostedInfra(OSType.Linux);
    vmInfrastructure = VmInitializeTaskHelper.getVMInfra(OSType.Linux);

    try {
      boolean result = vmInitializeUtils.validateDebug(hostedInfrastructure, ambiance);
      assertThat(result).isEqualTo(true);
    } catch (Exception e) {
      fail("Debug should be supported with Linux");
    }

    try {
      boolean result = vmInitializeUtils.validateDebug(vmInfrastructure, ambiance);
      assertThat(result).isEqualTo(true);
    } catch (Exception e) {
      fail("Debug should be supported with Linux");
    }
  }
}