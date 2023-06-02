/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacm.plan.creator.step;

import io.harness.beans.steps.nodes.V1.PluginStepNodeV1;
import io.harness.beans.steps.stepinfo.V1.PluginStepInfoV1;
import io.harness.ci.integrationstage.V1.CIPlanCreatorUtils;
import io.harness.ci.plancreator.V1.PluginStepPlanCreatorV1;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class IACMPluginStepPlanCretorV1 extends PluginStepPlanCreatorV1 {
  @Inject private CIPlanCreatorUtils ciPlanCreatorUtils;
  public static final String workspaceID = "workspaceId";
  public static final String WORKSPACE_ID = "WORKSPACE_ID";
  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, PluginStepNodeV1 stepElement) {
    Optional<Object> workspaceIdMetadata =
        ciPlanCreatorUtils.getDeserializedObjectFromDependency(ctx.getDependency(), workspaceID);
    if (workspaceIdMetadata.isPresent()) {
      String workspaceId = (String) workspaceIdMetadata.get();
      addMetadataToIACMPluginStep(stepElement, workspaceId);
    }
    return super.createPlanForFieldV2(ctx, stepElement);
  }
  private PluginStepNodeV1 addMetadataToIACMPluginStep(PluginStepNodeV1 stepElement, String workspaceId) {
    PluginStepInfoV1 stepInfo = stepElement.getPluginStepInfo();
    ParameterField<Map<String, ParameterField<String>>> stepEnvs = stepInfo.getEnvs();
    Map<String, ParameterField<String>> env = new HashMap<>();
    env.put(WORKSPACE_ID, ParameterField.createValueField(workspaceId));
    if (stepEnvs.getValue() != null) {
      env.putAll(stepEnvs.getValue());
    }
    PluginStepInfoV1 stepInfoV1 = PluginStepInfoV1.builder()
                                      .envs(ParameterField.createValueField(env))
                                      .image(stepInfo.getImage())
                                      .privileged(stepInfo.getPrivileged())
                                      .pull(stepInfo.getPull())
                                      .resources(stepInfo.getResources())
                                      .user(stepInfo.getUser())
                                      .uses(stepInfo.getUses())
                                      .uuid(stepInfo.getUuid())
                                      .volumes(stepInfo.getVolumes())
                                      .with(stepInfo.getWith())
                                      .build();
    stepElement.setPluginStepInfo(stepInfoV1);
    return stepElement;
  }
}
