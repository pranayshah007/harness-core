/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.data;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.time.Duration.ofDays;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAccess;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.data.output.PmsSweepingOutput;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PIPELINE)
@Value
@Builder
@StoreIn(DbAliases.PMS)
@Entity(value = "executionSweepingOutput", noClassnameStored = true)
@Document("executionSweepingOutput")
@FieldNameConstants(innerTypeName = "ExecutionSweepingOutputKeys")
@TypeAlias("executionSweepingOutput")
public class ExecutionSweepingOutputInstance implements PersistentEntity, UuidAccess {
  public static final Duration TTL = ofDays(30);

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_levelRuntimeIdUniqueIdx2")
                 .unique(true)
                 .field(ExecutionSweepingOutputKeys.planExecutionId)
                 .field(ExecutionSweepingOutputKeys.levelRuntimeIdIdx)
                 .field(ExecutionSweepingOutputKeys.name)
                 .build())
        .add(CompoundMongoIndex.builder().name("producedByRuntime_Idx").field("producedBy.runtimeId").build())
        .add(SortCompoundMongoIndex.builder()
                 .name("planExecutionId_fully_qualified_name_createdAt_Idx")
                 .field(ExecutionSweepingOutputKeys.planExecutionId)
                 .field(ExecutionSweepingOutputKeys.fullyQualifiedName)
                 .descRangeField(ExecutionSweepingOutputKeys.createdAt)
                 .build())
        .build();
  }
  @Wither @Id @dev.morphia.annotations.Id String uuid;
  @NonNull String planExecutionId;
  String stageExecutionId;
  @NotNull @Trimmed String name;
  String levelRuntimeIdIdx;
  Level producedBy;
  @Deprecated Map<String, Object> value; // use valueOutput instead
  PmsSweepingOutput valueOutput;
  @Wither @CreatedDate Long createdAt;

  @FdTtlIndex @Builder.Default Date validUntil = Date.from(OffsetDateTime.now().plus(TTL).toInstant());

  @Wither @Version Long version;

  String groupName;

  String fullyQualifiedName;

  public String getOutputValueJson() {
    if (!EmptyPredicate.isEmpty(valueOutput)) {
      return valueOutput.toJson();
    }

    return RecastOrchestrationUtils.toJson(value);
  }
}
