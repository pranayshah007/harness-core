/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cdng.ssh.CommandStepNode;
import io.harness.cdng.ssh.CommandUnitSourceType;
import io.harness.cdng.ssh.CommandUnitSpecType;
import io.harness.cdng.ssh.CommandUnitWrapper;
import io.harness.cdng.ssh.CopyCommandUnitSpec;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.exception.InvalidYamlException;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.YamlNode;

import software.wings.api.DeploymentType;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import java.util.Optional;
import java.util.Set;

public class CDPMSCommandStepFilterJsonCreator extends CDPMSStepFilterJsonCreatorV2 {
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
    return response;
  }

  private void validateStrategy(StrategyConfig strategy) {
    if (strategy != null
        && (strategy.getMatrixConfig() != null
            || (strategy.getParallelism() != null
                && (strategy.getParallelism().getValue() != null
                    || strategy.getParallelism().getExpressionValue() != null)))) {
      throw new InvalidYamlException("Command step supports only repeat strategy.");
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
    Optional<CommandUnitWrapper> copyCommandUnit = findCopyCommandUnit(stepNode);
    if (copyCommandUnit.isPresent() && isArtifactSourceType(copyCommandUnit.get())
        && isWinRmDeploymentType(filterCreationContext)) {
      throw new InvalidYamlException(
          "Copy command unit is not supported for WinRm deployment type. Please use download command unit instead.");
    }
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

  private Optional<CommandUnitWrapper> findCopyCommandUnit(AbstractStepNode stepNode) {
    if (stepNode == null || !stepNode.getClass().isAssignableFrom(CommandStepNode.class)) {
      return Optional.empty();
    }
    CommandStepNode commandStepNode = (CommandStepNode) stepNode;
    if (commandStepNode.getCommandStepInfo() == null
        || commandStepNode.getCommandStepInfo().getCommandUnits() == null) {
      return Optional.empty();
    }
    return commandStepNode.getCommandStepInfo()
        .getCommandUnits()
        .stream()
        .filter(i -> CommandUnitSpecType.COPY.equals(i.getType()))
        .findFirst();
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
}
