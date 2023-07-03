/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.stage;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.utils.StageStatus;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@ToString
@FieldNameConstants(innerTypeName = "StageExecutionEntityKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.PMS)
@Entity(value = "stageExecutionEntity", noClassnameStored = true)
@Document("stageExecutionEntity")
@TypeAlias("stageExecutionEntity")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CDP)
public class StageExecutionEntity implements PersistentEntity, UuidAware {
  @org.springframework.data.annotation.Id @Id String uuid;
  @CreatedDate private long createdAt;
  @LastModifiedDate private long lastModifiedAt;

  @NotNull private String accountIdentifier;
  @NotNull private String orgIdentifier;
  @NotNull private String projectIdentifier;
  @NotNull private String stageExecutionId;
  private String planExecutionId;
  private String pipelineIdentifier;
  private String stageName;
  private String stageIdentifier;
  private String stageType;
  @NotNull private StageStatus stageStatus;
  private Status status;
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "stageType", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @Nullable
  private StageExecutionSummaryDetails stageExecutionSummaryDetails;
  @Nullable private Long rollbackDuration;
  @Nullable private Long startts;
  @Nullable private Long endts;
  @Nullable private FailureInfo failureInfo;

  @Nullable private String[] tags;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("stage_execution_entity_idx")
                 .field(StageExecutionEntityKeys.accountIdentifier)
                 .field(StageExecutionEntityKeys.orgIdentifier)
                 .field(StageExecutionEntityKeys.projectIdentifier)
                 .field(StageExecutionEntityKeys.stageExecutionId)
                 .descSortField(StageExecutionEntityKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("unique_stage_execution_entity_idx")
                 .field(StageExecutionEntityKeys.accountIdentifier)
                 .field(StageExecutionEntityKeys.orgIdentifier)
                 .field(StageExecutionEntityKeys.projectIdentifier)
                 .field(StageExecutionEntityKeys.stageExecutionId)
                 .build())
        .build();
  }
}
