/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.filters.v1;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.creator.plan.stage.v1.DeploymentStageNodeV1;
import io.harness.filters.v1.GenericStageFilterJsonCreatorV3;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class DeploymentStageFilterJsonCreator extends GenericStageFilterJsonCreatorV3<DeploymentStageNodeV1> {
  @Override
  public Set<String> getSupportedStageTypes() {
    return new HashSet<>(Arrays.asList(YAMLFieldNameConstants.DEPLOYMENT_STAGE_V1));
  }

  @Override
  public PipelineFilter getFilter(FilterCreationContext filterCreationContext, DeploymentStageNodeV1 stageNode) {
    return null;
  }

  @Override
  public Class<DeploymentStageNodeV1> getFieldClass() {
    return DeploymentStageNodeV1.class;
  }
}
