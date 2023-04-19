/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.data;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAccess;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.data.PmsOutcome;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
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
@Entity(value = "outcomeInstances", noClassnameStored = true)
@Document("outcomeInstances")
@FieldNameConstants(innerTypeName = "OutcomeInstanceKeys")
@TypeAlias("outcomeInstance")
public class OutcomeInstance implements PersistentEntity, UuidAccess {
  public static final long TTL_MONTHS = 6;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_producedBySetupIdRuntimeIdIdx")
                 .unique(true)
                 .field(OutcomeInstanceKeys.planExecutionId)
                 .field(OutcomeInstanceKeys.producedBySetupId)
                 .field(OutcomeInstanceKeys.producedByRuntimeId)
                 .field(OutcomeInstanceKeys.name)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("unique_levelRuntimeIdUniqueIdx")
                 .unique(true)
                 .field(OutcomeInstanceKeys.planExecutionId)
                 .field(OutcomeInstanceKeys.levelRuntimeIdIdx)
                 .field(OutcomeInstanceKeys.name)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("producedByRuntimeIdIdx")
                 .field(OutcomeInstanceKeys.producedByRuntimeId)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("planExecutionIdProducedByRuntimeIdCreatedAtIdx")
                 .field(OutcomeInstanceKeys.planExecutionId)
                 .field(OutcomeInstanceKeys.producedByRuntimeId)
                 .descRangeField(OutcomeInstanceKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("planExecutionId_fully_qualified_name_createdAt")
                 .field(OutcomeInstanceKeys.planExecutionId)
                 .field(OutcomeInstanceKeys.fullyQualifiedName)
                 .descRangeField(OutcomeInstanceKeys.createdAt)
                 .build())
        .build();
  }

  @Wither @Id @dev.morphia.annotations.Id String uuid;

  String planExecutionId;
  String stageExecutionId;
  Level producedBy;
  @Trimmed String name;
  String levelRuntimeIdIdx;
  @Deprecated org.bson.Document outcome;
  PmsOutcome outcomeValue;
  @Wither @CreatedDate Long createdAt;
  @Wither @Version Long version;
  String groupName;
  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());

  String fullyQualifiedName;

  public String getOutcomeJsonValue() {
    if (!isEmpty(outcomeValue)) {
      return outcomeValue.toJson();
    }

    if (!isEmpty(outcome)) {
      return outcome.toJson();
    }

    return null;
  }

  @UtilityClass
  public static class OutcomeInstanceKeys {
    public final String producedBySetupId = OutcomeInstanceKeys.producedBy + ".setupId";
    public final String producedByRuntimeId = OutcomeInstanceKeys.producedBy + ".runtimeId";
  }
}
