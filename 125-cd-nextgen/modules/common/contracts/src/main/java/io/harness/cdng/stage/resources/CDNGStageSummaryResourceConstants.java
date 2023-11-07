/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.stage.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(CDC)
public class CDNGStageSummaryResourceConstants {
  private CDNGStageSummaryResourceConstants() {}
  public static final String ROW_DELIMITER_PARAM_MESSAGE = "delimiter for rows in formatted summary";
  public static final String KEY_VALUE_DELIMITER_PARAM_MESSAGE = "delimiter for keys and values in formatted summary";
  public static final String ROW_DELIMITER_KEY = "rowDelimiter";
  public static final String KEY_VALUE_DELIMITER_KEY = "keyValueDelimiter";

  public static final String STAGE_IDENTIFIERS_KEY = "stageIdentifiers";
  public static final String STAGE_IDENTIFIERS_PARAM_MESSAGE = "List of stage identifiers";
  public static final String STAGE_EXECUTION_IDENTIFIERS_KEY = "stageExecutionIdentifiers";
  public static final String STAGE_EXECUTION_IDENTIFIERS_PARAM_MESSAGE = "List of stage execution identifiers";
  public static final String PLAN_EXECUTION_ID_PARAM_MESSAGE = "The Pipeline Execution Id";
}
