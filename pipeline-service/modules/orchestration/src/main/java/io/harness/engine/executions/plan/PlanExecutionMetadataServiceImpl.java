/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.repositories.PlanExecutionMetadataRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class PlanExecutionMetadataServiceImpl implements PlanExecutionMetadataService {
  private final PlanExecutionMetadataRepository planExecutionMetadataRepository;

  @Inject
  public PlanExecutionMetadataServiceImpl(PlanExecutionMetadataRepository planExecutionMetadataRepository) {
    this.planExecutionMetadataRepository = planExecutionMetadataRepository;
  }

  @Override
  public Optional<PlanExecutionMetadata> findByPlanExecutionId(String planExecutionId) {
    return planExecutionMetadataRepository.findByPlanExecutionId(planExecutionId);
  }

  @Override
  public PlanExecutionMetadata save(PlanExecutionMetadata planExecutionMetadata) {
    return planExecutionMetadataRepository.save(planExecutionMetadata);
  }
}
