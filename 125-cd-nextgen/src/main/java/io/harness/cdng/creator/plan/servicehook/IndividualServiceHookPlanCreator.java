/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.servicehook;

import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.hooks.ServiceHook;
import io.harness.cdng.hooks.steps.IndividualServiceHookStep;
import io.harness.cdng.hooks.steps.ServiceHookStepParameters;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class IndividualServiceHookPlanCreator implements PartialPlanCreator<ServiceHook> {
  @Inject KryoSerializer kryoSerializer;
  @Override
  public Class<ServiceHook> getFieldClass() {
    return ServiceHook.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YamlTypes.PRE_HOOK, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, ServiceHook field) {
    String serviceHookId = (String) kryoSerializer.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(YamlTypes.UUID).toByteArray());
    ServiceHookStepParameters stepParameters = (ServiceHookStepParameters) kryoSerializer.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(PlanCreatorConstants.SERVICE_HOOK_STEP_PARAMETER).toByteArray());

    PlanNode customFilePlanNode =
        PlanNode.builder()
            .uuid(serviceHookId)
            .stepType(IndividualServiceHookStep.STEP_TYPE)
            .name(PlanCreatorConstants.SERVICE_HOOKS_NODE_NAME)
            .identifier(stepParameters.getIdentifier())
            .stepParameters(stepParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .skipExpressionChain(false)
            .build();

    return PlanCreationResponse.builder().planNode(customFilePlanNode).build();
  }
}
