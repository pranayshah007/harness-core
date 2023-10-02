/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.data;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.OutcomeInstance;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.data.StepOutcomeRef;
import io.harness.pms.contracts.refobjects.RefObject;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;

@OwnedBy(HarnessTeam.PIPELINE)
public interface PmsOutcomeService extends Resolver {
  List<String> findAllByRuntimeId(String planExecutionId, String runtimeId);

  Map<String, String> findAllOutcomesMapByRuntimeId(String planExecutionId, String runtimeId);

  List<String> fetchOutcomes(List<String> outcomeInstanceIds);

  String fetchOutcome(@NonNull String outcomeInstanceId);

  OptionalOutcome resolveOptional(Ambiance ambiance, RefObject refObject);

  List<OutcomeInstance> fetchOutcomeInstanceByRuntimeId(String runtimeId);

  List<String> cloneForRetryExecution(Ambiance ambiance, String originalNodeExecutionId);

  List<StepOutcomeRef> fetchOutcomeRefs(String nodeExecutionId);

  Map<String, List<StepOutcomeRef>> fetchOutcomeRefs(List<String> nodeExecutionIds);

  /**
   * Delete all outcome instances for given planExecutionIds
   * Uses - unique_levelRuntimeIdUniqueIdx
   *
   * @param planExecutionIds
   */
  void deleteAllOutcomesInstances(Set<String> planExecutionIds);

  /**
   * Updates all outcome instances for given planExecutionId
   * Uses - unique_levelRuntimeIdUniqueIdx
   *
   * @param planExecutionId
   */
  void updateTTL(String planExecutionId, Date ttlDate);
}
