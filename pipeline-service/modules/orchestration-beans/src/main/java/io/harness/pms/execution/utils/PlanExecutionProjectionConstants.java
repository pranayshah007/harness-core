/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.execution.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(PIPELINE)
public final class PlanExecutionProjectionConstants {
  public static final Set<String> fieldsForPlanExecutionDelete = Sets.newHashSet(PlanExecutionKeys.planId);

  public static final Set<String> fieldsForPostProdRollback =
      Sets.newHashSet(PlanExecutionMetadataKeys.postExecutionRollbackInfos);
}
