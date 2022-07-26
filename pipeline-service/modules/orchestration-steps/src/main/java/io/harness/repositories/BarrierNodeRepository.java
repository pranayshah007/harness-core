/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;

import org.springframework.data.repository.CrudRepository;

@OwnedBy(PIPELINE)
@HarnessRepo
public interface BarrierNodeRepository extends CrudRepository<BarrierExecutionInstance, String> {
  BarrierExecutionInstance findByIdentifierAndPlanExecutionId(String identifier, String planExecutionId);
}
