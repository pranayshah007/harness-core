/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ROLLBACK_STEPS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.ssh.CommandStepNode;
import io.harness.cdng.ssh.CommandStepParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(CDP)
public class TemplatedCommandStepPlanCreator extends CDPMSStepPlanCreatorV2<CommandStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(PlanCreatorUtils.TEMPLATE_TYPE);
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

    boolean isStepInsideRollback = YamlUtils.findParentNode(ctx.getCurrentField().getNode(), ROLLBACK_STEPS) != null;
    commandStepParameters.setRollback(isStepInsideRollback);

    return stepParameters;
  }
}
