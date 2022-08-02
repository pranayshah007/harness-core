/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.barriers.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.distribution.barrier.Barrier.State;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.steps.barriers.beans.BarrierPositionInfo.BarrierPosition.BarrierPositionKeys;
import io.harness.steps.barriers.beans.BarrierPositionInfo.BarrierPositionInfoKeys;
import io.harness.steps.barriers.beans.BarrierSetupInfo.BarrierSetupInfoKeys;
import io.harness.steps.barriers.beans.StageDetail.StageDetailKeys;

import com.google.common.collect.ImmutableList;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import lombok.experimental.Wither;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PIPELINE)
@Data
@Builder
@FieldNameConstants(innerTypeName = "BarrierExecutionInstanceKeys")
@Entity(value = "barrierExecutionInstances")
@Document("barrierExecutionInstances")
@TypeAlias("barrierExecutionInstance")
@StoreIn(DbAliases.PMS)
public final class BarrierExecutionInstance implements PersistentEntity, UuidAware, PersistentRegularIterable {
  public static final long TTL = 6;

  @Id @org.mongodb.morphia.annotations.Id private String uuid;

  @NotNull private String name;
  @NotNull private String identifier;
  @NotNull private String planExecutionId;
  @NotNull private State barrierState;
  @NotNull private BarrierSetupInfo setupInfo;
  private BarrierPositionInfo positionInfo;

  private Long nextIteration;

  // audit fields
  @Wither @FdIndex @CreatedDate Long createdAt;
  @Wither @LastModifiedDate Long lastUpdatedAt;
  @Version Long version;

  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL).toInstant());

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }

  @UtilityClass
  public static class BarrierExecutionInstanceKeys {
    public static final String stages = BarrierExecutionInstanceKeys.setupInfo + "." + BarrierSetupInfoKeys.stages;
    public static final String stagesIdentifier =
        BarrierExecutionInstanceKeys.setupInfo + "." + BarrierSetupInfoKeys.stages + "." + StageDetailKeys.identifier;
    public static final String positions =
        BarrierExecutionInstanceKeys.positionInfo + "." + BarrierPositionInfoKeys.barrierPositionList;

    public static final String stagePositionSetupId = positions + "." + BarrierPositionKeys.stageSetupId;
    public static final String stepGroupPositionSetupId = positions + "." + BarrierPositionKeys.stepGroupSetupId;
    public static final String stepPositionSetupId = positions + "." + BarrierPositionKeys.stepSetupId;
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_barrierState_stagesIdentifier_idx")
                 .field(BarrierExecutionInstanceKeys.planExecutionId)
                 .field(BarrierExecutionInstanceKeys.barrierState)
                 .field(BarrierExecutionInstanceKeys.stagesIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("unique_identifier_planExecutionId_idx")
                 .field(BarrierExecutionInstanceKeys.identifier)
                 .field(BarrierExecutionInstanceKeys.planExecutionId)
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("next_iteration_idx")
                 .field(BarrierExecutionInstanceKeys.barrierState)
                 .field(BarrierExecutionInstanceKeys.nextIteration)
                 .build())
        .build();
  }
}
