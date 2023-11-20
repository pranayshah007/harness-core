/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.execution;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
public interface StageExecutionBasicSummaryProjection {
  String getAccountIdentifier();
  String getOrgIdentifier();
  String getProjectIdentifier();
  String getEnvIdentifier();
  String getInfraIdentifier();
  String getServiceIdentifier();
  String getStageExecutionId();

  // createdAt and lastModifiedAt are mandatory for instantiation of StageExecutionInfo proxy at runtime
  Long getCreatedAt();
  Long getLastModifiedAt();
}
