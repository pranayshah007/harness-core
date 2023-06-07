/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.cdng.ssh.SshWinRmConstants.FILE_STORE_SCRIPT_ERROR_MSG;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.yaml.ParameterField.isNotNull;

import static java.lang.String.format;

import io.harness.beans.FileReference;
import io.harness.cdng.ssh.CommandStepInfo;
import io.harness.cdng.ssh.CommandStepNode;
import io.harness.cdng.ssh.CommandUnitSourceType;
import io.harness.cdng.ssh.CommandUnitSpecType;
import io.harness.cdng.ssh.CommandUnitWrapper;
import io.harness.cdng.ssh.CopyCommandUnitSpec;
import io.harness.cdng.ssh.ScriptCommandUnitSpec;
import io.harness.cdng.ssh.SshWinRmConfigFileHelper;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.shellscript.HarnessFileStoreSource;
import io.harness.steps.shellscript.ShellScriptBaseSource;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;

import software.wings.api.DeploymentType;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class CDPMSCommandStepFilterJsonCreator extends CDPMSStepFilterJsonCreatorV2 {
  @Inject private SshWinRmConfigFileHelper sshWinRmConfigFileHelper;

  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.COMMAND);
  }

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, AbstractStepNode yamlField) {
    FilterCreationResponse response = super.handleNode(filterCreationContext, yamlField);
    validateStrategy(yamlField.getStrategy());
    validateCommandUnitsExist(yamlField);
    validateCommandUnits(filterCreationContext, yamlField);
    validateCommandStepOutputVariables(yamlField);
    return response;
  }

  private void validateStrategy(ParameterField<StrategyConfig> strategy) {
    if (isNotNull(strategy) && strategy.getValue() != null
        && ((isNotNull(strategy.getValue().getMatrixConfig())
                && strategy.getValue().getMatrixConfig().getValue() != null)
            || (strategy.getValue().getParallelism() != null
                && (strategy.getValue().getParallelism().getValue() != null
                    || strategy.getValue().getParallelism().getExpressionValue() != null))
            || strategy.getValue().getRepeat() == null
            || ParameterField.isNull(strategy.getValue().getRepeat().getItems()))) {
      throw new InvalidYamlException("Command step support repeat strategy with items syntax.");
    }
  }

  private void validateCommandUnitsExist(AbstractStepNode stepNode) {
    if (CommandStepNode.class.isAssignableFrom(stepNode.getClass())
        && (((CommandStepNode) stepNode).getCommandStepInfo() == null
            || isEmpty(((CommandStepNode) stepNode).getCommandStepInfo().getCommandUnits()))) {
      throw new InvalidYamlException("Command step needs at least one command unit defined.");
    }
  }

  private void validateCommandUnits(FilterCreationContext filterCreationContext, AbstractStepNode stepNode) {
    List<CommandUnitWrapper> copyCommandUnits = findCommandUnits(stepNode, CommandUnitSpecType.COPY);
    copyCommandUnits.forEach(copyCommandUnit -> {
      if (isArtifactSourceType(copyCommandUnit) && isWinRmDeploymentType(filterCreationContext)) {
        throw new InvalidYamlException(
            "Copy command unit is not supported for WinRm deployment type. Please use download command unit instead.");
      }
    });

    List<CommandUnitWrapper> scriptCommandUnits = findCommandUnits(stepNode, CommandUnitSpecType.SCRIPT);
    scriptCommandUnits.forEach(scriptCommandUnit -> {
      if (isScriptFromHarnessFileStore(scriptCommandUnit)) {
        HarnessFileStoreSource harnessFileStoreSource =
            (HarnessFileStoreSource) ((ScriptCommandUnitSpec) scriptCommandUnit.getSpec()).getSource().getSpec();
        validateScriptFromHarnessFileStore(filterCreationContext, harnessFileStoreSource);
      }
    });
  }

  private boolean isWinRmDeploymentType(FilterCreationContext filterCreationContext) {
    JsonNode deploymentType = findDeploymentType(filterCreationContext);
    return deploymentType != null && DeploymentType.WINRM.name().equalsIgnoreCase(deploymentType.textValue());
  }

  private boolean isArtifactSourceType(CommandUnitWrapper commandUnitWrapper) {
    if (commandUnitWrapper.getSpec() instanceof CopyCommandUnitSpec) {
      return CommandUnitSourceType.Artifact.equals(
          ((CopyCommandUnitSpec) commandUnitWrapper.getSpec()).getSourceType());
    } else {
      return false;
    }
  }

  private boolean isScriptFromHarnessFileStore(CommandUnitWrapper commandUnitWrapper) {
    if (commandUnitWrapper.getSpec() instanceof ScriptCommandUnitSpec) {
      ScriptCommandUnitSpec scriptCommandUnitSpec = (ScriptCommandUnitSpec) commandUnitWrapper.getSpec();
      return Objects.equals(scriptCommandUnitSpec.getSource().getSpec().getType(), ShellScriptBaseSource.HARNESS);
    }
    return false;
  }

  private void validateScriptFromHarnessFileStore(
      FilterCreationContext filterCreationContext, HarnessFileStoreSource harnessFileStoreSource) {
    String accountIdentifier = filterCreationContext.getSetupMetadata().getAccountId();
    String orgIdentifier = filterCreationContext.getSetupMetadata().getOrgId();
    String projectIdentifier = filterCreationContext.getSetupMetadata().getProjectId();
    String scopedFilePath = harnessFileStoreSource.getFile().getValue();
    String script = sshWinRmConfigFileHelper.fetchFileContent(
        FileReference.of(scopedFilePath, accountIdentifier, orgIdentifier, projectIdentifier));
    if (isEmpty(script)) {
      throw new InvalidRequestException(format(FILE_STORE_SCRIPT_ERROR_MSG, scopedFilePath));
    }
  }

  private List<CommandUnitWrapper> findCommandUnits(AbstractStepNode stepNode, String commandUnitType) {
    if (stepNode == null || !stepNode.getClass().isAssignableFrom(CommandStepNode.class)) {
      return Collections.emptyList();
    }
    CommandStepNode commandStepNode = (CommandStepNode) stepNode;
    if (commandStepNode.getCommandStepInfo() == null
        || commandStepNode.getCommandStepInfo().getCommandUnits() == null) {
      return Collections.emptyList();
    }
    return commandStepNode.getCommandStepInfo()
        .getCommandUnits()
        .stream()
        .filter(i -> commandUnitType.equals(i.getType()))
        .collect(Collectors.toList());
  }

  private JsonNode findDeploymentType(FilterCreationContext filterCreationContext) {
    if (filterCreationContext == null || filterCreationContext.getCurrentField() == null) {
      return null;
    }
    YamlNode currentNode = filterCreationContext.getCurrentField().getNode();
    return findDeploymentTypeInHierarchy(currentNode);
  }

  private JsonNode findDeploymentTypeInHierarchy(YamlNode node) {
    if (node == null) {
      return null;
    }
    if (node.getCurrJsonNode() == null) {
      return node.getParentNode() != null ? findDeploymentTypeInHierarchy(node.getParentNode()) : null;
    }
    JsonNode deploymentType = node.getCurrJsonNode().get(YamlTypes.DEPLOYMENT_TYPE);
    if (deploymentType != null) {
      return deploymentType;
    } else {
      return findDeploymentTypeInHierarchy(node.getParentNode());
    }
  }

  private void validateCommandStepOutputVariables(AbstractStepNode stepNode) {
    if (CommandStepNode.class.isAssignableFrom(stepNode.getClass())
        && ((CommandStepNode) stepNode).getCommandStepInfo() != null) {
      CommandStepInfo commandStepInfo = ((CommandStepNode) stepNode).getCommandStepInfo();
      List<NGVariable> outputVariables = commandStepInfo.getOutputVariables();
      outputVariables.forEach(ngVariable -> {
        if (ngVariable != null && NGVariableType.NUMBER == ngVariable.getType()) {
          throw new InvalidYamlException(
              "Number output variables are not supported as Bash/PowerShell variable names. Please use String or Secret output variables instead.");
        }
      });
    }
  }
}
