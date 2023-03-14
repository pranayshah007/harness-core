/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plancreator.V1;

import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.PluginStepNode;
import io.harness.beans.steps.nodes.V1.PluginStepNodeV1;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.steps.stepinfo.V1.PluginStepInfoV1;
import io.harness.ci.integrationstage.V1.CIPlanCreatorUtils;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.utils.IdentifierGeneratorUtils;
import io.harness.pms.yaml.PipelineVersion;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Set;

public class PluginStepPlanCreatorV1 extends CIPMSStepPlanCreatorV2<PluginStepNodeV1> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(CIStepInfoType.PLUGIN_V1.getDisplayName());
  }

  @Override
  public Class<PluginStepNodeV1> getFieldClass() {
    return PluginStepNodeV1.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, PluginStepNodeV1 stepElement) {
    return super.createPlanForFieldV2(ctx, stepElement);
  }

  @Override
  public CIAbstractStepNode getStepNode(PluginStepNodeV1 stepElement) {
    PluginStepInfoV1 stepInfo = stepElement.getPluginStepInfo();
    return PluginStepNode.builder()
        .uuid(stepElement.getUuid())
        .identifier(IdentifierGeneratorUtils.getId(stepElement.getName()))
        .name(stepElement.getName())
        .failureStrategies(stepElement.getFailureStrategies())
        .timeout(stepElement.getTimeout())
        .pluginStepInfo(PluginStepInfo.builder()
                            .image(stepInfo.getImage())
                            .uses(stepInfo.getUses())
                            .envVariables(stepInfo.getEnvs())
                            .privileged(stepInfo.getPrivileged())
                            .resources(stepInfo.getResources())
                            .runAsUser(stepInfo.getUser())
                            .retry(stepInfo.getRetry())
                            .settings(stepInfo.getWith())
                            .imagePullPolicy(CIPlanCreatorUtils.getImagePullPolicy(stepInfo.getPull()))
                            .build())
        .build();
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V1);
  }
}
