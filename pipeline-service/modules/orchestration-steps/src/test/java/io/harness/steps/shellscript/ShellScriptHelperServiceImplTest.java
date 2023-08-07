/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.shellscript;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.DirectK8sInfraDelegateConfig;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG.ShellScriptTaskParametersNGBuilder;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.remote.FileStoreClient;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.validation.InputSetValidatorFactory;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.SshKeySpecDTOHelper;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.shell.ScriptType;
import io.harness.steps.OutputExpressionConstants;
import io.harness.utils.PmsFeatureFlagHelper;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import retrofit2.Call;
import retrofit2.Response;

@Slf4j
@OwnedBy(CDC)
@RunWith(MockitoJUnitRunner.class)
public class ShellScriptHelperServiceImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private SecretNGManagerClient secretManagerClient;
  @Mock private SshKeySpecDTOHelper sshKeySpecDTOHelper;
  @Mock private ShellScriptHelperService shellScriptHelperService;
  @Mock private NGSettingsClient settingsClient;
  @Mock private Call<ResponseDTO<SettingValueResponseDTO>> response;
  @Mock private EngineExpressionService engineExpressionService;
  @Mock FileStoreClient fileStoreClient;
  @Mock private InputSetValidatorFactory inputSetValidatorFactory;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @InjectMocks private ShellScriptHelperServiceImpl shellScriptHelperServiceImpl;

  @Before
  public void beforeRun() {
    PowerMockito.when(settingsClient.getSetting(any(), any(), any(), any())).thenReturn(response);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetEnvironmentVariables() throws IOException {
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("false").valueType(SettingValueType.BOOLEAN).build();
    PowerMockito.when(response.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));

    assertThat(shellScriptHelperServiceImpl.getEnvironmentVariables(null, Ambiance.newBuilder().build())).isEmpty();
    assertThat(shellScriptHelperServiceImpl.getEnvironmentVariables(new HashMap<>(), Ambiance.newBuilder().build()))
        .isEmpty();

    Map<String, Object> envVariables = new HashMap<>();
    envVariables.put("var1", Arrays.asList(1));
    envVariables.put("var2", "val2");
    envVariables.put("var3", ParameterField.createValueField("val3"));
    envVariables.put("var4", ParameterField.createExpressionField(true, "<+unresolved>", null, true));

    assertThatThrownBy(
        () -> shellScriptHelperServiceImpl.getEnvironmentVariables(envVariables, Ambiance.newBuilder().build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Env. variables: [var4] found to be unresolved");

    envVariables.remove("var4");
    Map<String, String> environmentVariables =
        shellScriptHelperServiceImpl.getEnvironmentVariables(envVariables, Ambiance.newBuilder().build());
    assertThat(environmentVariables).hasSize(2);
    assertThat(environmentVariables.get("var2")).isEqualTo("val2");
    assertThat(environmentVariables.get("var3")).isEqualTo("val3");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetEnvironmentVariablesWithServiceVariables() throws IOException {
    // export service variable setting enabled
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    PowerMockito.when(response.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));

    VariablesSweepingOutput serviceVariableOutput = new VariablesSweepingOutput();
    serviceVariableOutput.put("svar1", ParameterField.createValueField("sval1"));

    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(serviceVariableOutput).build());

    // without any input variables. only service vars should be present
    assertThat(shellScriptHelperServiceImpl.getEnvironmentVariables(null, Ambiance.newBuilder().build())).hasSize(1);
    assertThat(shellScriptHelperServiceImpl.getEnvironmentVariables(new HashMap<>(), Ambiance.newBuilder().build()))
        .hasSize(1);

    Map<String, Object> envVariables = new HashMap<>();
    envVariables.put("var1", Arrays.asList(1));
    envVariables.put("var2", "val2");
    envVariables.put("var3", ParameterField.createValueField("val3"));

    Map<String, String> environmentVariables =
        shellScriptHelperServiceImpl.getEnvironmentVariables(envVariables, Ambiance.newBuilder().build());
    assertThat(environmentVariables).hasSize(3);
    assertThat(environmentVariables.get("svar1")).isEqualTo("sval1");
    assertThat(environmentVariables.get("var2")).isEqualTo("val2");
    assertThat(environmentVariables.get("var3")).isEqualTo("val3");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetOutputVars() {
    assertThat(shellScriptHelperServiceImpl.getOutputVars(null, new HashSet<>())).isEmpty();
    assertThat(shellScriptHelperServiceImpl.getOutputVars(new HashMap<>(), new HashSet<>())).isEmpty();

    Map<String, Object> outputVariables = new LinkedHashMap<>();
    outputVariables.put("var1", Arrays.asList(1));
    outputVariables.put("var2", "val2");
    outputVariables.put("var3", ParameterField.createValueField("val3"));
    outputVariables.put("var4", ParameterField.createExpressionField(true, "<+unresolved>", null, true));

    assertThatThrownBy(() -> shellScriptHelperServiceImpl.getOutputVars(outputVariables, new HashSet<>()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Output variable [var4] value found to be empty");

    outputVariables.remove("var4");
    List<String> outputVariablesValues = shellScriptHelperServiceImpl.getOutputVars(outputVariables, new HashSet<>());
    assertThat(outputVariablesValues).hasSize(2);
    assertThat(outputVariablesValues.get(0)).isEqualTo("val2");
    assertThat(outputVariablesValues.get(1)).isEqualTo("val3");

    Set<String> secretOutputVars = new HashSet<>();
    secretOutputVars.add("var2");
    outputVariablesValues = shellScriptHelperServiceImpl.getOutputVars(outputVariables, secretOutputVars);
    assertThat(outputVariablesValues).hasSize(1);
    assertThat(outputVariablesValues.get(0)).isEqualTo("val3");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetSecretOutputVars() {
    assertThat(shellScriptHelperServiceImpl.getSecretOutputVars(null, new HashSet<>())).isEmpty();
    assertThat(shellScriptHelperServiceImpl.getSecretOutputVars(new HashMap<>(), new HashSet<>())).isEmpty();

    Map<String, Object> outputVariables = new LinkedHashMap<>();
    outputVariables.put("var1", Arrays.asList(1));
    outputVariables.put("var2", "val2");
    outputVariables.put("var3", ParameterField.createValueField("val3"));
    outputVariables.put("var4", ParameterField.createExpressionField(true, "<+unresolved>", null, true));

    Set<String> secretOutputVars = new HashSet<>();
    secretOutputVars.add("var4");

    assertThatThrownBy(() -> shellScriptHelperServiceImpl.getSecretOutputVars(outputVariables, secretOutputVars))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Output variable [var4] value found to be empty");

    secretOutputVars.remove("var4");
    secretOutputVars.add("var2");
    List<String> outputVariablesValues =
        shellScriptHelperServiceImpl.getSecretOutputVars(outputVariables, secretOutputVars);
    assertThat(outputVariablesValues).hasSize(1);
    assertThat(outputVariablesValues.get(0)).isEqualTo("val2");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetK8sInfraDelegateConfig() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    String script = "echo hey";
    ShellScriptTaskParametersNGBuilder taskParamsBuilder = ShellScriptTaskParametersNG.builder();

    assertThat(shellScriptHelperServiceImpl.getK8sInfraDelegateConfig(ambiance, script)).isNull();

    script = "export KUBE_CONFIG=${HARNESS_KUBE_CONFIG_PATH}";
    OptionalSweepingOutput optionalSweepingOutput = OptionalSweepingOutput.builder().found(false).build();
    doReturn(optionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.K8S_INFRA_DELEGATE_CONFIG_OUTPUT_NAME));
    assertThat(shellScriptHelperServiceImpl.getK8sInfraDelegateConfig(ambiance, script)).isNull();

    K8sInfraDelegateConfigOutput k8sInfraDelegateConfigOutput = K8sInfraDelegateConfigOutput.builder().build();
    optionalSweepingOutput = OptionalSweepingOutput.builder().found(true).output(k8sInfraDelegateConfigOutput).build();
    doReturn(optionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.K8S_INFRA_DELEGATE_CONFIG_OUTPUT_NAME));
    assertThat(shellScriptHelperServiceImpl.getK8sInfraDelegateConfig(ambiance, script)).isNull();

    DirectK8sInfraDelegateConfig k8sInfraDelegateConfig = DirectK8sInfraDelegateConfig.builder().build();
    k8sInfraDelegateConfigOutput =
        K8sInfraDelegateConfigOutput.builder().k8sInfraDelegateConfig(k8sInfraDelegateConfig).build();
    optionalSweepingOutput = OptionalSweepingOutput.builder().found(true).output(k8sInfraDelegateConfigOutput).build();
    doReturn(optionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.K8S_INFRA_DELEGATE_CONFIG_OUTPUT_NAME));
    assertThat(shellScriptHelperServiceImpl.getK8sInfraDelegateConfig(ambiance, script))
        .isEqualTo(k8sInfraDelegateConfig);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testPrepareTaskParametersForIncorrectExecutionTarget() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ShellScriptStepParameters stepParameters =
        ShellScriptStepParameters.infoBuilder().onDelegate(ParameterField.createValueField(true)).build();
    ShellScriptTaskParametersNGBuilder taskParamsBuilder = ShellScriptTaskParametersNG.builder();

    shellScriptHelperServiceImpl.prepareTaskParametersForExecutionTarget(ambiance, stepParameters, taskParamsBuilder);
    assertThat(taskParamsBuilder.build().getHost()).isNull();

    stepParameters.setOnDelegate(ParameterField.createValueField(false));
    assertThatThrownBy(()
                           -> shellScriptHelperServiceImpl.prepareTaskParametersForExecutionTarget(
                               ambiance, stepParameters, taskParamsBuilder))
        .hasMessageContaining("Execution Target can't be empty with on delegate set to false");

    ExecutionTarget executionTarget = ExecutionTarget.builder().build();
    stepParameters.setExecutionTarget(executionTarget);
    assertThatThrownBy(()
                           -> shellScriptHelperServiceImpl.prepareTaskParametersForExecutionTarget(
                               ambiance, stepParameters, taskParamsBuilder))
        .hasMessageContaining("Connector Ref in Execution Target can't be empty");

    executionTarget.setConnectorRef(ParameterField.createValueField("cRef"));
    assertThatThrownBy(()
                           -> shellScriptHelperServiceImpl.prepareTaskParametersForExecutionTarget(
                               ambiance, stepParameters, taskParamsBuilder))
        .hasMessageContaining("Host in Execution Target can't be empty");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testPrepareTaskParametersForExecutionTarget() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
                            .build();
    ExecutionTarget executionTarget = ExecutionTarget.builder()
                                          .connectorRef(ParameterField.createValueField("cref"))
                                          .host(ParameterField.createValueField("host"))
                                          .build();
    ShellScriptStepParameters stepParameters = ShellScriptStepParameters.infoBuilder()
                                                   .onDelegate(ParameterField.createValueField(false))
                                                   .executionTarget(executionTarget)
                                                   .build();
    ShellScriptTaskParametersNGBuilder taskParamsBuilder = ShellScriptTaskParametersNG.builder();

    try (MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class)) {
      aStatic.when(() -> NGRestUtils.getResponse(any(), any())).thenReturn(null);
      assertThatThrownBy(()
                             -> shellScriptHelperServiceImpl.prepareTaskParametersForExecutionTarget(
                                 ambiance, stepParameters, taskParamsBuilder))
          .hasMessageContaining("No secret configured with identifier: cref");

      SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().build();
      Optional<SecretResponseWrapper> secretResponseWrapperOptional = Optional.of(
          SecretResponseWrapper.builder().secret(SecretDTOV2.builder().spec(sshKeySpecDTO).build()).build());
      when(NGRestUtils.getResponse(any(), any())).thenReturn(secretResponseWrapperOptional.get());

      List<EncryptedDataDetail> encryptedDataDetails = Collections.singletonList(EncryptedDataDetail.builder().build());
      doReturn(encryptedDataDetails)
          .when(sshKeySpecDTOHelper)
          .getSSHKeyEncryptionDetails(sshKeySpecDTO, AmbianceUtils.getNgAccess(ambiance));
      shellScriptHelperServiceImpl.prepareTaskParametersForExecutionTarget(ambiance, stepParameters, taskParamsBuilder);
      assertThat(taskParamsBuilder.build().getHost()).isEqualTo("host");
      assertThat(taskParamsBuilder.build().getEncryptionDetails()).hasSize(1);
      assertThat(taskParamsBuilder.build().getSshKeySpecDTO()).isEqualTo(sshKeySpecDTO);
    }
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetShellScript() {
    String script = "echo <+unresolved1>\necho <+unresolved2>";
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(Map.of("accountId", "testAcc")).build();

    ParameterField<String> parameterFieldScript = ParameterField.createExpressionField(true, script, null, true);
    ShellScriptInlineSource source = ShellScriptInlineSource.builder().script(parameterFieldScript).build();
    ShellScriptStepParameters stepParameters =
        ShellScriptStepParameters.infoBuilder()
            .source(ShellScriptSourceWrapper.builder().spec(source).type("Inline").build())
            .build();
    assertThat(shellScriptHelperServiceImpl.getShellScript(stepParameters, ambiance))
        .isEqualTo("echo <+unresolved1>\necho <+unresolved2>");
    source.setScript(ParameterField.createValueField("echo hi"));
    assertThat(shellScriptHelperServiceImpl.getShellScript(stepParameters, ambiance)).isEqualTo("echo hi");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetShellScriptWithFileScript() {
    final String fileContent = "echo hi";
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(Map.of("accountId", "testAcc")).build();

    ParameterField<String> parameterFieldFile = ParameterField.createValueField("account:/test");
    HarnessFileStoreSource source = HarnessFileStoreSource.builder().file(parameterFieldFile).build();
    ShellScriptStepParameters stepParameters =
        ShellScriptStepParameters.infoBuilder()
            .source(ShellScriptSourceWrapper.builder().spec(source).type("Harness").build())
            .build();

    Call call = mock(Call.class);

    try (MockedStatic<SafeHttpCall> safeHttpCallMockedStatic = mockStatic(SafeHttpCall.class)) {
      doReturn(call).when(fileStoreClient).getContent(anyString(), anyString(), anyString(), anyString());
      doReturn(fileContent).when(engineExpressionService).renderExpression(any(), anyString());
      doReturn(true).when(pmsFeatureFlagHelper).isEnabled(anyString(), any(FeatureName.class));

      safeHttpCallMockedStatic.when(() -> SafeHttpCall.executeWithExceptions(any()))
          .thenReturn(ResponseDTO.newResponse(fileContent));
      String ret = shellScriptHelperServiceImpl.getShellScript(stepParameters, ambiance);
      assertThat(ret).isEqualTo(fileContent);
    }
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetShellScriptWithFileScriptFFNotEnabled() {
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(Map.of("accountId", "testAcc")).build();

    ParameterField<String> parameterFieldFile = ParameterField.createValueField("account:/test");
    HarnessFileStoreSource source = HarnessFileStoreSource.builder().file(parameterFieldFile).build();
    ShellScriptStepParameters stepParameters =
        ShellScriptStepParameters.infoBuilder()
            .source(ShellScriptSourceWrapper.builder().spec(source).type("Harness").build())
            .build();

    assertThatThrownBy(() -> shellScriptHelperServiceImpl.getShellScript(stepParameters, ambiance))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetShellScriptThrowsException() {
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(Map.of("accountId", "testAcc")).build();

    ParameterField<String> parameterFieldFile = ParameterField.createValueField("account:/test");
    HarnessFileStoreSource source = HarnessFileStoreSource.builder().file(parameterFieldFile).build();
    ShellScriptStepParameters stepParameters =
        ShellScriptStepParameters.infoBuilder()
            .source(ShellScriptSourceWrapper.builder().spec(source).type("Unknown").build())
            .build();

    assertThatThrownBy(() -> shellScriptHelperServiceImpl.getShellScript(stepParameters, ambiance))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetWorkingDirectory() {
    ShellScriptStepParameters stepParameters =
        ShellScriptStepParameters.infoBuilder().onDelegate(ParameterField.createValueField(true)).build();
    assertThat(shellScriptHelperServiceImpl.getWorkingDirectory(
                   ParameterField.ofNull(), ScriptType.BASH, stepParameters.onDelegate.getValue()))
        .isEqualTo("/tmp");
    assertThat(shellScriptHelperServiceImpl.getWorkingDirectory(
                   ParameterField.ofNull(), ScriptType.POWERSHELL, stepParameters.onDelegate.getValue()))
        .isEqualTo("/tmp");
    stepParameters.setOnDelegate(ParameterField.createValueField(false));
    assertThat(shellScriptHelperServiceImpl.getWorkingDirectory(
                   ParameterField.ofNull(), ScriptType.POWERSHELL, stepParameters.onDelegate.getValue()))
        .isEqualTo("%TEMP%");

    stepParameters.setExecutionTarget(
        ExecutionTarget.builder().workingDirectory(ParameterField.createValueField("dir")).build());
    assertThat(
        shellScriptHelperServiceImpl.getWorkingDirectory(stepParameters.getExecutionTarget().getWorkingDirectory(),
            ScriptType.BASH, stepParameters.onDelegate.getValue()))
        .isEqualTo("dir");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testBuildShellScriptTaskParametersNG() {
    Ambiance ambiance = buildAmbiance();
    Map<String, Object> inputVars = new LinkedHashMap<>();
    inputVars.put("key1", "val1");
    Map<String, Object> outputVars = new LinkedHashMap<>();
    outputVars.put("key1", "val1");
    outputVars.put("key2", "val2");

    ShellScriptStepParameters stepParameters = ShellScriptStepParameters.infoBuilder()
                                                   .shellType(ShellType.Bash)
                                                   .onDelegate(ParameterField.createValueField(true))
                                                   .environmentVariables(inputVars)
                                                   .outputVariables(outputVars)
                                                   .secretOutputVariables(new HashSet<>())
                                                   .build();
    String script = "echo hey";
    DirectK8sInfraDelegateConfig k8sInfraDelegateConfig = DirectK8sInfraDelegateConfig.builder().build();
    Map<String, String> taskEnvVariables = new LinkedHashMap<>();
    inputVars.put("key1", "val1");
    List<String> taskOutputVars = Arrays.asList("key1", "key2");

    doReturn(script).when(shellScriptHelperService).getShellScript(stepParameters, ambiance);
    doNothing()
        .when(shellScriptHelperService)
        .prepareTaskParametersForExecutionTarget(eq(ambiance), eq(stepParameters), any());
    doReturn(k8sInfraDelegateConfig).when(shellScriptHelperService).getK8sInfraDelegateConfig(ambiance, script);
    doReturn(taskEnvVariables)
        .when(shellScriptHelperService)
        .getEnvironmentVariables(inputVars, Ambiance.newBuilder().build());
    doReturn(taskOutputVars).when(shellScriptHelperService).getOutputVars(outputVars, new HashSet<>());
    doReturn("/tmp")
        .when(shellScriptHelperService)
        .getWorkingDirectory(ParameterField.ofNull(), ScriptType.BASH, stepParameters.onDelegate.getValue());

    ShellScriptTaskParametersNG taskParams =
        (ShellScriptTaskParametersNG) shellScriptHelperServiceImpl.buildShellScriptTaskParametersNG(
            ambiance, stepParameters);
    assertThat(taskParams.getScript()).isEqualTo(script);
    assertThat(taskParams.getK8sInfraDelegateConfig()).isEqualTo(k8sInfraDelegateConfig);
    assertThat(taskParams.getWorkingDirectory()).isEqualTo("/tmp");
    assertThat(taskParams.getOutputVars()).isEqualTo(taskOutputVars);
    assertThat(taskParams.getEnvironmentVariables()).isEqualTo(taskEnvVariables);
  }

  private Ambiance buildAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
        .build();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testPrepareShellScriptOutcome() {
    ShellScriptOutcome shellScriptOutcome =
        shellScriptHelperServiceImpl.prepareShellScriptOutcome(null, new HashMap<>());
    assertThat(shellScriptOutcome).isNull();

    shellScriptOutcome = shellScriptHelperServiceImpl.prepareShellScriptOutcome(new HashMap<>(), null);
    assertThat(shellScriptOutcome).isNull();

    Map<String, Object> outputVariables = new HashMap<>();
    outputVariables.put("output1", ParameterField.createValueField("var1"));
    outputVariables.put("output2", ParameterField.createValueField("var2"));

    shellScriptOutcome = shellScriptHelperServiceImpl.prepareShellScriptOutcome(new HashMap<>(), outputVariables);
    assertThat(shellScriptOutcome.getOutputVariables()).hasSize(2);
    assertThat(shellScriptOutcome.getOutputVariables().get("output1")).isNull();

    Map<String, String> envVariables = new HashMap<>();
    envVariables.put("var1", "val1");
    envVariables.put("var2", "val2");

    shellScriptOutcome = shellScriptHelperServiceImpl.prepareShellScriptOutcome(envVariables, outputVariables);
    assertThat(shellScriptOutcome.getOutputVariables()).hasSize(2);
    assertThat(shellScriptOutcome.getOutputVariables().get("output1")).isEqualTo("val1");
    assertThat(shellScriptOutcome.getOutputVariables().get("output2")).isEqualTo("val2");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testPrepareShellScriptOutcomeWithSecretVars() {
    ShellScriptOutcome shellScriptOutcome =
        ShellScriptHelperService.prepareShellScriptOutcome(null, new HashMap<>(), new HashSet<>());
    assertThat(shellScriptOutcome).isNull();

    shellScriptOutcome = ShellScriptHelperService.prepareShellScriptOutcome(new HashMap<>(), null, null);
    assertThat(shellScriptOutcome).isNull();

    Map<String, Object> outputVariables = new HashMap<>();
    outputVariables.put("output1", ParameterField.createValueField("var1"));
    outputVariables.put("output2", ParameterField.createValueField("var2"));

    shellScriptOutcome = ShellScriptHelperService.prepareShellScriptOutcome(new HashMap<>(), outputVariables, null);
    assertThat(shellScriptOutcome).isNotNull();
    assertThat(shellScriptOutcome.getOutputVariables()).hasSize(2);
    assertThat(shellScriptOutcome.getOutputVariables().get("output1")).isNull();

    Map<String, String> envVariables = new HashMap<>();
    envVariables.put("var1", "val1");
    envVariables.put("var2", "val2");

    shellScriptOutcome = ShellScriptHelperService.prepareShellScriptOutcome(envVariables, outputVariables, null);
    assertThat(shellScriptOutcome).isNotNull();
    assertThat(shellScriptOutcome.getOutputVariables()).hasSize(2);
    assertThat(shellScriptOutcome.getOutputVariables().get("output1")).isEqualTo("val1");
    assertThat(shellScriptOutcome.getOutputVariables().get("output2")).isEqualTo("val2");

    Set<String> secretOutputVars = new HashSet<>();
    secretOutputVars.add("output1");

    // "${sweepingOutputSecrets.obtain("output1","sa32zupgqijF2be+H2lEAw7yfMwGDFtC5zciKbzQGEtm5Vq+cjo7RclAhPVLTig7")}">
    String SECRET_REGEX = "\\$\\{sweepingOutputSecrets\\.obtain\\(\"[\\S|.]+\",\"[\\S|.]+\"\\)}";

    shellScriptOutcome =
        ShellScriptHelperService.prepareShellScriptOutcome(envVariables, outputVariables, secretOutputVars);
    assertThat(shellScriptOutcome).isNotNull();
    assertThat(shellScriptOutcome.getOutputVariables()).hasSize(2);
    assertThat(shellScriptOutcome.getOutputVariables().get("output1")).containsPattern(Pattern.compile(SECRET_REGEX));
    assertThat(shellScriptOutcome.getOutputVariables().get("output2")).isEqualTo("val2");

    shellScriptOutcome =
        ShellScriptHelperService.prepareShellScriptOutcome(new HashMap<>(), outputVariables, secretOutputVars);
    assertThat(shellScriptOutcome).isNotNull();
    assertThat(shellScriptOutcome.getOutputVariables().get("output1")).isNull();
  }
}
