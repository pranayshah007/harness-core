/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.step;

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
@FieldNameConstants(innerTypeName = "StepExecutionEntityKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.PMS)
@Entity(value = "stepExecutionEntity", noClassnameStored = true)
@Document("stepExecutionEntity")
@TypeAlias("stepExecutionEntity")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CDP)
public class StepExecutionEntity implements PersistentEntity, UuidAware {
  @org.springframework.data.annotation.Id @Id String uuid;
  @CreatedDate private Long createdAt;
  @LastModifiedDate private Long lastModifiedAt;

  @NotNull private String accountIdentifier;
  @NotNull private String orgIdentifier;
  @NotNull private String projectIdentifier;
  @NotNull private String stageExecutionId;
  @NotNull private String stepExecutionId;
  @NotNull private String planExecutionId;
  @NotNull private String pipelineIdentifier;
  private String stepName;
  private String stepIdentifier;
  private Status status;
  @NotNull private String stepType;
  @Nullable private Long startts;
  @Nullable private Long endts;
  @Nullable private FailureInfo failureInfo;

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "stepType", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @Nullable
  private StepExecutionDetails executionDetails;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("step_execution_entity_idx")
                 .field(StepExecutionEntityKeys.accountIdentifier)
                 .field(StepExecutionEntityKeys.orgIdentifier)
                 .field(StepExecutionEntityKeys.projectIdentifier)
                 .field(StepExecutionEntityKeys.stepExecutionId)
                 .descSortField(StepExecutionEntityKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("unique_step_execution_entity_idx")
                 .field(StepExecutionEntityKeys.accountIdentifier)
                 .field(StepExecutionEntityKeys.orgIdentifier)
                 .field(StepExecutionEntityKeys.projectIdentifier)
                 .field(StepExecutionEntityKeys.stepExecutionId)
                 .build())
        .build();
  }
}
