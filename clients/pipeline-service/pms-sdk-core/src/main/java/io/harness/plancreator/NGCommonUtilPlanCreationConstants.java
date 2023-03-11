/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class NGCommonUtilPlanCreationConstants {
  public final String COMBINED_ROLLBACK_ID_SUFFIX = "_combinedRollback";
  public final String NG_FORK = "NG_FORK";
  public final String STRATEGY = "STRATEGY";
  public final String STRATEGY_V1 = "STRATEGY_V1";
  public final String IDENTITY_STRATEGY = "IDENTITY_STRATEGY";
  public final String IDENTITY_STRATEGY_INTERNAL = "IDENTITY_STRATEGY_INTERNAL";

  public final String NG_SECTION = "NG_SECTION";
  public final String NG_SPEC_STEP = "NG_SPEC_STEP";
  public final String SECTION_CHAIN = "SECTION_CHAIN";
  public final String NG_SECTION_WITH_ROLLBACK_INFO = "NG_SECTION_WITH_ROLLBACK_INFO";
  public final String NG_EXECUTION = "NG_EXECUTION";
  public final String EXECUTION_NODE_NAME = "Execution";
  public final String EXECUTION_NODE_IDENTIFIER = "execution";
  public final String ROLLBACK_NODE_NAME = "(Rollback)";
  public final String ROLLBACK_STAGE_UUID_SUFFIX = "_rollbackStage";
  public final String ROLLBACK_STAGE_NODE_NAME = "(Rollback Stage)";
  public final String INFRA_ROLLBACK_NODE_NAME = "Infrastructure (Rollback)";
  public final String INFRA_ROLLBACK_NODE_IDENTIFIER = "infraRollbackSteps";
  public final String ROLLBACK_STEPS_NODE_ID_SUFFIX = "_rollbackSteps";
  public final String ROLLBACK_EXECUTION_NODE_ID_SUFFIX = "_combinedRollback";
  public final String STEP_GROUP = "STEP_GROUP";
  public final String NOOP = "NOOP";
  public final String GROUP = "GROUP";

  public final String ROLLBACK_STAGE = "ROLLBACK_STAGE";
}
