/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution.creator.plan;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.beans.stepnode.ProvenanceStepNode;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.SSCA)
public class ProvenanceStepPlanCreator extends CIPMSStepPlanCreatorV2<ProvenanceStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(SscaConstants.SLSA_PROVENANCE);
  }

  @Override
  public Class<ProvenanceStepNode> getFieldClass() {
    return ProvenanceStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, ProvenanceStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V0);
  }
}
