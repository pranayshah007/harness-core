/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.utils;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.PlanExecutionContext;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PlanExecutionContextMapper {
  public PlanExecutionContext toExecutionContext(ExecutionMetadata metadata) {
    if (metadata == null) {
      return PlanExecutionContext.newBuilder().build();
    }
    return PlanExecutionContext.newBuilder()
        .setRunSequence(metadata.getRunSequence())
        .setTriggerInfo(metadata.getTriggerInfo())
        .setPipelineIdentifier(metadata.getPipelineIdentifier())
        .setExecutionUuid(metadata.getExecutionUuid())
        .setPrincipalInfo(metadata.getPrincipalInfo())
        .setGitSyncBranchContext(metadata.getGitSyncBranchContext())
        .setPipelineStoreType(metadata.getPipelineStoreType())
        .setPipelineConnectorRef(metadata.getPipelineConnectorRef())
        .setHarnessVersion(metadata.getHarnessVersion())
        .setProcessedYamlVersion(metadata.getProcessedYamlVersion())
        .setExecutionMode(metadata.getExecutionMode())
        .build();
  }
}
