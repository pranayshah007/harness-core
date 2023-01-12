/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.template;

import static software.wings.beans.command.CommandUnitType.COPY_CONFIGS;
import static software.wings.beans.command.CommandUnitType.DOWNLOAD_ARTIFACT;
import static software.wings.beans.command.CommandUnitType.EXEC;
import static software.wings.beans.command.CommandUnitType.SCP;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
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
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.shell.ScriptType;
import io.harness.steps.shellscript.ShellScriptBaseSource;
import io.harness.steps.shellscript.ShellScriptInlineSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.steps.shellscript.ShellType;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;
import io.harness.yaml.utils.JsonPipelineUtils;

import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitType;
import software.wings.beans.command.CopyConfigCommandUnit;
import software.wings.beans.command.DownloadArtifactCommandUnit;
import software.wings.beans.command.ExecCommandUnit;
import software.wings.beans.command.ScpCommandUnit;
import software.wings.beans.template.Template;
import software.wings.beans.template.command.ShellScriptTemplate;
import software.wings.beans.template.command.SshCommandTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public class ServiceCommandTemplateService implements NgTemplateService {
  private static final Set<CommandUnitType> SUPPORTED_COMMAND_UNITS =
      Sets.newHashSet(SCP, COPY_CONFIGS, EXEC, DOWNLOAD_ARTIFACT);

  @Override
  public Set<String> getExpressions(Template template) {
    ShellScriptTemplate shellScriptTemplate = (ShellScriptTemplate) template.getTemplateObject();
    if (StringUtils.isBlank(shellScriptTemplate.getScriptString())) {
      return Collections.emptySet();
    }
    return MigratorExpressionUtils.extractAll(shellScriptTemplate.getScriptString());
  }

  @Override
  public boolean isMigrationSupported() {
    return true;
  }

  @Override
  public JsonNode getNgTemplateConfigSpec(Template template, String orgIdentifier, String projectIdentifier) {
    SshCommandTemplate sshCommandTemplate = (SshCommandTemplate) template.getTemplateObject();
    if (EmptyPredicate.isEmpty(sshCommandTemplate.getCommandUnits())
        || !sshCommandTemplate.getCommandUnits().stream().allMatch(
            commandUnit -> SUPPORTED_COMMAND_UNITS.contains(commandUnit.getCommandUnitType()))) {
      return null;
    }

    List<NGVariable> variables = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(template.getVariables())) {
      template.getVariables().forEach(variable -> {
        variables.add(StringNGVariable.builder()
                          .name(variable.getName())
                          .type(NGVariableType.STRING)
                          .value(valueOrDefaultEmpty(variable.getValue()))
                          .build());
      });
    }

    List<CommandUnitWrapper> commandUnitWrappers = sshCommandTemplate.getCommandUnits()
                                                       .stream()
                                                       .map(commandUnit -> {
                                                         CommandUnitType commandUnitType =
                                                             commandUnit.getCommandUnitType();
                                                         if (SCP.equals(commandUnitType)) {
                                                           return handleScp(commandUnit);
                                                         }
                                                         if (EXEC.equals(commandUnitType)) {
                                                           return handleExec(commandUnit);
                                                         }
                                                         if (COPY_CONFIGS.equals(commandUnitType)) {
                                                           return handleCopyConfigs(commandUnit);
                                                         }
                                                         if (DOWNLOAD_ARTIFACT.equals(commandUnitType)) {
                                                           return handleDownloadArtifact(commandUnit);
                                                         }
                                                         return null;
                                                       })
                                                       .filter(Objects::nonNull)
                                                       .collect(Collectors.toList());

    CommandStepInfo commandStepInfo = CommandStepInfo.infoBuilder()
                                          .commandUnits(commandUnitWrappers)
                                          .onDelegate(ParameterField.createValueField(true))
                                          .environmentVariables(variables)
                                          .build();

    return JsonPipelineUtils.asTree(commandStepInfo);
  }

  @Override
  public String getNgTemplateStepName(Template template) {
    return StepSpecTypeConstants.COMMAND;
  }

  static CommandUnitWrapper handleCopyConfigs(CommandUnit commandUnit) {
    CopyConfigCommandUnit configCommandUnit = (CopyConfigCommandUnit) commandUnit;
    return CommandUnitWrapper.builder()
        .type(CommandUnitSpecType.COPY)
        .name(commandUnit.getName())
        .spec(CopyCommandUnitSpec.builder()
                  .destinationPath(valueOrDefaultEmpty(configCommandUnit.getDestinationParentPath()))
                  .sourceType(CommandUnitSourceType.Config)
                  .build())
        .build();
  }

  static CommandUnitWrapper handleDownloadArtifact(CommandUnit commandUnit) {
    DownloadArtifactCommandUnit downloadCommandUnit = (DownloadArtifactCommandUnit) commandUnit;
    return CommandUnitWrapper.builder()
        .type(CommandUnitSpecType.DOWNLOAD_ARTIFACT)
        .name(commandUnit.getName())
        .spec(DownloadArtifactCommandUnitSpec.builder()
                  .destinationPath(valueOrDefaultEmpty(downloadCommandUnit.getCommandPath()))
                  .build())
        .build();
  }

  static CommandUnitWrapper handleScp(CommandUnit commandUnit) {
    ScpCommandUnit scpCommandUnit = (ScpCommandUnit) commandUnit;
    return CommandUnitWrapper.builder()
        .type(CommandUnitSpecType.COPY)
        .name(commandUnit.getName())
        .spec(CopyCommandUnitSpec.builder()
                  .destinationPath(valueOrDefaultEmpty(scpCommandUnit.getDestinationDirectoryPath()))
                  .sourceType(CommandUnitSourceType.Artifact)
                  .build())
        .build();
  }

  static CommandUnitWrapper handleExec(CommandUnit commandUnit) {
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
    String script = (String) MigratorExpressionUtils.render(execCommandUnit.getCommandString(), new HashMap<>());

    return CommandUnitWrapper.builder()
        .type(CommandUnitSpecType.SCRIPT)
        .name(commandUnit.getName())
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
}
