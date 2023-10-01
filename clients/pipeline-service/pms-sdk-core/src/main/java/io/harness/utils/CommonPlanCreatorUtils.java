/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.utils;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.NGSpecStep;
import io.harness.steps.common.NGSectionStepParameters;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NumberNGVariable;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.core.variables.StringNGVariable;

import java.util.List;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@UtilityClass
@OwnedBy(PIPELINE)
public class CommonPlanCreatorUtils {
  public PlanNode getSpecPlanNode(String nodeUuid, String childNodeId) {
    StepParameters stepParameters =
        NGSectionStepParameters.builder().childNodeId(childNodeId).logMessage("Spec Element").build();
    return PlanNode.builder()
        .uuid(nodeUuid)
        .identifier(YAMLFieldNameConstants.SPEC)
        .stepType(NGSpecStep.STEP_TYPE)
        .name(YAMLFieldNameConstants.SPEC)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .skipGraphType(SkipType.SKIP_NODE)
        .build();
  }

  public void validateVariables(List<NGVariable> variables, String msg) {
    if (!isEmpty(variables)) {
      for (NGVariable variable : variables) {
        if (variable instanceof StringNGVariable) {
          StringNGVariable stringVariable = (StringNGVariable) variable;
          if (stringVariable.getValue().isExecutionInput()) {
            throw new InvalidRequestException(msg);
          }
        } else if (variable instanceof SecretNGVariable) {
          SecretNGVariable secretNGVariable = (SecretNGVariable) variable;
          if (secretNGVariable.getValue().isExecutionInput()) {
            throw new InvalidRequestException(msg);
          }
        } else if (variable instanceof NumberNGVariable) {
          NumberNGVariable numberNGVariable = (NumberNGVariable) variable;
          if (numberNGVariable.getValue().isExecutionInput()) {
            throw new InvalidRequestException(msg);
          }
        }
      }
    }
  }
}
