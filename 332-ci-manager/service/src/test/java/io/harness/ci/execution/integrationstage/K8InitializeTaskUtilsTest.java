/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.ci.execution.integrationstage.K8InitializeTaskUtilsHelper.EMPTY_DIR_MOUNT_PATH;
import static io.harness.ci.execution.integrationstage.K8InitializeTaskUtilsHelper.HOST_DIR_MOUNT_PATH;
import static io.harness.ci.execution.integrationstage.K8InitializeTaskUtilsHelper.PVC_DIR_MOUNT_PATH;
import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;

import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.logserviceclient.CILogServiceUtils;
import io.harness.ci.tiserviceclient.TIServiceUtils;
import io.harness.delegate.beans.ci.pod.PodVolume;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.stoserviceclient.STOServiceUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class K8InitializeTaskUtilsTest extends CIExecutionTestBase {
  private K8InitializeTaskUtils k8InitializeTaskUtils;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock CILogServiceUtils logServiceUtils;
  @Mock TIServiceUtils tiServiceUtils;
  @Mock STOServiceUtils stoServiceUtils;
  @Mock private CIFeatureFlagService featureFlagService;
  @Mock private SecretUtils secretUtils;

  @Before
  public void setUp() {
    k8InitializeTaskUtils = new K8InitializeTaskUtils();

    on(k8InitializeTaskUtils).set("connectorUtils", connectorUtils);
    on(k8InitializeTaskUtils).set("secretUtils", secretUtils);
    on(k8InitializeTaskUtils).set("executionSweepingOutputResolver", executionSweepingOutputResolver);
    on(k8InitializeTaskUtils).set("logServiceUtils", logServiceUtils);
    on(k8InitializeTaskUtils).set("featureFlagService", featureFlagService);
    on(k8InitializeTaskUtils).set("tiServiceUtils", tiServiceUtils);
    on(k8InitializeTaskUtils).set("stoServiceUtils", stoServiceUtils);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void convertDirectK8Volumes() {
    K8sDirectInfraYaml k8sDirectInfraYaml = K8InitializeTaskUtilsHelper.getDirectK8InfrastructureWithVolume();

    List<PodVolume> expected = K8InitializeTaskUtilsHelper.getConvertedVolumes();
    List<PodVolume> actual = k8InitializeTaskUtils.convertDirectK8Volumes(k8sDirectInfraYaml);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getVolumeToMountPath() {
    List<PodVolume> podVolumes = K8InitializeTaskUtilsHelper.getConvertedVolumes();
    List<String> sharedPaths = Arrays.asList("/tmp/shared");
    Map<String, String> expected = new HashMap<>();
    expected.put("harness", "/harness");
    expected.put("addon", "/addon");
    expected.put("shared-0", "/tmp/shared");

    expected.put("volume-0", EMPTY_DIR_MOUNT_PATH);
    expected.put("volume-1", HOST_DIR_MOUNT_PATH);
    expected.put("volume-2", PVC_DIR_MOUNT_PATH);

    Map<String, String> actual = k8InitializeTaskUtils.getVolumeToMountPath(sharedPaths, podVolumes);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getLinuxOS() {
    K8sDirectInfraYaml k8sDirectInfraYaml = K8InitializeTaskUtilsHelper.getDirectK8InfrastructureWithVolume();

    OSType expected = OSType.Linux;
    OSType actual = k8InitializeTaskUtils.getOS(k8sDirectInfraYaml);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getWindowsOS() {
    K8sDirectInfraYaml k8sDirectInfraYaml = K8InitializeTaskUtilsHelper.getDirectK8InfrastructureWithVolume();
    k8sDirectInfraYaml.getSpec().setOs(ParameterField.createValueField(OSType.Windows));

    OSType expected = OSType.Windows;
    OSType actual = k8InitializeTaskUtils.getOS(k8sDirectInfraYaml);
    assertThat(actual).isEqualTo(expected);
  }
}
