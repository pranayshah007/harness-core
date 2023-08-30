/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.template;
import static io.harness.data.structure.CollectionUtils.distinctByKey;
import static io.harness.ngmigration.utils.MigratorUtility.RUNTIME_BOOLEAN_INPUT;
import static io.harness.ngmigration.utils.MigratorUtility.RUNTIME_DELEGATE_INPUT;

import static software.wings.beans.command.CommandUnitType.COPY_CONFIGS;
import static software.wings.beans.command.CommandUnitType.DOCKER_START;
import static software.wings.beans.command.CommandUnitType.DOCKER_STOP;
import static software.wings.beans.command.CommandUnitType.DOWNLOAD_ARTIFACT;
import static software.wings.beans.command.CommandUnitType.EXEC;
import static software.wings.beans.command.CommandUnitType.PORT_CHECK_CLEARED;
import static software.wings.beans.command.CommandUnitType.PORT_CHECK_LISTENING;
import static software.wings.beans.command.CommandUnitType.PROCESS_CHECK_RUNNING;
import static software.wings.beans.command.CommandUnitType.PROCESS_CHECK_STOPPED;
import static software.wings.beans.command.CommandUnitType.SCP;
import static software.wings.beans.command.CommandUnitType.SETUP_ENV;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.ssh.CommandStepInfo;
import io.harness.cdng.ssh.CommandUnitSourceType;
import io.harness.cdng.ssh.CommandUnitSpecType;
import io.harness.cdng.ssh.CommandUnitWrapper;
import io.harness.cdng.ssh.CopyCommandUnitSpec;
import io.harness.cdng.ssh.DownloadArtifactCommandUnitSpec;
import io.harness.cdng.ssh.ScriptCommandUnitSpec;
import io.harness.cdng.ssh.TailFilePattern;
import io.harness.data.structure.EmptyPredicate;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;
import io.harness.shell.ScriptType;
import io.harness.steps.shellscript.ShellScriptBaseSource;
import io.harness.steps.shellscript.ShellScriptInlineSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.steps.shellscript.ShellType;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.JsonPipelineUtils;

