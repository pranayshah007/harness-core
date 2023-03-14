/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import static io.harness.annotations.dev.HarnessTeam.IACM;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_ACCESS_KEY;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_SECRET_KEY;
import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.entities.Stack;
import io.harness.beans.entities.StackVariables;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.buildstate.PluginSettingUtils;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.utils.CIStepInfoUtils;
import io.harness.ci.utils.HarnessImageUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.iacm.execution.IACMStepsUtils;
import io.harness.iacmserviceclient.IACMServiceUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(IACM)
public class IACMStepUtilTest extends CategoryTest {
  @Mock private CIExecutionConfigService ciExecutionConfigService;
  @Mock ConnectorUtils connectorUtils;
  @Mock HarnessImageUtils harnessImageUtils;
  @Mock IACMServiceUtils iacmServiceUtils;
  @InjectMocks private IACMStepsUtils iacmStepsUtils;
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
  public void testIACMGetStackVariables() {
    Map<String, ParameterField<String>> stepVars = new HashMap<>();
    stepVars.put("STACK_ID", ParameterField.createValueField("stackID"));
    stepVars.put("WORKFLOW", ParameterField.createValueField("provision"));
    Map<String, ParameterField<String>> envVars = new HashMap<>();
    envVars.put("Key1", ParameterField.createValueField("Value1"));
    envVars.put("Key2", ParameterField.createValueField("Value1"));
    Map<String, ParameterField<String>> tfVars = new HashMap<>();
    tfVars.put("tfvar1", ParameterField.createValueField("TfValue1"));
    tfVars.put("tfvar2", ParameterField.createValueField("Value1"));
    Map<String, ParameterField<String>> env = new HashMap<>();
    env.put("command", ParameterField.createValueField("Apply"));
    PluginStepInfo stepInfo = PluginStepInfo.builder()
                                  .envVariables(ParameterField.createValueField(stepVars))
                                  .uses(ParameterField.createValueField("initialise"))
                                  .build();
    StackVariables[] stackVariables = new StackVariables[] {StackVariables.builder()
                                                                .stack("123")
                                                                .account("abc")
                                                                .key("keytest1")
                                                                .kind("env")
                                                                .value("keyValue1")
                                                                .value_type("secret")
                                                                .build(),
        StackVariables.builder()
            .stack("123")
            .account("abc")
            .key("keytest2")
            .kind("env")
            .value("keyValue2")
            .value_type("string")
            .build(),
        StackVariables.builder()
            .stack("123")
            .account("abc")
            .key("keytest3")
            .kind("tf")
            .value("keyValue3")
            .value_type("secret")
            .build(),
        StackVariables.builder()
            .stack("123")
            .account("abc")
            .key("keytest4")
            .kind("tf")
            .value("keyValue4")
            .value_type("tf")
            .build()};

    Mockito.mockStatic(CIStepInfoUtils.class);
    when(CIStepInfoUtils.getPluginCustomStepImage(any(), any(), any(), any())).thenReturn("imageName");
    Stack stack = Stack.builder().provider_connector("awsTest").provisioner("terraform").build();
    when(iacmServiceUtils.getIACMStackInfo(any(), any(), any(), any())).thenReturn(stack);
    when(iacmServiceUtils.getIacmStackEnvs(any(), any(), any(), any())).thenReturn(stackVariables);
    when(harnessImageUtils.getHarnessImageConnectorDetailsForVM(any(), any()))
        .thenReturn(ConnectorDetails.builder().build());
    Mockito.mockStatic(IntegrationStageUtils.class);
    when(IntegrationStageUtils.getFullyQualifiedImageName(any(), any())).thenReturn("imageName");
    Mockito.mockStatic(PluginSettingUtils.class);
    when(PluginSettingUtils.getConnectorSecretEnvMap(any())).thenReturn(new HashMap<>());
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(ConnectorDetails.builder().build());

    VmPluginStep vmPluginStep = iacmStepsUtils.injectIACMInfo(ambiance, stepInfo, null, null);
    assertThat(vmPluginStep.getEnvVariables().size()).isEqualTo(8);
    assertThat(vmPluginStep.getEnvVariables().get("ENV_SECRETS_keytest1")).contains("${ngSecretManager.obtain");
    assertThat(vmPluginStep.getEnvVariables().get("PLUGIN_keytest2")).isEqualTo("keyValue2");
    assertThat(vmPluginStep.getEnvVariables().get("TFVARS_SECRETS_keytest3")).contains("${ngSecretManager.obtain");
    assertThat(vmPluginStep.getEnvVariables().get("TF_keytest4")).isEqualTo("keyValue4");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testIACMGetConnectorRef() {
    Map<String, ParameterField<String>> stepVars = new HashMap<>();
    stepVars.put("STACK_ID", ParameterField.createValueField("stackID"));
    stepVars.put("WORKFLOW", ParameterField.createValueField("provision"));
    Map<String, String> env = new HashMap<>();
    env.put("command", "Apply");
    PluginStepInfo stepInfo = PluginStepInfo.builder()
                                  .envVariables(ParameterField.createValueField(stepVars))
                                  .uses(ParameterField.createValueField("initialise"))
                                  .build();

    Mockito.mockStatic(CIStepInfoUtils.class);
    when(CIStepInfoUtils.getPluginCustomStepImage(any(), any(), any(), any())).thenReturn("imageName");
    Stack stack = Stack.builder()
                      .provider_connector("awsTest")
                      .repository_path("root")
                      .provisioner_version("1.2.3")
                      .provisioner("terraform")
                      .build();
    when(iacmServiceUtils.getIACMStackInfo(any(), any(), any(), any())).thenReturn(stack);
    when(iacmServiceUtils.getIacmStackEnvs(any(), any(), any(), any())).thenReturn(new StackVariables[] {});
    when(harnessImageUtils.getHarnessImageConnectorDetailsForVM(any(), any()))
        .thenReturn(ConnectorDetails.builder().build());
    Mockito.mockStatic(IntegrationStageUtils.class);
    when(IntegrationStageUtils.getFullyQualifiedImageName(any(), any())).thenReturn("imageName");
    Mockito.mockStatic(PluginSettingUtils.class);
    Map<EnvVariableEnum, String> map = new HashMap<>();
    map.put(EnvVariableEnum.AWS_ACCESS_KEY, PLUGIN_ACCESS_KEY);
    map.put(EnvVariableEnum.AWS_SECRET_KEY, PLUGIN_SECRET_KEY);
    when(PluginSettingUtils.getConnectorSecretEnvMap(any())).thenReturn(map);
    when(connectorUtils.getConnectorDetails(any(), any()))
        .thenReturn(ConnectorDetails.builder().connectorType(ConnectorType.AWS).build());

    VmPluginStep vmPluginStep = iacmStepsUtils.injectIACMInfo(ambiance, stepInfo, null, null);
    assertThat(vmPluginStep.getEnvVariables().size()).isEqualTo(4);
    assertThat(vmPluginStep.getEnvVariables().get("PLUGIN_ROOT_DIR")).isEqualTo("root");
    assertThat(vmPluginStep.getEnvVariables().get("PLUGIN_TF_VERSION")).isEqualTo("1.2.3");
    assertThat(vmPluginStep.getConnector().getConnectorType()).isEqualTo(ConnectorType.AWS);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testDifferentStepsInputs() {
    Map<String, ParameterField<String>> stepVars = new HashMap<>();
    stepVars.put("STACK_ID", ParameterField.createValueField("stackID"));

    Mockito.mockStatic(CIStepInfoUtils.class);
    when(CIStepInfoUtils.getPluginCustomStepImage(any(), any(), any(), any())).thenReturn("imageName");
    Stack stack = Stack.builder()
                      .provider_connector("awsTest")
                      .repository_path("root")
                      .provisioner_version("1.2.3")
                      .provisioner("terraform")
                      .build();
    when(iacmServiceUtils.getIACMStackInfo(any(), any(), any(), any())).thenReturn(stack);
    when(iacmServiceUtils.getIacmStackEnvs(any(), any(), any(), any())).thenReturn(new StackVariables[] {});
    when(harnessImageUtils.getHarnessImageConnectorDetailsForVM(any(), any()))
        .thenReturn(ConnectorDetails.builder().build());
    Mockito.mockStatic(IntegrationStageUtils.class);
    when(IntegrationStageUtils.getFullyQualifiedImageName(any(), any())).thenReturn("imageName");
    Mockito.mockStatic(PluginSettingUtils.class);
    Map<EnvVariableEnum, String> map = new HashMap<>();
    map.put(EnvVariableEnum.AWS_ACCESS_KEY, PLUGIN_ACCESS_KEY);
    map.put(EnvVariableEnum.AWS_SECRET_KEY, PLUGIN_SECRET_KEY);
    when(PluginSettingUtils.getConnectorSecretEnvMap(any())).thenReturn(map);
    when(connectorUtils.getConnectorDetails(any(), any()))
        .thenReturn(ConnectorDetails.builder().connectorType(ConnectorType.AWS).build());
    List<String> commands = Arrays.asList("initialise", "evaluate", "execute");
    List<String> workflows = Arrays.asList("provision", "teardown");

    for (int i = 0; i <= commands.size() - 1; i++) {
      for (int j = 0; j <= workflows.size() - 1; j++) {
        stepVars.put("WORKFLOW", ParameterField.createValueField(workflows.get(j)));
        PluginStepInfo stepInfo = PluginStepInfo.builder()
                                      .envVariables(ParameterField.createValueField(stepVars))
                                      .uses(ParameterField.createValueField(commands.get(i)))
                                      .build();
        VmPluginStep vmPluginStep = iacmStepsUtils.injectIACMInfo(ambiance, stepInfo, null, null);
        if (i == 0) {
          assertThat(vmPluginStep.getEnvVariables().size()).isEqualTo(4);
          assertThat(vmPluginStep.getEnvVariables().get("PLUGIN_OPERATIONS")).isEqualTo("initialise");
        }
        if (i == 1) {
          assertThat(vmPluginStep.getEnvVariables().size()).isEqualTo(4);
          if (j == 0) {
            assertThat(vmPluginStep.getEnvVariables().get("PLUGIN_OPERATIONS")).isEqualTo("plan");
          } else {
            assertThat(vmPluginStep.getEnvVariables().get("PLUGIN_OPERATIONS")).isEqualTo("plan-destroy");
          }
        }
        if (i == 2) {
          assertThat(vmPluginStep.getEnvVariables().size()).isEqualTo(4);
          if (j == 0) {
            assertThat(vmPluginStep.getEnvVariables().get("PLUGIN_OPERATIONS")).isEqualTo("apply");
          } else {
            assertThat(vmPluginStep.getEnvVariables().get("PLUGIN_OPERATIONS")).isEqualTo("destroy");
          }
        }
      }
    }
  }
}
