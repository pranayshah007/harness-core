/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.executions.steps.StepSpecTypeConstants.COMMAND;
import static io.harness.pms.yaml.ParameterField.isNull;
import static io.harness.pms.yaml.YAMLFieldNameConstants.REPEAT;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ROLLBACK_STEPS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP_GROUP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STRATEGY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.ssh.CommandStepNode;
import io.harness.cdng.ssh.CommandStepParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(CDP)
public class CommandStepPlanCreator extends CDPMSStepPlanCreatorV2<CommandStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.COMMAND);
  }

  @Override
  public Class<CommandStepNode> getFieldClass() {
    return CommandStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, CommandStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, CommandStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);
    CommandStepParameters commandStepParameters =
        (CommandStepParameters) ((StepElementParameters) stepParameters).getSpec();

    if (shouldIncludeRepeatSyntax(ctx, stepElement)) {
      // TODO @sahil make a constant file for these expressions
      commandStepParameters.setHost(ParameterField.createValueField("<+repeat.item>"));
    }

    boolean isStepInsideRollback = YamlUtils.findParentNode(ctx.getCurrentField().getNode(), ROLLBACK_STEPS) != null;
    commandStepParameters.setRollback(isStepInsideRollback);

    String commandDeployFqn = getExecutionStepFqn(ctx.getCurrentField(), COMMAND);
    commandStepParameters.setCommandDeployFqn(commandDeployFqn);

    return stepParameters;
  }

  private boolean shouldIncludeRepeatSyntax(PlanCreationContext ctx, CommandStepNode stepElement) {
    boolean isStepInsideStepGroup = YamlUtils.findParentNode(ctx.getCurrentField().getNode(), STEP_GROUP) != null;
    if (isStepInsideStepGroup) {
      JsonNode stepGroupJsonNode =
          YamlUtils.findParentNode(ctx.getCurrentField().getNode(), STEP_GROUP).getCurrJsonNode();
      JsonNode strategyNode = stepGroupJsonNode.findValue(STRATEGY);
      if (strategyNode == null) {
        return false;
      }

      JsonNode repeatNode = strategyNode.findValue(REPEAT);
      if (repeatNode == null) {
        throw new InvalidRequestException("Only repeat looping strategy is supported for Command Step step groups");
      }

    } else {
      ParameterField<StrategyConfig> strategyConfig = stepElement.getStrategy();
      if (isNull(strategyConfig) || strategyConfig.getValue() == null) {
        return false;
      }
      if (strategyConfig.getValue().getRepeat() == null) {
        throw new InvalidRequestException("Only repeat looping strategy is supported for Command Step");
      }
    }

    return true;
  }
}
