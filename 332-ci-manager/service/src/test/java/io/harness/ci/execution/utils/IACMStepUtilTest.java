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
import io.harness.beans.entities.Workspace;
import io.harness.beans.entities.WorkspaceVariables;
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
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.iacm.execution.IACMStepsUtils;
import io.harness.iacmserviceclient.IACMServiceUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  private final Ambiance ambiance = Ambiance.newBuilder()
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
  public void testIACMGetConnectorRef() {
    Map<String, ParameterField<String>> stepVars = new HashMap<>();
    stepVars.put("HARNESS_WORKSPACE", ParameterField.createValueField("stackID"));
    Map<String, JsonNode> setting = new HashMap<>();
    ObjectMapper mapper = new ObjectMapper();
    setting.put("command", mapper.valueToTree("init"));
    PluginStepInfo stepInfo = PluginStepInfo.builder()
                                  .envVariables(ParameterField.createValueField(stepVars))
                                  .settings(ParameterField.createValueField(setting))
                                  .build();

    Mockito.mockStatic(CIStepInfoUtils.class);
    when(CIStepInfoUtils.getPluginCustomStepImage(any(), any(), any(), any())).thenReturn("imageName");
    Workspace stack = Workspace.builder()
                          .provider_connector("awsTest")
                          .repository_path("root")
                          .provisioner_version("1.2.3")
                          .provisioner("terraform")
                          .build();
    when(iacmServiceUtils.getIACMWorkspaceInfo(any(), any(), any(), any())).thenReturn(stack);
    when(iacmServiceUtils.getIacmWorkspaceEnvs(any(), any(), any(), any())).thenReturn(new WorkspaceVariables[] {});
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

    Map<String, String> envVariables = iacmStepsUtils.getIACMEnvVariables(ambiance, stepInfo);
    assertThat(envVariables).hasSize(6);
    assertThat(envVariables.get("PLUGIN_ROOT_DIR")).isEqualTo("root");
    assertThat(envVariables.get("PLUGIN_TF_VERSION")).isEqualTo("1.2.3");
    ConnectorDetails connector = iacmStepsUtils.retrieveIACMConnectorDetails(ambiance, stepInfo);
    assertThat(connector.getConnectorType()).isEqualTo(ConnectorType.AWS);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testDifferentStepsInputs() {
    Map<String, ParameterField<String>> stepVars = new HashMap<>();
    stepVars.put("HARNESS_WORKSPACE", ParameterField.createValueField("stackID"));

    Mockito.mockStatic(CIStepInfoUtils.class);
    when(CIStepInfoUtils.getPluginCustomStepImage(any(), any(), any(), any())).thenReturn("imageName");
    Workspace stack = Workspace.builder()
                          .provider_connector("awsTest")
                          .repository_path("root")
                          .provisioner_version("1.2.3")
                          .provisioner("terraform")
                          .build();
    when(iacmServiceUtils.getIACMWorkspaceInfo(any(), any(), any(), any())).thenReturn(stack);
    when(iacmServiceUtils.getIacmWorkspaceEnvs(any(), any(), any(), any())).thenReturn(new WorkspaceVariables[] {});
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
    List<String> commands = Arrays.asList("init", "plan", "plan-destroy", "apply", "destroy");

    for (int i = 0; i <= commands.size() - 1; i++) {
      stepVars.put("WORKFLOW", ParameterField.createValueField("workflow"));
      Map<String, JsonNode> setting = new HashMap<>();
      ObjectMapper mapper = new ObjectMapper();
      setting.put("command", mapper.valueToTree(commands.get(i)));
      PluginStepInfo stepInfo = PluginStepInfo.builder()
                                    .envVariables(ParameterField.createValueField(stepVars))
                                    .settings(ParameterField.createValueField(setting))
                                    .build();
      Map<String, String> vmPluginStep = iacmStepsUtils.getIACMEnvVariables(ambiance, stepInfo);
      assertThat(vmPluginStep.size()).isEqualTo(6);
      assertThat(vmPluginStep.get("PLUGIN_COMMAND")).isEqualTo(commands.get(i));
    }
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testIACMEnvVarsTransformation() {
    Map<String, ParameterField<String>> stepVars = new HashMap<>();
    stepVars.put("HARNESS_WORKSPACE", ParameterField.createValueField("stackID"));
    Map<String, JsonNode> setting = new HashMap<>();
    ObjectMapper mapper = new ObjectMapper();
    setting.put("command", mapper.valueToTree("init"));
    PluginStepInfo stepInfo = PluginStepInfo.builder()
                                  .envVariables(ParameterField.createValueField(stepVars))
                                  .settings(ParameterField.createValueField(setting))
                                  .build();

    Mockito.mockStatic(CIStepInfoUtils.class);
    when(CIStepInfoUtils.getPluginCustomStepImage(any(), any(), any(), any())).thenReturn("imageName");
    Workspace stack = Workspace.builder()
                          .provider_connector("awsTest")
                          .repository_path("root")
                          .provisioner_version("1.2.3")
                          .provisioner("terraform")
                          .build();
    when(iacmServiceUtils.getIACMWorkspaceInfo(any(), any(), any(), any())).thenReturn(stack);
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

    String[][] expectedResults = new String[][] {
        {"{\"keytest2\":\"keyValue2\",\"keytest1\":\"keyValue1\"}",
            "{\"keytest4\":\"keyValue4\",\"keytest3\":\"keyValue3\"}"},
        {"{\"keytest2\":\"keyValue2\",\"keytest1\":\"${ngSecretManager.obtain(\"keytest1\", -871314908)}\"}",
            "{\"keytest4\":\"keyValue4\",\"keytest3\":\"${ngSecretManager.obtain(\"keytest3\", -871314908)}\"}"},
        {"{}", "{}"},

    };

    WorkspaceVariables[][] testCases = {
        {WorkspaceVariables.builder()
                .stack("123")
                .account("abc")
                .key("keytest1")
                .kind("env")
                .value("keyValue1")
                .value_type("string")
                .build(),
            WorkspaceVariables.builder()
                .stack("123")
                .account("abc")
                .key("keytest2")
                .kind("env")
                .value("keyValue2")
                .value_type("string")
                .build(),
            WorkspaceVariables.builder()
                .stack("123")
                .account("abc")
                .key("keytest3")
                .kind("tf")
                .value("keyValue3")
                .value_type("string")
                .build(),
            WorkspaceVariables.builder()
                .stack("123")
                .account("abc")
                .key("keytest4")
                .kind("tf")
                .value("keyValue4")
                .value_type("string")
                .build()},

        {WorkspaceVariables.builder()
                .stack("123")
                .account("abc")
                .key("keytest1")
                .kind("env")
                .value("keyValue1")
                .value_type("secret")
                .build(),
            WorkspaceVariables.builder()
                .stack("123")
                .account("abc")
                .key("keytest2")
                .kind("env")
                .value("keyValue2")
                .value_type("string")
                .build(),
            WorkspaceVariables.builder()
                .stack("123")
                .account("abc")
                .key("keytest3")
                .kind("tf")
                .value("keyValue3")
                .value_type("secret")
                .build(),
            WorkspaceVariables.builder()
                .stack("123")
                .account("abc")
                .key("keytest4")
                .kind("tf")
                .value("keyValue4")
                .value_type("string")
                .build()},

        {},
    };

    for (int i = 0; i < testCases.length; i++) {
      when(iacmServiceUtils.getIacmWorkspaceEnvs(any(), any(), any(), any())).thenReturn(testCases[i]);
      Map<String, String> vmPluginStep = iacmStepsUtils.getIACMEnvVariables(ambiance, stepInfo);
      assertThat(vmPluginStep.get("PLUGIN_ENV_VARS")).isEqualTo(expectedResults[i][0]);
      assertThat(vmPluginStep.get("PLUGIN_VARS")).isEqualTo(expectedResults[i][1]);
    }
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testIsIACMStep() {
    Map<String, ParameterField<String>> stepVars = new HashMap<>();
    stepVars.put("HARNESS_WORKSPACE", ParameterField.createValueField("stackID"));
    Map<String, JsonNode> setting = new HashMap<>();
    ObjectMapper mapper = new ObjectMapper();
    setting.put("command", mapper.valueToTree("plan"));
    PluginStepInfo stepInfo = PluginStepInfo.builder()
                                  .envVariables(ParameterField.createValueField(stepVars))
                                  .settings(ParameterField.createValueField(setting))
                                  .build();
    assertThat(iacmStepsUtils.isIACMStep(stepInfo)).isTrue();

    PluginStepInfo stepInfo2 = PluginStepInfo.builder().settings(ParameterField.createValueField(setting)).build();
    assertThat(iacmStepsUtils.isIACMStep(stepInfo2)).isFalse();
  }
}
