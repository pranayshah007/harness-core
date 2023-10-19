/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.cvng.analysis.entities.VerificationTaskBase;
import io.harness.cvng.statemachine.beans.AnalysisOrchestratorStatus;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "AnalysisOrchestratorKeys")
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.CVNG)
@Entity(value = "analysisOrchestrators", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class AnalysisOrchestrator
    extends VerificationTaskBase implements PersistentEntity, UuidAware, PersistentRegularIterable {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("orchestratorIteratorQueryIdx")
                 .field(AnalysisOrchestratorKeys.status)
                 .field(AnalysisOrchestratorKeys.analysisOrchestrationIteration)
                 .build())
        .build();
  }

  @Id private String uuid;
  @FdIndex private String verificationTaskId;
  @Builder.Default private List<AnalysisStateMachine> analysisStateMachineQueue = new ArrayList<>();
  private AnalysisOrchestratorStatus status;
  private String accountId;

  @FdTtlIndex private Date validUntil = Date.from(OffsetDateTime.now().plusDays(30).toInstant());

  @FdIndex private Long analysisOrchestrationIteration;

  private Long lastQueueSizeMetricIteration;

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (AnalysisOrchestratorKeys.analysisOrchestrationIteration.equals(fieldName)) {
      this.analysisOrchestrationIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (AnalysisOrchestratorKeys.analysisOrchestrationIteration.equals(fieldName)) {
      return this.analysisOrchestrationIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }
}
