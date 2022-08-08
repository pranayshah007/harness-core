/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SKIPPED;

import static software.wings.beans.command.CommandUnitType.COMMAND;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.CommandExecutionStatus;
import io.harness.shell.ScriptType;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.DeploymentType;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.command.CleanupPowerShellCommandUnit;
import software.wings.beans.command.CleanupSshCommandUnit;
import software.wings.beans.command.CommandExecutionContext;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitType;
import software.wings.beans.command.ExecCommandUnit;
import software.wings.beans.command.InitPowerShellCommandUnit;
import software.wings.beans.command.InitSshCommandUnit;
import software.wings.beans.command.InitSshCommandUnitV2;
import software.wings.beans.dto.Command;
import software.wings.service.intfc.CommandUnitExecutorService;
import software.wings.service.intfc.ServiceCommandExecutorService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManagementDelegateService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by anubhaw on 6/2/16.
 */
@OwnedBy(HarnessTeam.CDC)
@ValidateOnExecution
@Singleton
@Slf4j
public class ServiceCommandExecutorServiceImpl implements ServiceCommandExecutorService {
  @Inject private Map<String, CommandUnitExecutorService> commandUnitExecutorServiceMap;
  @Inject private EncryptionService encryptionService;
  @Inject private SecretManagementDelegateService secretManagementDelegateService;

  /* (non-Javadoc)
   * @see software.wings.service.intfc.ServiceCommandExecutorService#execute(software.wings.beans.ServiceInstance,
   * software.wings.beans.command.Command)
   */
  @Override
  public CommandExecutionStatus execute(Command command, CommandExecutionContext context) {
    Set<String> nonSshDeploymentType = Sets.newHashSet(
        DeploymentType.AWS_CODEDEPLOY.name(), DeploymentType.ECS.name(), DeploymentType.KUBERNETES.name());
    decryptCredentials(context);
    if (!nonSshDeploymentType.contains(context.getDeploymentType())) {
      return executeShellCommand(command, context, context.getDeploymentType());
    } else {
      return executeNonSshCommand(command, context, commandUnitExecutorServiceMap.get(context.getDeploymentType()));
    }
  }

  private CommandExecutionStatus executeNonSshCommand(
      Command command, CommandExecutionContext context, CommandUnitExecutorService commandUnitExecutorService) {
    try {
      CommandExecutionStatus commandExecutionStatus =
          commandUnitExecutorService.execute(command.getCommandUnits().get(0), context); // TODO:: do it recursively
      commandUnitExecutorService.cleanup(context.getActivityId(), context.getHost());
      return commandExecutionStatus;
    } catch (Exception ex) {
      commandUnitExecutorService.cleanup(context.getActivityId(), context.getHost());
      throw ex;
    }
  }

  private CommandExecutionStatus executeShellCommand(
      Command command, CommandExecutionContext context, String deploymentType) {
    CommandUnitExecutorService commandUnitExecutorService = commandUnitExecutorServiceMap.get(deploymentType);
    List<CommandUnit> flattenedCommandUnits = getFlattenCommandUnitList(command);
    ScriptType scriptType = getScriptType(flattenedCommandUnits, deploymentType);
    try {
      if (scriptType == ScriptType.BASH) {
        if (context.isInlineSshCommand()) {
          InitSshCommandUnitV2 initCommandUnit = new InitSshCommandUnitV2();
          initCommandUnit.setCommand(command);
          command.getCommandUnits().add(0, initCommandUnit);
        } else {
          InitSshCommandUnit initCommandUnit = new InitSshCommandUnit();
          initCommandUnit.setCommand(command);
          command.getCommandUnits().add(0, initCommandUnit);
        }
        command.getCommandUnits().add(new CleanupSshCommandUnit());
      } else if (scriptType == ScriptType.POWERSHELL) {
        InitPowerShellCommandUnit initPowerShellCommandUnit = new InitPowerShellCommandUnit();
        initPowerShellCommandUnit.setCommand(command);
        command.getCommandUnits().add(0, initPowerShellCommandUnit);
        command.getCommandUnits().add(new CleanupPowerShellCommandUnit());
      }

      return executeShellCommand(commandUnitExecutorService, command, context);
    } finally {
      if (!context.isExecuteOnDelegate()) {
        commandUnitExecutorService.cleanup(context.getActivityId(), context.getHost());
      }
    }
  }
  private List<CommandUnit> getFlattenCommandUnitList(Command command) {
    return command.getCommandUnits()
        .stream()
        .flatMap(commandUnit -> {
          if (CommandUnitType.COMMAND == commandUnit.getCommandUnitType()) {
            return getFlattenCommandUnitList((Command) commandUnit).stream();
          } else {
            return Stream.of(commandUnit);
          }
        })
        .collect(toList());
  }

  private ScriptType getScriptType(List<CommandUnit> commandUnits, String deploymentType) {
    if ((commandUnits.stream().anyMatch(unit
            -> (unit.getCommandUnitType() == CommandUnitType.EXEC
                   || unit.getCommandUnitType() == CommandUnitType.DOWNLOAD_ARTIFACT)
                && ((ExecCommandUnit) unit).getScriptType() == ScriptType.POWERSHELL))
        || (DeploymentType.WINRM.name().equals(deploymentType))) {
      return ScriptType.POWERSHELL;
    } else {
      return ScriptType.BASH;
    }
  }

  private CommandExecutionStatus executeShellCommand(
      CommandUnitExecutorService commandUnitExecutorService, Command command, CommandExecutionContext context) {
    List<CommandUnit> commandUnits = command.getCommandUnits();

    CommandExecutionStatus commandExecutionStatus = CommandExecutionStatus.FAILURE;

    for (CommandUnit commandUnit : commandUnits) {
      commandExecutionStatus = COMMAND == commandUnit.getCommandUnitType()
          ? executeShellCommand(commandUnitExecutorService, (Command) commandUnit, context)
          : commandUnitExecutorService.execute(commandUnit, context);
      if (FAILURE == commandExecutionStatus) {
        if (COMMAND == commandUnit.getCommandUnitType() && ((Command) commandUnit).getCommandUnits().isEmpty()) {
          commandExecutionStatus = SKIPPED;
        } else {
          break;
        }
      }
    }

    return commandExecutionStatus;
  }

  @VisibleForTesting
  void decryptCredentials(CommandExecutionContext context) {
    if (context.getHostConnectionAttributes() != null) {
      encryptionService.decrypt((EncryptableSetting) context.getHostConnectionAttributes().getValue(),
          context.getHostConnectionCredentials(), false);
      if ((context.getHostConnectionAttributes().getValue() instanceof HostConnectionAttributes)
          && ((HostConnectionAttributes) context.getHostConnectionAttributes().getValue()).isVaultSSH()) {
        secretManagementDelegateService.signPublicKey(
            (HostConnectionAttributes) context.getHostConnectionAttributes().getValue(), context.getSshVaultConfig());
      }
    }
    if (context.getBastionConnectionAttributes() != null) {
      encryptionService.decrypt((EncryptableSetting) context.getBastionConnectionAttributes().getValue(),
          context.getBastionConnectionCredentials(), false);
    }
    if (context.getWinrmConnectionAttributes() != null) {
      encryptionService.decrypt(
          context.getWinrmConnectionAttributes(), context.getWinrmConnectionEncryptedDataDetails(), false);
    }
    if (context.getCloudProviderSetting() != null) {
      encryptionService.decrypt((EncryptableSetting) context.getCloudProviderSetting().getValue(),
          context.getCloudProviderCredentials(), false);
    }
  }
}
