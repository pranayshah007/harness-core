/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.outcome.ArtifactoryGenericArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.configfile.ConfigFileOutcome;
import io.harness.cdng.configfile.steps.ConfigFilesOutcome;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStoreFile;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.storeconfig.HarnessStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.task.shell.SshCommandTaskParameters;
import io.harness.delegate.task.ssh.CopyCommandUnit;
import io.harness.delegate.task.ssh.NGCommandUnitType;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.PdcSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.contracts.refobjects.RefType;
import io.harness.pms.data.OrchestrationRefType;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.shell.ScriptType;
import io.harness.ssh.FileSourceType;
import io.harness.steps.shellscript.ShellScriptHelperService;
import io.harness.steps.shellscript.ShellScriptInlineSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.steps.shellscript.ShellType;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class SshCommandStepHelperTest extends CategoryTest {
  @Mock private ShellScriptHelperService shellScriptHelperService;
  @Mock private SshEntityHelper sshEntityHelper;
  @Mock private OutcomeService outcomeService;
  @Mock private FileStoreService fileStoreService;
  @Mock private NGEncryptedDataService ngEncryptedDataService;

  @InjectMocks private SshCommandStepHelper helper;

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
      PdcInfrastructureOutcome.builder().connectorRef("pdcConnector").credentialsRef("sshKeyRef").build();
  private final OptionalOutcome pdcInfrastructureOutcome =
      OptionalOutcome.builder()
          .found(true)
          .outcome(PdcInfrastructureOutcome.builder()
                       .credentialsRef(pdcInfrastructure.getCredentialsRef())
                       .connectorRef(pdcInfrastructure.getConnectorRef())
                       .build())
          .build();

  private final ArtifactoryGenericArtifactOutcome artifactoryArtifact =
      ArtifactoryGenericArtifactOutcome.builder().connectorRef("artifactoryConnector").repositoryName("test").build();

  private final OptionalOutcome artifactOutcome =
      OptionalOutcome.builder()
          .found(true)
          .outcome(ArtifactsOutcome.builder().primary(artifactoryArtifact).build())
          .build();

  private final ConfigFilesOutcome configFilesOutCm = new ConfigFilesOutcome();

  private final OptionalOutcome configFilesOutcome =
      OptionalOutcome.builder().found(true).outcome(configFilesOutCm).build();

  private final PdcSshInfraDelegateConfig pdcSshInfraDelegateConfig =
      PdcSshInfraDelegateConfig.builder().hosts(Arrays.asList("host1")).build();
  private final ArtifactoryArtifactDelegateConfig artifactDelegateConfig =
      ArtifactoryArtifactDelegateConfig.builder().build();
  private final ParameterField workingDirParam = ParameterField.createValueField(workingDir);

  @Before
  public void prepare() {
    MockitoAnnotations.initMocks(this);
    configFilesOutCm.put("test",
        ConfigFileOutcome.builder()
            .identifier("test")
            .store(HarnessStore.builder()
                       .files(ParameterField.createValueField(
                           Arrays.asList(HarnessStoreFile.builder()
                                             .ref(ParameterField.createValueField("config-file"))
                                             .path(ParameterField.createValueField("fs"))
                                             .isEncrypted(ParameterField.createValueField(false))
                                             .build())))
                       .build())
            .build());
    doReturn(pdcInfrastructureOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(infra));
    doReturn(pdcSshInfraDelegateConfig).when(sshEntityHelper).getSshInfraDelegateConfig(pdcInfrastructure, ambiance);
    doReturn(artifactOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(artifact));
    doReturn(configFilesOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(configFiles));
    doReturn(artifactDelegateConfig)
        .when(sshEntityHelper)
        .getArtifactDelegateConfigConfig(artifactoryArtifact, ambiance);

    doReturn(Optional.of(FileNodeDTO.builder().content("Hello World").name("test.txt").build()))
        .when(fileStoreService)
        .get(any(), any(), any(), any(), anyBoolean());
    doReturn(workingDir)
        .when(shellScriptHelperService)
        .getWorkingDirectory(eq(workingDirParam), any(ScriptType.class), anyBoolean());
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

    doReturn(workingDir)
        .when(shellScriptHelperService)
        .getWorkingDirectory(eq(workingDirParam), any(ScriptType.class), anyBoolean());
    doReturn(taskEnv).when(shellScriptHelperService).getEnvironmentVariables(env);
    SshCommandTaskParameters taskParameters = helper.buildSshCommandTaskParameters(ambiance, stepParameters);
    assertThat(taskParameters).isNotNull();
    assertThat(taskParameters.getSshInfraDelegateConfig()).isEqualTo(pdcSshInfraDelegateConfig);
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

    doReturn(taskEnv).when(shellScriptHelperService).getEnvironmentVariables(env);
    SshCommandTaskParameters taskParameters = helper.buildSshCommandTaskParameters(ambiance, stepParameters);

    assertThat(taskParameters).isNotNull();
    assertThat(taskParameters.getSshInfraDelegateConfig()).isEqualTo(pdcSshInfraDelegateConfig);
    assertThat(taskParameters.getFileDelegateConfig()).isNotNull();
    assertThat(taskParameters.getFileDelegateConfig().getStores()).isNotEmpty();
    StoreDelegateConfig storeDelegateConfig = taskParameters.getFileDelegateConfig().getStores().get(0);
    assertThat(storeDelegateConfig).isInstanceOf(HarnessStoreDelegateConfig.class);
    HarnessStoreDelegateConfig harnessStoreDelegateConfig = (HarnessStoreDelegateConfig) storeDelegateConfig;
    assertThat(harnessStoreDelegateConfig.getConfigFiles()).isNotEmpty();
    assertThat(harnessStoreDelegateConfig.getConfigFiles().get(0).getFileContent()).isEqualTo("Hello World");
    assertThat(harnessStoreDelegateConfig.getConfigFiles().get(0).getFileName()).isEqualTo("test.txt");
    assertThat(harnessStoreDelegateConfig.getConfigFiles().get(0).getFileSize()).isEqualTo(11L);

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
                .commandUnit(StepCommandUnit.builder()
                                 .type(CommandUnitSpecType.SCRIPT)
                                 .spec(ScriptCommandUnitSpec.builder()
                                           .tailFiles(Arrays.asList(
                                               TailFilePattern.builder()
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
                                 .build())
                .build()))
        .environmentVariables(env)
        .delegateSelectors(ParameterField.createValueField(Arrays.asList(new TaskSelectorYaml("ssh-delegate"))))
        .onDelegate(ParameterField.createValueField(false))
        .build();
  }

  private CommandStepParameters buildCopyCommandStepParams(Map<String, Object> env) {
    return CommandStepParameters.infoBuilder()
        .commandUnits(
            Arrays.asList(CommandUnitWrapper.builder()
                              .commandUnit(StepCommandUnit.builder()
                                               .name("copy-config")
                                               .type(CommandUnitSpecType.COPY)
                                               .spec(CopyCommandUnitSpec.builder()
                                                         .sourceType(CommandUnitSourceType.Config)
                                                         .destinationPath(ParameterField.createValueField("tmp"))
                                                         .build())
                                               .build())
                              .build(),
                CommandUnitWrapper.builder()
                    .commandUnit(StepCommandUnit.builder()
                                     .name("copy-artifact")
                                     .type(CommandUnitSpecType.COPY)
                                     .spec(CopyCommandUnitSpec.builder()
                                               .sourceType(CommandUnitSourceType.Artifact)
                                               .destinationPath(ParameterField.createValueField("tmp"))
                                               .build())
                                     .build())
                    .build()))
        .environmentVariables(env)
        .delegateSelectors(ParameterField.createValueField(Arrays.asList(new TaskSelectorYaml("ssh-delegate"))))
        .onDelegate(ParameterField.createValueField(false))
        .build();
  }
}
