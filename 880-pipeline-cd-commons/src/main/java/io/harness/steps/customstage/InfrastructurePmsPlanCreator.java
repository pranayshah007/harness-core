/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.customstage;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.data.structure.UUIDGenerator;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class InfrastructurePmsPlanCreator {
  public PlanNode getInfraTaskExecutableStepV2PlanNode(EnvironmentYamlV2 environmentYamlV2,
      List<AdviserObtainment> adviserObtainments, ServiceDefinitionType deploymentType,
      ParameterField<Boolean> skipInstances) {
    ParameterField<String> infraRef;
    ParameterField<Map<String, Object>> infraInputs;

    if (ParameterField.isNotNull(environmentYamlV2.getInfrastructureDefinitions())
        && isNotEmpty(environmentYamlV2.getInfrastructureDefinitions().getValue())) {
      infraRef = environmentYamlV2.getInfrastructureDefinitions().getValue().get(0).getIdentifier();
      infraInputs = environmentYamlV2.getInfrastructureDefinitions().getValue().get(0).getInputs();
    } else if (ParameterField.isNotNull(environmentYamlV2.getInfrastructureDefinition())) {
      infraRef = environmentYamlV2.getInfrastructureDefinition().getValue().getIdentifier();
      infraInputs = environmentYamlV2.getInfrastructureDefinition().getValue().getInputs();
    } else {
      infraRef = ParameterField.createValueField(null);
      infraInputs = ParameterField.createValueField(null);
    }
    InfrastructureTaskExecutableStepV2Params params = InfrastructureTaskExecutableStepV2Params.builder()
                                                          .envRef(environmentYamlV2.getEnvironmentRef())
                                                          .infraRef(infraRef)
                                                          .infraInputs(infraInputs)
                                                          .deploymentType(deploymentType)
                                                          .skipInstances(skipInstances)
                                                          .build();
    return PlanNode.builder()
        .uuid(UUIDGenerator.generateUuid())
        .expressionMode(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED)
        .name("Infrastructure")
        .identifier("infrastructure")
        .stepType(
            StepType.newBuilder().setType("INFRASTRUCTURE_TASKSTEP_V2").setStepCategory(StepCategory.STEP).build())
        .group("infrastructureGroup")
        .stepParameters(params)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                .build())
        .adviserObtainments(adviserObtainments)
        .build();
  }
}
