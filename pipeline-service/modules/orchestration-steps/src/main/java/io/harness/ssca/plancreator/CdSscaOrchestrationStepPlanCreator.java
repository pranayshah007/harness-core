/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.plancreator;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.cd.beans.stepnode.CdSscaOrchestrationStepNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.SSCA)
public class CdSscaOrchestrationStepPlanCreator extends ChildrenPlanCreator<CdSscaOrchestrationStepNode> {
  @Override
  public Class<CdSscaOrchestrationStepNode> getFieldClass() {
    return CdSscaOrchestrationStepNode.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(STEP, Collections.singleton(SscaConstants.CD_SSCA_ORCHESTRATION));
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, CdSscaOrchestrationStepNode config) {
    return null;
  }

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, CdSscaOrchestrationStepNode config, List<String> childrenNodeIds) {
    return null;
  }
}
