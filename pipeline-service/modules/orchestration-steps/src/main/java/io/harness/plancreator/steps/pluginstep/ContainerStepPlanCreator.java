/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.pluginstep;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.steps.container.ContainerStepSpecTypeConstants;
import io.harness.steps.plugin.ContainerStepNode;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerStepPlanCreator extends AbstractContainerStepPlanCreator<ContainerStepNode> {
  @Override
  public Class<ContainerStepNode> getFieldClass() {
    return ContainerStepNode.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(STEP, Collections.singleton(ContainerStepSpecTypeConstants.CONTAINER_STEP));
  }

  @Override
  public PlanNode createPlanForStep(
      String stepNodeId, StepParameters stepParameters, List<AdviserObtainment> adviserObtainments) {
    return RunContainerStepPlanCreater.createPlanForField(stepNodeId, stepParameters, adviserObtainments);
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V0);
  }
}
