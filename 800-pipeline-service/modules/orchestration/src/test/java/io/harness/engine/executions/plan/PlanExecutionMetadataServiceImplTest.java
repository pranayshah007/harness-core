/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.repositories.PlanExecutionMetadataRepository;
import io.harness.rule.Owner;
import io.harness.testlib.RealMongo;

import com.google.inject.Inject;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanExecutionMetadataServiceImplTest extends OrchestrationTestBase {
  @Inject private PlanExecutionMetadataRepository planExecutionMetadataRepository;
  @Inject private PlanExecutionMetadataService planExecutionMetadataService;

  @Test
  @RealMongo
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void findByPlanExecutionId() {
    String planExecutionId = generateUuid();
    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build();
    planExecutionMetadataRepository.save(planExecutionMetadata);

    Optional<PlanExecutionMetadata> saved = planExecutionMetadataService.findByPlanExecutionId(planExecutionId);
    assertThat(saved.isPresent()).isTrue();
    assertThat(saved.get().getPlanExecutionId()).isEqualTo(planExecutionId);
  }

  @Test
  @RealMongo
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void save() {
    String planExecutionId = generateUuid();
    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build();
    planExecutionMetadataService.save(planExecutionMetadata);

    Optional<PlanExecutionMetadata> saved = planExecutionMetadataRepository.findById(planExecutionMetadata.getUuid());
    assertThat(saved.isPresent()).isTrue();
  }
}
