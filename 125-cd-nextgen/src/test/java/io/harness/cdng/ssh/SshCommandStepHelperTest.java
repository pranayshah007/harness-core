/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.ssh.SshWinRmConstants.FILE_STORE_SCRIPT_ERROR_MSG;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.VITALIE;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileReference;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.outcome.ArtifactoryGenericArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.configfile.ConfigFileOutcome;
import io.harness.cdng.configfile.ConfigFilesOutcome;
import io.harness.cdng.execution.ExecutionDetails;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.cdng.execution.sshwinrm.SshWinRmStageExecutionDetails;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.ssh.output.SshInfraDelegateConfigOutput;
import io.harness.cdng.ssh.output.WinRmInfraDelegateConfigOutput;
import io.harness.cdng.ssh.rollback.CommandStepRollbackHelper;
import io.harness.cdng.ssh.utils.CommandStepUtils;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.storeconfig.HarnessStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.shell.SshCommandTaskParameters;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.ssh.CopyCommandUnit;
import io.harness.delegate.task.ssh.EmptyHostDelegateConfig;
import io.harness.delegate.task.ssh.NGCommandUnitType;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.PdcSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.PdcWinRmInfraDelegateConfig;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
import io.harness.delegate.task.ssh.SshInfraDelegateConfig;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.delegate.task.ssh.config.ConfigFileParameters;
import io.harness.delegate.task.ssh.config.FileDelegateConfig;
import io.harness.delegate.task.ssh.config.SecretConfigFile;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SkipRollbackException;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.contracts.refobjects.RefType;
import io.harness.pms.data.OrchestrationRefType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.shell.ScriptType;
import io.harness.ssh.FileSourceType;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.steps.shellscript.HarnessFileStoreSource;
import io.harness.steps.shellscript.ShellScriptBaseSource;
import io.harness.steps.shellscript.ShellScriptInlineSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.steps.shellscript.ShellType;

import com.google.common.collect.Maps;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({CommandStepUtils.class})
@OwnedBy(CDP)
public class SshCommandStepHelperTest extends CategoryTest {
  @Mock private SshEntityHelper sshEntityHelper;
  @Mock private OutcomeService outcomeService;
  @Mock private NGEncryptedDataService ngEncryptedDataService;
  @Mock private EncryptedDataDetail encryptedDataDetail;
  @Mock private CDExpressionResolver cdExpressionResolver;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private SshWinRmArtifactHelper sshWinRmArtifactHelper;
  @Mock private SshWinRmConfigFileHelper sshWinRmConfigFileHelper;
  @Mock private CommandStepRollbackHelper commandStepRollbackHelper;
  @Mock private NGLogCallback ngLogCallback;
  @Mock private EngineExpressionService engineExpressionService;

  @InjectMocks @Spy private SshCommandStepHelper helper;