import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitType;
import software.wings.beans.command.CopyConfigCommandUnit;
import software.wings.beans.command.DockerStartCommandUnit;
import software.wings.beans.command.DockerStopCommandUnit;
import software.wings.beans.command.DownloadArtifactCommandUnit;
import software.wings.beans.command.ExecCommandUnit;
import software.wings.beans.command.PortCheckClearedCommandUnit;
import software.wings.beans.command.PortCheckListeningCommandUnit;
import software.wings.beans.command.ProcessCheckRunningCommandUnit;
import software.wings.beans.command.ProcessCheckStoppedCommandUnit;
import software.wings.beans.command.ScpCommandUnit;
import software.wings.beans.command.SetupEnvCommandUnit;
import software.wings.beans.template.Template;
import software.wings.beans.template.command.SshCommandTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public class ServiceCommandTemplateService implements NgTemplateService {
  private static final Set<CommandUnitType> SUPPORTED_COMMAND_UNITS =
      Sets.newHashSet(SCP, COPY_CONFIGS, EXEC, DOWNLOAD_ARTIFACT, SETUP_ENV, DOCKER_START, DOCKER_STOP,
          PORT_CHECK_CLEARED, PORT_CHECK_LISTENING, PROCESS_CHECK_RUNNING, PROCESS_CHECK_STOPPED);

  @Override
  public boolean isMigrationSupported() {
    return true;
  }

  @Override
  public JsonNode getNgTemplateConfigSpec(
      MigrationContext context, Template template, String orgIdentifier, String projectIdentifier) {
    SshCommandTemplate sshCommandTemplate = (SshCommandTemplate) template.getTemplateObject();
    if (EmptyPredicate.isEmpty(sshCommandTemplate.getCommandUnits())
        || !sshCommandTemplate.getCommandUnits().stream().allMatch(
            commandUnit -> SUPPORTED_COMMAND_UNITS.contains(commandUnit.getCommandUnitType()))) {
      return null;
    }

    List<NGVariable> variables = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(template.getVariables())) {
      variables.addAll(MigratorUtility.getVariables(context, template.getVariables()));
    }

    List<CommandUnitWrapper> commandUnitWrappers = sshCommandTemplate.getCommandUnits()
                                                       .stream()
                                                       .map(commandUnit -> handleCommandUnit(context, commandUnit))
                                                       .filter(Objects::nonNull)
                                                       .collect(Collectors.toList());

    updateTemplateSpecWithNewExpressions(context, commandUnitWrappers, variables);

    CommandStepInfo commandStepInfo = CommandStepInfo.infoBuilder()
                                          .commandUnits(commandUnitWrappers)
                                          .onDelegate(RUNTIME_BOOLEAN_INPUT)
                                          .delegateSelectors(RUNTIME_DELEGATE_INPUT)
                                          .environmentVariables(variables)
                                          .build();

    return JsonPipelineUtils.asTree(commandStepInfo);
  }

  private CommandUnitWrapper handleCommandUnit(MigrationContext context, CommandUnit commandUnit) {
    CommandUnitType type = commandUnit.getCommandUnitType();
    String name = commandUnit.getName();
    switch (type) {
      case SCP:
        return handleScp(commandUnit, context.getInputDTO().getIdentifierCaseFormat());
      case EXEC:
        return handleExec(context, commandUnit);
      case COPY_CONFIGS:
        return handleCopyConfigs(commandUnit, context.getInputDTO().getIdentifierCaseFormat());
      case DOWNLOAD_ARTIFACT:
        return handleDownloadArtifact(commandUnit, context.getInputDTO().getIdentifierCaseFormat());
      case SETUP_ENV:
        SetupEnvCommandUnit setup = (SetupEnvCommandUnit) commandUnit;
        return getExec(context, name, setup.getScriptType(), setup.getCommandString(), setup.getCommandPath());
      case DOCKER_START:
        DockerStartCommandUnit dockerStart = (DockerStartCommandUnit) commandUnit;
        return getExec(context, name, dockerStart.getScriptType(), dockerStart.getCommandString());
      case DOCKER_STOP:
        DockerStopCommandUnit dockerStop = (DockerStopCommandUnit) commandUnit;
        return getExec(context, name, dockerStop.getScriptType(), dockerStop.getCommandString());
      case PROCESS_CHECK_RUNNING:
        ProcessCheckRunningCommandUnit processRunning = (ProcessCheckRunningCommandUnit) commandUnit;
        return getExec(context, name, processRunning.getScriptType(), processRunning.getCommandString());
      case PROCESS_CHECK_STOPPED:
        ProcessCheckStoppedCommandUnit processStopped = (ProcessCheckStoppedCommandUnit) commandUnit;
        return getExec(context, name, processStopped.getScriptType(), processStopped.getCommandString());
      case PORT_CHECK_CLEARED:
        PortCheckClearedCommandUnit portCleared = (PortCheckClearedCommandUnit) commandUnit;
        return getExec(context, name, portCleared.getScriptType(), portCleared.getCommandString());
      case PORT_CHECK_LISTENING:
        PortCheckListeningCommandUnit portListening = (PortCheckListeningCommandUnit) commandUnit;
        return getExec(context, name, portListening.getScriptType(), portListening.getCommandString());
      default:
        return null;
    }
  }

  private CommandUnitWrapper getExec(MigrationContext context, String name, ScriptType scriptType, String script) {
    return getExec(context, name, scriptType, script, null);
  }

  private CommandUnitWrapper getExec(
      MigrationContext context, String name, ScriptType scriptType, String script, String workingDir) {
    ParameterField<String> directory =
        StringUtils.isBlank(workingDir) ? ParameterField.ofNull() : ParameterField.createValueField(workingDir);
    script = (String) MigratorExpressionUtils.render(context, script, new HashMap<>());
    return CommandUnitWrapper.builder()
        .type(CommandUnitSpecType.SCRIPT)
        .name(name)
        .identifier(MigratorUtility.generateIdentifier(name, context.getInputDTO().getIdentifierCaseFormat()))
        .spec(ScriptCommandUnitSpec.builder()
                  .shell(ScriptType.POWERSHELL.equals(scriptType) ? ShellType.PowerShell : ShellType.Bash)
                  .tailFiles(Collections.emptyList())
                  .workingDirectory(directory)
                  .source(ShellScriptSourceWrapper.builder()
                              .type(ShellScriptBaseSource.INLINE)
                              .spec(ShellScriptInlineSource.builder().script(valueOrDefaultEmpty(script)).build())
                              .build())
                  .build())
        .build();
  }

  @Override
  public String getNgTemplateStepName(Template template) {
    return StepSpecTypeConstants.COMMAND;
  }

  static CommandUnitWrapper handleCopyConfigs(CommandUnit commandUnit, CaseFormat caseFormat) {
    CopyConfigCommandUnit configCommandUnit = (CopyConfigCommandUnit) commandUnit;
    return CommandUnitWrapper.builder()
        .type(CommandUnitSpecType.COPY)
        .name(commandUnit.getName())
        .identifier(MigratorUtility.generateIdentifier(commandUnit.getName(), caseFormat))
        .spec(CopyCommandUnitSpec.builder()
                  .destinationPath(valueOrDefaultEmpty(configCommandUnit.getDestinationParentPath()))
                  .sourceType(CommandUnitSourceType.Config)
                  .build())
        .build();
  }

  static CommandUnitWrapper handleDownloadArtifact(CommandUnit commandUnit, CaseFormat caseFormat) {
    DownloadArtifactCommandUnit downloadCommandUnit = (DownloadArtifactCommandUnit) commandUnit;
    return CommandUnitWrapper.builder()
        .type(CommandUnitSpecType.DOWNLOAD_ARTIFACT)
        .name(commandUnit.getName())
        .identifier(MigratorUtility.generateIdentifier(commandUnit.getName(), caseFormat))
        .spec(DownloadArtifactCommandUnitSpec.builder()
                  .destinationPath(valueOrDefaultEmpty(downloadCommandUnit.getCommandPath()))
                  .build())
        .build();
  }

  static CommandUnitWrapper handleScp(CommandUnit commandUnit, CaseFormat caseFormat) {
    ScpCommandUnit scpCommandUnit = (ScpCommandUnit) commandUnit;
    return CommandUnitWrapper.builder()
        .type(CommandUnitSpecType.COPY)
        .name(commandUnit.getName())
        .identifier(MigratorUtility.generateIdentifier(commandUnit.getName(), caseFormat))
        .spec(CopyCommandUnitSpec.builder()
                  .destinationPath(valueOrDefaultEmpty(scpCommandUnit.getDestinationDirectoryPath()))
                  .sourceType(CommandUnitSourceType.Artifact)
                  .build())
        .build();
  }

  static CommandUnitWrapper handleExec(MigrationContext context, CommandUnit commandUnit) {
    ExecCommandUnit execCommandUnit = (ExecCommandUnit) commandUnit;
    List<TailFilePattern> tailFilePatterns = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(execCommandUnit.getTailPatterns())) {
      tailFilePatterns = execCommandUnit.getTailPatterns()
                             .stream()
                             .map(pattern
                                 -> TailFilePattern.builder()
                                        .tailFile(valueOrDefaultEmpty(pattern.getFilePath()))
                                        .tailPattern(valueOrDefaultEmpty(pattern.getPattern()))
                                        .build())
                             .collect(Collectors.toList());
    }
    String script =
        (String) MigratorExpressionUtils.render(context, execCommandUnit.getCommandString(), new HashMap<>());

    return CommandUnitWrapper.builder()
        .type(CommandUnitSpecType.SCRIPT)
        .name(commandUnit.getName())
        .identifier(
            MigratorUtility.generateIdentifier(commandUnit.getName(), context.getInputDTO().getIdentifierCaseFormat()))
        .spec(
            ScriptCommandUnitSpec.builder()
                .shell(execCommandUnit.getScriptType().equals(ScriptType.BASH) ? ShellType.Bash : ShellType.PowerShell)
                .tailFiles(tailFilePatterns)
                .workingDirectory(StringUtils.isBlank(execCommandUnit.getCommandPath())
                        ? ParameterField.ofNull()
                        : valueOrDefaultEmpty(execCommandUnit.getCommandPath()))
                .source(ShellScriptSourceWrapper.builder()
                            .type(ShellScriptBaseSource.INLINE)
                            .spec(ShellScriptInlineSource.builder().script(valueOrDefaultEmpty(script)).build())
                            .build())
                .build())
        .build();
  }

  static ParameterField<String> valueOrDefaultEmpty(String val) {
    return ParameterField.createValueField(StringUtils.isNotBlank(val) ? val : "");
  }

  static void updateTemplateSpecWithNewExpressions(
      MigrationContext context, List<CommandUnitWrapper> commandUnitWrappers, List<NGVariable> variables) {
    // More details here: https://harness.atlassian.net/browse/CDS-73356
    if (EmptyPredicate.isEmpty(variables)) {
      return;
    }
    Map<String, Object> customExpressions =
        variables.stream()
            .filter(distinctByKey(NGVariable::getName))
            .map(NGVariable::getName)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toMap(s -> s, s -> String.format("<+spec.environmentVariables.%s>", s)));

    MigratorExpressionUtils.render(context, commandUnitWrappers, customExpressions);
  }
}
