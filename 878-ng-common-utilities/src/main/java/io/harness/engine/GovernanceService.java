/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.governance.GovernanceMetadata;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@OwnedBy(HarnessTeam.PIPELINE)
public interface GovernanceService {
  GovernanceMetadata evaluateGovernancePolicies(String expandedJson, String accountId, String orgIdentifier,
      String projectIdentifier, String action, String planExecutionId, String pipelineVersion);
  GovernanceMetadata evaluateGovernancePoliciesForTemplate(String expandedJson, String accountId, String orgIdentifier,
      String projectIdentifier, String action, String type);
}