  private final String workingDir = "/tmp";
  private final String accountId = "test";
  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions("accountId", accountId)
                                        .putSetupAbstractions("projectIdentifier", "test")
                                        .putSetupAbstractions("orgIdentifier", "org")
                                        .build();
  private final RefObject infra = RefObject.newBuilder()
                                      .setName(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                                      .setKey(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                                      .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                                      .build();

  private final RefObject artifact = RefObject.newBuilder()
                                         .setName(OutcomeExpressionConstants.ARTIFACTS)
                                         .setKey(OutcomeExpressionConstants.ARTIFACTS)
                                         .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                                         .build();

  private final RefObject configFiles =
      RefObject.newBuilder()
          .setName(OutcomeExpressionConstants.CONFIG_FILES)
          .setKey(OutcomeExpressionConstants.CONFIG_FILES)
          .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
          .build();

  private final PdcInfrastructureOutcome pdcInfrastructure =
      PdcInfrastructureOutcome.builder()
          .connectorRef("pdcConnector")
          .credentialsRef("sshKeyRef")
          .environment(EnvironmentOutcome.builder().name("env").build())
          .build();
  private final OptionalOutcome pdcInfrastructureOutcome =
      OptionalOutcome.builder()
          .found(true)
          .outcome(PdcInfrastructureOutcome.builder()
                       .credentialsRef(pdcInfrastructure.getCredentialsRef())
                       .connectorRef(pdcInfrastructure.getConnectorRef())
                       .environment(EnvironmentOutcome.builder().name("env").build())
                       .build())
          .build();

  private final ArtifactoryGenericArtifactOutcome artifactoryArtifact =
      ArtifactoryGenericArtifactOutcome.builder().connectorRef("artifactoryConnector").repositoryName("test").build();

  private final FileDelegateConfig fileDelegateConfig =
      FileDelegateConfig.builder()
          .stores(Collections.singletonList(
              HarnessStoreDelegateConfig.builder()
                  .configFiles(Arrays.asList(ConfigFileParameters.builder()
                                                 .fileContent("Hello World")
                                                 .fileSize(11L)
                                                 .fileName("test.txt")
                                                 .build(),
                      ConfigFileParameters.builder()
                          .fileName("secret-ref")
                          .isEncrypted(true)
                          .secretConfigFile(
                              SecretConfigFile.builder()
                                  .encryptedConfigFile(SecretRefData.builder().identifier("secretRef").build())
                                  .build())
                          .build()))
                  .build()))
          .build();

  private final OptionalOutcome artifactOutcome =
      OptionalOutcome.builder()
          .found(true)
          .outcome(ArtifactsOutcome.builder().primary(artifactoryArtifact).build())
          .build();

  private final ConfigFilesOutcome configFilesOutCm = new ConfigFilesOutcome();

  private final OptionalOutcome configFilesOutcome =
      OptionalOutcome.builder().found(true).outcome(configFilesOutCm).build();

  private final ServiceStepOutcome sshServiceOutcome = ServiceStepOutcome.builder().type("Ssh").name("ssh-svc").build();
  private final ServiceStepOutcome winRmServiceOutcome =
      ServiceStepOutcome.builder().type("WinRm").name("winrm-svc").build();
  private final PdcSshInfraDelegateConfig pdcSshInfraDelegateConfig =
      PdcSshInfraDelegateConfig.builder().hosts(Collections.singleton("host1")).build();

  private final PdcWinRmInfraDelegateConfig pdcWinRmInfraDelegateConfig =
      PdcWinRmInfraDelegateConfig.builder().hosts(Collections.singleton("host1")).build();

  private final ArtifactoryArtifactDelegateConfig artifactDelegateConfig =
      ArtifactoryArtifactDelegateConfig.builder().build();
  private final ParameterField workingDirParam = ParameterField.createValueField(workingDir);

  private OptionalSweepingOutput pdcSshOptionalSweepingOutput =
      OptionalSweepingOutput.builder()
          .found(true)
          .output(SshInfraDelegateConfigOutput.builder().sshInfraDelegateConfig(pdcSshInfraDelegateConfig).build())
          .build();

  private OptionalSweepingOutput pdcWinRmOptionalSweepingOutput =
      OptionalSweepingOutput.builder()
          .found(true)
          .output(
              WinRmInfraDelegateConfigOutput.builder().winRmInfraDelegateConfig(pdcWinRmInfraDelegateConfig).build())
          .build();

  private OptionalSweepingOutput variablesSweepingOutput =
      OptionalSweepingOutput.builder().found(true).output(new VariablesSweepingOutput()).build();

  MockedStatic<CommandStepUtils> mockStaticCommandStepUtils;

  @Before
  public void prepare() {
    MockitoAnnotations.initMocks(this);

    doReturn(pdcInfrastructureOutcome)
        .when(outcomeService)
        .resolveOptional(
            eq(ambiance), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)));

    doReturn(variablesSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(
            eq(ambiance), eq(RefObjectUtils.getOutcomeRefObject(YAMLFieldNameConstants.SERVICE_VARIABLES)));

    doReturn(Maps.newLinkedHashMap())
        .when(engineExpressionService)
        .evaluateExpression(eq(ambiance), eq("<+stage.variables>"));

    HarnessStore harnessStore = getHarnessStore();
    configFilesOutCm.put("test", ConfigFileOutcome.builder().identifier("test").store(harnessStore).build());
    doReturn(pdcInfrastructureOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(infra));
    doReturn(artifactOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(artifact));
    doReturn(configFilesOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(configFiles));
    doReturn(artifactDelegateConfig)
        .when(sshWinRmArtifactHelper)
        .getArtifactDelegateConfigConfig(artifactoryArtifact, ambiance);

    mockStaticCommandStepUtils = Mockito.mockStatic(CommandStepUtils.class);
    PowerMockito.when(CommandStepUtils.getWorkingDirectory(eq(workingDirParam), any(ScriptType.class), anyBoolean()))
        .thenReturn(workingDir);
    doNothing()
        .when(commandStepRollbackHelper)
        .updateRollbackData(any(io.harness.beans.Scope.class), any(String.class), any(Map.class), any(Map.class));
    doReturn(Arrays.asList(encryptedDataDetail)).when(ngEncryptedDataService).getEncryptionDetails(any(), any());
    doReturn(harnessStore).when(cdExpressionResolver).updateExpressions(any(), any());
    doNothing().when(cdExpressionResolver).updateStoreConfigExpressions(any(), any());
    doReturn(fileDelegateConfig)
        .when(sshWinRmConfigFileHelper)
        .getFileDelegateConfig(any(), eq(ambiance), anyBoolean());
  }

  @After
  public void cleanup() {
    mockStaticCommandStepUtils.close();
  }

  private HarnessStore getHarnessStore() {
    return HarnessStore.builder()
        .files(ParameterField.createValueField(
            Collections.singletonList(format("%s:%s", Scope.ACCOUNT.getYamlRepresentation(), "fs"))))
        .secretFiles(ParameterField.createValueField(Collections.singletonList("account.secret-ref")))
        .build();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testBuildScriptSshCommandTaskParameters() {
    Map<String, Object> env = new LinkedHashMap<>();
    env.put("key", "val");

    Map<String, String> taskEnv = new LinkedHashMap<>();
    env.put("key", "val");

    CommandStepParameters stepParameters = buildScriptCommandStepParams(env);

    doReturn(sshServiceOutcome)
        .when(outcomeService)
        .resolve(eq(ambiance), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE)));
    doReturn(pdcSshOptionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(eq(ambiance),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                OutputExpressionConstants.SSH_INFRA_DELEGATE_CONFIG_OUTPUT_NAME)));
    doReturn(pdcSshInfraDelegateConfig).when(sshEntityHelper).getSshInfraDelegateConfig(pdcInfrastructure, ambiance);
    PowerMockito.when(CommandStepUtils.getWorkingDirectory(eq(workingDirParam), any(ScriptType.class), anyBoolean()))
        .thenReturn(workingDir);
    PowerMockito.when(CommandStepUtils.mergeEnvironmentVariables(eq(env), any())).thenReturn(taskEnv);
    CommandTaskParameters taskParameters = helper.buildCommandTaskParameters(ambiance, stepParameters);
    assertThat(taskParameters).isInstanceOf(SshCommandTaskParameters.class);
    SshCommandTaskParameters sshTaskParameters = (SshCommandTaskParameters) taskParameters;

