/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plancreator.V1;

import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.BackgroundStepNode;
import io.harness.beans.steps.nodes.V1.BackgroundStepNodeV1;
import io.harness.beans.steps.stepinfo.BackgroundStepInfo;
import io.harness.beans.steps.stepinfo.V1.BackgroundStepInfoV1;
import io.harness.ci.integrationstage.V1.CIPlanCreatorUtils;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.utils.IdentifierGeneratorUtils;
import io.harness.pms.yaml.PipelineVersion;

import com.google.common.collect.Sets;
import java.util.Set;

public class BackgroundStepPlanCreatorV1 extends CIPMSStepPlanCreatorV2<BackgroundStepNodeV1> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(CIStepInfoType.BACKGROUND_V1.getDisplayName());
  }

  @Override
  public Class<BackgroundStepNodeV1> getFieldClass() {
    return BackgroundStepNodeV1.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, BackgroundStepNodeV1 stepElement) {
    return super.createPlanForFieldV2(ctx, stepElement);
  }

  @Override
  public CIAbstractStepNode getStepNode(BackgroundStepNodeV1 stepElement) {
    BackgroundStepInfoV1 backgroundStepInfoV1 = stepElement.getBackgroundStepInfoV1();
    return BackgroundStepNode.builder()
        .uuid(stepElement.getUuid())
        .identifier(IdentifierGeneratorUtils.getId(stepElement.getName()))
        .name(stepElement.getName())
        .failureStrategies(stepElement.getFailureStrategies())
        .timeout(stepElement.getTimeout())
        .backgroundStepInfo(BackgroundStepInfo.builder()
                                .command(backgroundStepInfoV1.getRun())
                                .image(backgroundStepInfoV1.getImage())
                                .envVariables(backgroundStepInfoV1.getEnvs())
                                .resources(backgroundStepInfoV1.getResources())
                                .retry(backgroundStepInfoV1.getRetry())
                                .shell(CIPlanCreatorUtils.getShell(backgroundStepInfoV1.getShell()))
                                .imagePullPolicy(CIPlanCreatorUtils.getImagePullPolicy(backgroundStepInfoV1.getPull()))
                                .runAsUser(backgroundStepInfoV1.getUser())
                                .privileged(backgroundStepInfoV1.getPrivileged())
                                .entrypoint(backgroundStepInfoV1.getEntrypointList())
                                .ports(backgroundStepInfoV1.getPorts())
                                .build())
        .build();
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V1);
  }
}
