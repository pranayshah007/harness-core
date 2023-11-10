/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cd;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(CDC)
public final class CDStageSummaryConstants {
  private CDStageSummaryConstants() {}
  public static final String SERVICE = "Service";
  public static final String ENVIRONMENT = "Environment";
  public static final String INFRA_DEFINITION = "Infrastructure Definition";
  public static final String SERVICES = "Services";
  public static final String ENVIRONMENTS = "Environments";
  public static final String INFRA_DEFINITIONS = "Infrastructure Definitions";
  public static final String ENVIRONMENT_GROUP = "Environment Group";
  public static final String STAGE_IDENTIFIERS_KEY = "stageIdentifiers";
  public static final String STAGE_IDENTIFIERS_PARAM_MESSAGE = "List of stage identifiers";
  public static final String STAGE_EXECUTION_IDENTIFIERS_KEY = "stageExecutionIdentifiers";
  public static final String STAGE_EXECUTION_IDENTIFIERS_PARAM_MESSAGE = "List of stage execution identifiers";
  public static final String PLAN_EXECUTION_ID_PARAM_MESSAGE = "The Pipeline Execution Id";
}