    assertScriptTaskParameters(taskParameters, taskEnv);
    assertThat(sshTaskParameters.getSshInfraDelegateConfig()).isEqualTo(pdcSshInfraDelegateConfig);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testBuildCopySshCommandTaskParameters() {
    Map<String, Object> env = new LinkedHashMap<>();
    env.put("key", "val");

    Map<String, String> taskEnv = new LinkedHashMap<>();
    env.put("key", "val");

    ParameterField workingDirParam = ParameterField.createValueField(workingDir);
    CommandStepParameters stepParameters = buildCopyCommandStepParams(env);

    doReturn(sshServiceOutcome)
        .when(outcomeService)
        .resolve(eq(ambiance), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE)));
    doReturn(pdcSshOptionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(eq(ambiance),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                OutputExpressionConstants.SSH_INFRA_DELEGATE_CONFIG_OUTPUT_NAME)));
    doReturn(pdcSshInfraDelegateConfig).when(sshEntityHelper).getSshInfraDelegateConfig(pdcInfrastructure, ambiance);
    PowerMockito.when(CommandStepUtils.mergeEnvironmentVariables(eq(env), any())).thenReturn(taskEnv);
    CommandTaskParameters taskParameters = helper.buildCommandTaskParameters(ambiance, stepParameters);
    assertThat(taskParameters).isInstanceOf(SshCommandTaskParameters.class);
    SshCommandTaskParameters sshTaskParameters = (SshCommandTaskParameters) taskParameters;

    assertCopyTaskParameters(taskParameters, taskEnv);
    assertThat(sshTaskParameters.getSshInfraDelegateConfig()).isEqualTo(pdcSshInfraDelegateConfig);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testBuildScriptWinRmCommandTaskParameters() {
    Map<String, Object> env = new LinkedHashMap<>();
    env.put("key", "val");

    Map<String, String> taskEnv = new LinkedHashMap<>();
    env.put("key", "val");

    CommandStepParameters stepParameters = buildScriptCommandStepParams(env);

    doReturn(winRmServiceOutcome)
        .when(outcomeService)
        .resolve(eq(ambiance), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE)));
    doReturn(pdcWinRmOptionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(eq(ambiance),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                OutputExpressionConstants.WINRM_INFRA_DELEGATE_CONFIG_OUTPUT_NAME)));
    doReturn(pdcWinRmInfraDelegateConfig)
        .when(sshEntityHelper)
        .getWinRmInfraDelegateConfig(pdcInfrastructure, ambiance);
    PowerMockito.when(CommandStepUtils.getWorkingDirectory(eq(workingDirParam), any(ScriptType.class), anyBoolean()))
        .thenReturn(workingDir);
    PowerMockito.when(CommandStepUtils.mergeEnvironmentVariables(eq(env), any())).thenReturn(taskEnv);
    CommandTaskParameters taskParameters = helper.buildCommandTaskParameters(ambiance, stepParameters);
    assertThat(taskParameters).isInstanceOf(WinrmTaskParameters.class);
    WinrmTaskParameters winrmTaskParameters = (WinrmTaskParameters) taskParameters;

    assertThat(winrmTaskParameters.getWinRmInfraDelegateConfig()).isEqualTo(pdcWinRmInfraDelegateConfig);
    assertScriptTaskParameters(taskParameters, taskEnv);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testBuildCopyWinRmCommandTaskParameters() {
    Map<String, Object> env = new LinkedHashMap<>();
    env.put("key", "val");

    Map<String, String> taskEnv = new LinkedHashMap<>();
    env.put("key", "val");

    ParameterField workingDirParam = ParameterField.createValueField(workingDir);
    CommandStepParameters stepParameters = buildCopyCommandStepParams(env);

    doReturn(winRmServiceOutcome)
        .when(outcomeService)
        .resolve(eq(ambiance), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE)));
    doReturn(pdcWinRmOptionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(eq(ambiance),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                OutputExpressionConstants.WINRM_INFRA_DELEGATE_CONFIG_OUTPUT_NAME)));
    doReturn(pdcWinRmInfraDelegateConfig)
        .when(sshEntityHelper)
        .getWinRmInfraDelegateConfig(pdcInfrastructure, ambiance);
    PowerMockito.when(CommandStepUtils.mergeEnvironmentVariables(eq(env), any())).thenReturn(taskEnv);
    CommandTaskParameters taskParameters = helper.buildCommandTaskParameters(ambiance, stepParameters);
    assertThat(taskParameters).isInstanceOf(WinrmTaskParameters.class);
    WinrmTaskParameters winRmTaskParameters = (WinrmTaskParameters) taskParameters;

    assertCopyTaskParameters(taskParameters, taskEnv);
    assertThat(winRmTaskParameters.getWinRmInfraDelegateConfig()).isEqualTo(pdcWinRmInfraDelegateConfig);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testHandleTaskException() throws Exception {
    RuntimeException runtimeException = new RuntimeException("Failed to execute the task");

    doReturn(ngLogCallback).when(helper).getLogCallback(any(), any(), anyBoolean());

    StepResponse response = helper.handleTaskException(ambiance,
        StepElementParameters.builder().spec(buildScriptCommandStepParams(Collections.emptyMap())).build(),
        runtimeException);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getUnitProgressList()).isNotEmpty();
    assertThat(response.getUnitProgressList().get(0).getUnitName()).isEqualTo("Execute");
    assertThat(response.getUnitProgressList().get(0).getStatus()).isEqualTo(UnitStatus.FAILURE);
    assertThat(response.getFailureInfo()).isNotNull();
    assertThat(response.getFailureInfo().getErrorMessage()).isEqualTo("RuntimeException: Failed to execute the task");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testHandleTaskDataException() {
    TaskNGDataException taskNGDataException =
        new TaskNGDataException(UnitProgressData.builder().build(), new RuntimeException("Failure"));

    assertThatThrownBy(
        ()
            -> helper.handleTaskException(ambiance,
                StepElementParameters.builder().spec(buildScriptCommandStepParams(Collections.emptyMap())).build(),
                taskNGDataException))
        .isInstanceOf(TaskNGDataException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testBuildCommandTaskParametersCustomDeployment() {
    Map<String, Object> env = new LinkedHashMap<>();
    env.put("key", "val");

    Map<String, String> taskEnv = new LinkedHashMap<>();
    env.put("key", "val");

    CommandStepParameters stepParameters = buildCopyCommandStepParams(env);

    doReturn(
        ServiceStepOutcome.builder().type(ServiceSpecType.CUSTOM_DEPLOYMENT).name("DeploymentTemplateService").build())
        .when(outcomeService)
        .resolve(eq(ambiance), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE)));

    EmptyHostDelegateConfig emptyHostDelegateConfig = EmptyHostDelegateConfig.builder().build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder()
            .found(true)
            .output(SshInfraDelegateConfigOutput.builder().sshInfraDelegateConfig(emptyHostDelegateConfig).build())
            .build();

    doReturn(optionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(eq(ambiance),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                OutputExpressionConstants.SSH_INFRA_DELEGATE_CONFIG_OUTPUT_NAME)));
    PowerMockito.when(CommandStepUtils.mergeEnvironmentVariables(eq(env), any())).thenReturn(taskEnv);
    CommandTaskParameters taskParameters = helper.buildCommandTaskParameters(ambiance, stepParameters);
    assertThat(taskParameters).isInstanceOf(SshCommandTaskParameters.class);
    SshCommandTaskParameters sshTaskParameters = (SshCommandTaskParameters) taskParameters;
    assertThat(sshTaskParameters.getSshInfraDelegateConfig()).isNotNull();
    SshInfraDelegateConfig sshInfraDelegateConfig = sshTaskParameters.getSshInfraDelegateConfig();
    assertThat(sshInfraDelegateConfig).isInstanceOf(EmptyHostDelegateConfig.class);

    assertCopyTaskParameters(taskParameters, taskEnv);
    assertThat(sshTaskParameters.getSshInfraDelegateConfig()).isEqualTo(emptyHostDelegateConfig);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetShellScriptFromHarnessFileStoreWithEmptyContent() {
    String scopedFilePath = "account:/folder1/folder2/emptyScript";
    ShellScriptSourceWrapper shellScriptSourceWrapper =
        ShellScriptSourceWrapper.builder()
            .type(ShellScriptBaseSource.HARNESS)
            .spec(HarnessFileStoreSource.builder().file(ParameterField.createValueField(scopedFilePath)).build())
            .build();
    ShellScriptBaseSource spec = shellScriptSourceWrapper.getSpec();

    when(cdExpressionResolver.updateExpressions(ambiance, spec)).thenReturn(spec);
    when(
        sshWinRmConfigFileHelper.fetchFileContent(FileReference.of(scopedFilePath, AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance))))
        .thenReturn("");

    assertThatThrownBy(() -> helper.getShellScript(ambiance, shellScriptSourceWrapper))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(format(FILE_STORE_SCRIPT_ERROR_MSG, scopedFilePath));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testPrepareSshWinRmRollbackData() {
    ExecutionInfoKey executionInfoKey = ExecutionInfoKey.builder().build();
    when(commandStepRollbackHelper.getExecutionInfoKey(ambiance)).thenReturn(executionInfoKey);

    ExecutionDetails executionDetails = SshWinRmStageExecutionDetails.builder().build();
    StageExecutionInfo stageExecutionInfo =
        StageExecutionInfo.builder().uuid("someid").executionDetails(executionDetails).build();

    when(commandStepRollbackHelper.getLatestSuccessfulStageExecutionInfo(any(), any()))
        .thenReturn(Optional.of(stageExecutionInfo));

    helper.prepareSshWinRmRollbackData(ambiance);

    verify(executionSweepingOutputService)
        .consume(
            eq(ambiance), eq(OutcomeExpressionConstants.SSH_WINRM_PREPARE_ROLLBACK_DATA_OUTCOME), any(), anyString());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetSshWinRmRollbackData_shouldDeletePhantomStageExecutionInfo() {
    Map<String, String> mergedEnvVariables = new HashMap<>();
    CommandStepParameters commandStepParameters = CommandStepParameters.infoBuilder().build();

    when(commandStepRollbackHelper.getRollbackData(any(), any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> helper.getSshWinRmRollbackData(ambiance, mergedEnvVariables, commandStepParameters))
        .isInstanceOf(SkipRollbackException.class)
        .hasMessage("Not found previous successful rollback data, hence skipping rollback");

    ArgumentCaptor<Ambiance> argumentCaptor = ArgumentCaptor.forClass(Ambiance.class);
    verify(commandStepRollbackHelper, times(1)).deleteIfExistsCurrentStageExecutionInfo(argumentCaptor.capture());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetMergedEnvVariablesMap() {
    LinkedHashMap<String, Object> evaluatedStageVariables =
        new LinkedHashMap<>(Map.of("var1", "value1s", "var2", "value2s"));
    LinkedHashMap<String, Object> evaluatedPipelineVariables =
        new LinkedHashMap<>(Map.of("var1", "value1p", "var3", "value3p"));
    LinkedHashMap<String, Object> envVariables = new LinkedHashMap<>(Map.of("var4", "value4"));
    LinkedHashMap<String, Object> taskParamEnvVariables = new LinkedHashMap<>(Map.of("var5", "value5"));

    PdcInfrastructureOutcome pdcInfrastructureOutcome =
        PdcInfrastructureOutcome.builder()
            .environment(EnvironmentOutcome.builder().variables(envVariables).build())
            .build();

    CommandStepParameters commandStepParameters =
        CommandStepParameters.infoBuilder().environmentVariables(taskParamEnvVariables).build();

    doReturn(evaluatedStageVariables)
        .when(cdExpressionResolver)
        .evaluateExpression(any(), eq("<+stage.variables>"), any());
    doReturn(evaluatedPipelineVariables)
        .when(cdExpressionResolver)
        .evaluateExpression(any(), eq("<+pipeline.variables>"), any());

    PowerMockito.when(CommandStepUtils.mergeEnvironmentVariables(eq(evaluatedStageVariables), any(Map.class)))
        .thenReturn(evaluatedStageVariables);

    PowerMockito.when(CommandStepUtils.mergeEnvironmentVariables(eq(evaluatedPipelineVariables), any(Map.class)))
        .thenReturn(new HashMap() {
          {
            putAll(evaluatedStageVariables);
            putAll(evaluatedPipelineVariables);
          }
        });

    PowerMockito
        .when(CommandStepUtils.mergeEnvironmentVariables(
            eq(commandStepParameters.getEnvironmentVariables()), any(Map.class)))
        .thenReturn(new HashMap() {
          {
            putAll(evaluatedStageVariables);
            putAll(evaluatedPipelineVariables);
            putAll(envVariables);
            putAll(taskParamEnvVariables);
          }
        });

    Map<String, String> result =
        helper.getMergedEnvVariablesMap(ambiance, commandStepParameters, pdcInfrastructureOutcome);

    assertThat(result.get("var1")).isEqualTo("value1p");
    assertThat(result.get("var2")).isEqualTo("value2s");
    assertThat(result.get("var3")).isEqualTo("value3p");
    assertThat(result.get("var4")).isEqualTo("value4");
    assertThat(result.get("var5")).isEqualTo("value5");
  }

  private void assertScriptTaskParameters(CommandTaskParameters taskParameters, Map<String, String> taskEnv) {
    assertThat(taskParameters).isNotNull();
    assertThat(taskParameters.getAccountId()).isEqualTo(accountId);
    assertThat(taskParameters.getCommandUnits()).isNotEmpty();
    assertThat(taskParameters.getCommandUnits().size()).isEqualTo(3);
    NgCommandUnit commandUnit = taskParameters.getCommandUnits()
                                    .stream()
                                    .filter(cu -> cu.getCommandUnitType().equals(NGCommandUnitType.SCRIPT))
                                    .findFirst()
                                    .get();
    assertThat(commandUnit).isInstanceOf(ScriptCommandUnit.class);
    ScriptCommandUnit scriptCommandUnit = (ScriptCommandUnit) commandUnit;
    assertThat(scriptCommandUnit.getWorkingDirectory()).isEqualTo(workingDir);
    assertThat(scriptCommandUnit.getScript()).isEqualTo("echo Test");
    assertThat(scriptCommandUnit.getScriptType()).isEqualTo(ScriptType.BASH);
    assertThat(scriptCommandUnit.getTailFilePatterns().get(0).getFilePath()).isEqualTo("nohup.out");
    assertThat(scriptCommandUnit.getTailFilePatterns().get(0).getPattern()).isEqualTo("*Successfull");
    assertThat(taskParameters.getEnvironmentVariables()).isEqualTo(taskEnv);
  }

  private void assertCopyTaskParameters(CommandTaskParameters taskParameters, Map<String, String> taskEnv) {
    assertThat(taskParameters).isNotNull();
    assertThat(taskParameters.getFileDelegateConfig()).isNotNull();
    assertThat(taskParameters.getFileDelegateConfig().getStores()).isNotEmpty();
    StoreDelegateConfig storeDelegateConfig = taskParameters.getFileDelegateConfig().getStores().get(0);
    assertThat(storeDelegateConfig).isInstanceOf(HarnessStoreDelegateConfig.class);
    HarnessStoreDelegateConfig harnessStoreDelegateConfig = (HarnessStoreDelegateConfig) storeDelegateConfig;
    assertThat(harnessStoreDelegateConfig.getConfigFiles()).isNotEmpty();
    assertThat(harnessStoreDelegateConfig.getConfigFiles().size()).isEqualTo(2);
    assertThat(harnessStoreDelegateConfig.getConfigFiles().get(0).getFileContent()).isEqualTo("Hello World");
    assertThat(harnessStoreDelegateConfig.getConfigFiles().get(0).getFileName()).isEqualTo("test.txt");
    assertThat(harnessStoreDelegateConfig.getConfigFiles().get(0).getFileSize()).isEqualTo(11L);
    assertThat(harnessStoreDelegateConfig.getConfigFiles().get(1).getFileContent()).isNull();
    assertThat(harnessStoreDelegateConfig.getConfigFiles().get(1).getFileName()).isEqualTo("secret-ref");
    assertThat(harnessStoreDelegateConfig.getConfigFiles().get(1).getFileSize()).isEqualTo(0L);
    assertThat(harnessStoreDelegateConfig.getConfigFiles().get(1).isEncrypted()).isTrue();
    SecretConfigFile secret = harnessStoreDelegateConfig.getConfigFiles().get(1).getSecretConfigFile();
    assertThat(secret.getEncryptedConfigFile()).isNotNull();

    assertThat(taskParameters.getFileDelegateConfig().getStores()).isNotEmpty();
    assertThat(taskParameters.getAccountId()).isEqualTo(accountId);
    assertThat(taskParameters.getCommandUnits()).isNotEmpty();
    assertThat(taskParameters.getCommandUnits().size()).isEqualTo(4);
    NgCommandUnit copyConfigCommandUnit =
        taskParameters.getCommandUnits().stream().filter(cu -> cu.getName().equals("copy-config")).findFirst().get();
    assertThat(copyConfigCommandUnit).isInstanceOf(CopyCommandUnit.class);
    CopyCommandUnit ccCommandUnit = (CopyCommandUnit) copyConfigCommandUnit;
    assertThat(ccCommandUnit.getSourceType()).isEqualTo(FileSourceType.CONFIG);
    assertThat(ccCommandUnit.getDestinationPath()).isEqualTo("tmp");
    NgCommandUnit copyArtifactCommandUnit =
        taskParameters.getCommandUnits().stream().filter(cu -> cu.getName().equals("copy-artifact")).findFirst().get();
    assertThat(copyArtifactCommandUnit).isInstanceOf(CopyCommandUnit.class);
    CopyCommandUnit caCommandUnit = (CopyCommandUnit) copyArtifactCommandUnit;
    assertThat(caCommandUnit.getSourceType()).isEqualTo(FileSourceType.ARTIFACT);
    assertThat(caCommandUnit.getDestinationPath()).isEqualTo("tmp");

    assertThat(taskParameters.getEnvironmentVariables()).isEqualTo(taskEnv);
  }

  private CommandStepParameters buildScriptCommandStepParams(Map<String, Object> env) {
    return CommandStepParameters.infoBuilder()
        .commandUnits(Arrays.asList(
            CommandUnitWrapper.builder()
                .type(CommandUnitSpecType.SCRIPT)
                .name("Execute")
                .spec(ScriptCommandUnitSpec.builder()
                          .tailFiles(Arrays.asList(TailFilePattern.builder()
                                                       .tailFile(ParameterField.createValueField("nohup.out"))
                                                       .tailPattern(ParameterField.createValueField("*Successfull"))
                                                       .build()))
                          .shell(ShellType.Bash)
                          .workingDirectory(workingDirParam)
                          .source(ShellScriptSourceWrapper.builder()
                                      .spec(ShellScriptInlineSource.builder()
                                                .script(ParameterField.createValueField("echo Test"))
                                                .build())
                                      .type("Inline")
                                      .build())
                          .build())
                .build()))
        .environmentVariables(env)
        .delegateSelectors(ParameterField.createValueField(Arrays.asList(new TaskSelectorYaml("ssh-delegate"))))
        .onDelegate(ParameterField.createValueField(false))
        .build();
  }

  private CommandStepParameters buildCopyCommandStepParams(Map<String, Object> env) {
    return CommandStepParameters.infoBuilder()
        .commandUnits(Arrays.asList(CommandUnitWrapper
                                        .builder()

                                        .name("copy-config")
                                        .type(CommandUnitSpecType.COPY)
                                        .spec(CopyCommandUnitSpec.builder()
                                                  .sourceType(CommandUnitSourceType.Config)
                                                  .destinationPath(ParameterField.createValueField("tmp"))
                                                  .build())

                                        .build(),
            CommandUnitWrapper
                .builder()

                .name("copy-artifact")
                .type(CommandUnitSpecType.COPY)
                .spec(CopyCommandUnitSpec.builder()
                          .sourceType(CommandUnitSourceType.Artifact)
                          .destinationPath(ParameterField.createValueField("tmp"))
                          .build())

                .build()))
        .environmentVariables(env)
        .delegateSelectors(ParameterField.createValueField(Arrays.asList(new TaskSelectorYaml("ssh-delegate"))))
        .onDelegate(ParameterField.createValueField(false))
        .build();
  }
}
