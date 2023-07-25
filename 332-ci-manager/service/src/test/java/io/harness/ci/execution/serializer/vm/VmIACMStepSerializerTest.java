/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.serializer.vm;

import static io.harness.annotations.dev.HarnessTeam.IACM;
import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.IACMTerraformPluginInfo;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.buildstate.PluginSettingUtils;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.serializer.vm.VmIACMStepSerializer;
import io.harness.ci.utils.CIStepInfoUtils;
import io.harness.ci.utils.HarnessImageUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.iacm.execution.IACMStepsUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(IACM)
public class VmIACMStepSerializerTest extends CategoryTest {
  @Mock private CIExecutionConfigService ciExecutionConfigService;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private HarnessImageUtils harnessImageUtils;

  @Mock IACMStepsUtils iacmStepsUtils;
  @InjectMocks private VmIACMStepSerializer vmIACMPluginCompatibleStepSerializer;
  private Ambiance ambiance = Ambiance.newBuilder()
                                  .putAllSetupAbstractions(Maps.of("accountId", "accountId", "projectIdentifier",
                                      "projectIdentfier", "orgIdentifier", "orgIdentifier"))
                                  .build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  @Ignore("CI-8692: TI team to follow up")
  public void testIACMGetWorkspaceVariables() {
    Map<String, String> envVars = new HashMap<>();
    envVars.put("Key1", "Value1");
    envVars.put("Key2", "Value1");
    Map<String, String> tfVars = new HashMap<>();
    tfVars.put("tfvar1", "TfValue1");
    tfVars.put("tfvar2", "Value1");
    Map<String, String> env = new HashMap<>();
    env.put("command", "Apply");
    IACMTerraformPluginInfo stepInfo = IACMTerraformPluginInfo.builder()
                                           .envVariables(ParameterField.createValueField(env))
                                           .identifier("id")
                                           .name("name")
                                           .operation(ParameterField.<String>builder().build())
                                           .image(ParameterField.<String>builder().build())
                                           .build();

    Mockito.mockStatic(CIStepInfoUtils.class);
    when(CIStepInfoUtils.getPluginCustomStepImage(any(), any(), any(), any())).thenReturn("imageName");
    when(iacmStepsUtils.replaceExpressionFunctorToken(any(), any())).thenReturn(new HashMap<>() {
      {
        put("ENV_SECRETS_keytest1", "${ngSecretManager.obtain");
        put("PLUGIN_keytest2", "keyValue2");
        put("TFVARS_SECRETS_keytest3", "${ngSecretManager.obtain");
        put("TF_keytest4", "keyValue4");
        put("PLUGIN_CONNECTOR_REF", "connectorRef");
        put("PLUGIN_PROVISIONER", "provisioner");
      }
    });
    when(iacmStepsUtils.retrieveIACMConnectorDetails(ambiance, "connectorRef", "provisioner"))
        .thenReturn(ConnectorDetails.builder().build());
    Mockito.mockStatic(IntegrationStageUtils.class);
    when(IntegrationStageUtils.getFullyQualifiedImageName(any(), any())).thenReturn("imageName");
    Mockito.mockStatic(PluginSettingUtils.class);
    when(PluginSettingUtils.getConnectorSecretEnvMap(any())).thenReturn(new HashMap<>());
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(ConnectorDetails.builder().build());

    VmPluginStep vmPluginStep = vmIACMPluginCompatibleStepSerializer.serialize(ambiance, stepInfo, null, null);
    assertThat(vmPluginStep.getEnvVariables().size()).isEqualTo(5);
    assertThat(vmPluginStep.getEnvVariables().get("ENV_SECRETS_keytest1")).contains("${ngSecretManager.obtain");
    assertThat(vmPluginStep.getEnvVariables().get("PLUGIN_keytest2")).isEqualTo("keyValue2");
    assertThat(vmPluginStep.getEnvVariables().get("TFVARS_SECRETS_keytest3")).contains("${ngSecretManager.obtain");
    assertThat(vmPluginStep.getEnvVariables().get("TF_keytest4")).isEqualTo("keyValue4");
  }
}
