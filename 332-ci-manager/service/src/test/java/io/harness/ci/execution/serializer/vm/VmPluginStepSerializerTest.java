/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.serializer.vm;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.ci.commonconstants.CIExecutionConstants.WORKSPACE_ID;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.NGONZALEZ;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.serializer.SerializerUtils;
import io.harness.ci.serializer.vm.VmPluginStepSerializer;
import io.harness.ci.utils.HarnessImageUtils;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.delegate.beans.ci.vm.steps.VmRunStep;
import io.harness.delegate.beans.ci.vm.steps.VmStepInfo;
import io.harness.iacm.execution.IACMStepsUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Map;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CI)
public class VmPluginStepSerializerTest extends CategoryTest {
  @Mock private ConnectorUtils connectorUtils;
  @Mock private IACMStepsUtils iacmStepsUtils;
  @Mock private HarnessImageUtils harnessImageUtils;
  @Mock private CIFeatureFlagService ciFeatureFlagService;
  @Mock private SerializerUtils serializerUtils;

  @InjectMocks private VmPluginStepSerializer vmPluginStepSerializer;
  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putAllSetupAbstractions(Maps.of("accountId", "accountId", "projectIdentifier",
                                            "projectIdentfier", "orgIdentifier", "orgIdentifier"))
                                        .build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testPluginStepSerialize() {
    PluginStepInfo pluginStepInfo =
        PluginStepInfo.builder()
            .image(ParameterField.createValueField("image"))
            .privileged(ParameterField.createValueField(true))
            .runAsUser(ParameterField.createValueField(1000))
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .reports(ParameterField.createValueField(null))
            .envVariables(ParameterField.createValueField(Map.of(
                "key1", ParameterField.createValueField("val1"), "key2", ParameterField.createValueField("val2"))))
            .build();
    VmPluginStep vmPluginStep = (VmPluginStep) vmPluginStepSerializer.serialize(
        pluginStepInfo, null, "id", null, null, ambiance, null, null, null);
    assertThat(vmPluginStep.isPrivileged()).isTrue();
    assertThat(vmPluginStep.getImage()).isEqualTo("image");
    assertThat(vmPluginStep.getRunAsUser()).isEqualTo("1000");
    assertThat(vmPluginStep.getEnvVariables()).isEqualTo(Map.of("key1", "val1", "key2", "val2"));
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testPluginStepSerializeGitClone() {
    PluginStepInfo pluginStepInfo =
        PluginStepInfo.builder()
            .image(ParameterField.createValueField("image"))
            .privileged(ParameterField.createValueField(true))
            .harnessManagedImage(true)
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .reports(ParameterField.createValueField(null))
            .envVariables(ParameterField.createValueField(Map.of(
                "key1", ParameterField.createValueField("val1"), "key2", ParameterField.createValueField("val2"))))
            .build();
    StageInfraDetails stageInfraDetails = VmStageInfraDetails.builder().build();
    VmPluginStep vmPluginStep = (VmPluginStep) vmPluginStepSerializer.serialize(
        pluginStepInfo, stageInfraDetails, "harness-git-clone", null, null, ambiance, null, null, null);
    assertThat(vmPluginStep.isPrivileged()).isTrue();
    assertThat(vmPluginStep.getImage()).isEqualTo("image");
    assertThat(vmPluginStep.getEnvVariables()).isEqualTo(Map.of("key1", "val1", "key2", "val2"));
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testPluginStepSerializerCreatesIACMPluginStep() {
    PluginStepInfo pluginStepInfo =
        PluginStepInfo.builder()
            .privileged(ParameterField.createValueField(true))
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .reports(ParameterField.createValueField(null))
            .image(ParameterField.<String>builder().value("foobar").build())
            .envVariables(ParameterField.createValueField(Map.of(WORKSPACE_ID, ParameterField.createValueField("val1"),
                "PLUGIN_CONNECTOR_REF", ParameterField.createValueField("connectorRef"), "PLUGIN_PROVISIONER",
                ParameterField.createValueField("provisioner"))))
            .build();

    when(iacmStepsUtils.isIACMStep(any())).thenReturn(true);

    VmStepInfo vmStepInfo =
        vmPluginStepSerializer.serialize(pluginStepInfo, null, "id", null, null, ambiance, null, null, null);
    assertThat(vmStepInfo).isInstanceOf(VmPluginStep.class);
    VmPluginStep vmPluginStep = (VmPluginStep) vmStepInfo;
    assertThat(vmPluginStep.getImage()).isEqualTo("foobar");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testPluginStepSerializerWithUsesCreatesIACMPluginStep() {
    PluginStepInfo pluginStepInfo =
        PluginStepInfo.builder()
            .privileged(ParameterField.createValueField(true))
            .uses(ParameterField.createValueField("faaa"))
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .reports(ParameterField.createValueField(null))
            .envVariables(ParameterField.createValueField(Map.of(WORKSPACE_ID, ParameterField.createValueField("val1"),
                "PLUGIN_CONNECTOR_REF", ParameterField.createValueField("connectorRef"), "PLUGIN_PROVISIONER",
                ParameterField.createValueField("provisioner"))))
            .build();

    VmStepInfo vmStepInfo = vmPluginStepSerializer.serialize(
        pluginStepInfo, DliteVmStageInfraDetails.builder().build(), "id", null, null, ambiance, null, null, null);
    assertThat(vmStepInfo).isInstanceOf(VmRunStep.class);
  }
}
