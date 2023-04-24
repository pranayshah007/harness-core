/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.execution;

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
import io.harness.utils.StageStatus;

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
@FieldNameConstants(innerTypeName = "StageExecutionInfoKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "stageExecutionInfo", noClassnameStored = true)
@Document("stageExecutionInfo")
@TypeAlias("stageExecutionInfo")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CDP)
public class StageExecutionInfo implements PersistentEntity, UuidAware {
  @org.springframework.data.annotation.Id @Id String uuid;
  @CreatedDate private long createdAt;
  @LastModifiedDate private long lastModifiedAt;

  @NotNull private String accountIdentifier;
  @NotNull private String orgIdentifier;
  @NotNull private String projectIdentifier;
  @Nullable private String envIdentifier;
  @Nullable private String infraIdentifier;
  @Nullable private String serviceIdentifier;
  @NotNull private String stageExecutionId;
  @NotNull private String planExecutionId;
  @NotNull private StageStatus stageStatus;
  @NotNull private Status status;
  @Nullable private String deploymentIdentifier;
  @Nullable private ExecutionSummaryDetails executionSummaryDetails;
  @Nullable private Long rollbackDuration;
  @Nullable private Long startts;
  @Nullable private Long endts;

  @Nullable private String[] tags;
  @Nullable private ExecutionDetails executionDetails;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("sorted_stage_execution_info_idx")
                 .field(StageExecutionInfoKeys.accountIdentifier)
                 .field(StageExecutionInfoKeys.orgIdentifier)
                 .field(StageExecutionInfoKeys.projectIdentifier)
                 .field(StageExecutionInfoKeys.envIdentifier)
                 .field(StageExecutionInfoKeys.infraIdentifier)
                 .field(StageExecutionInfoKeys.serviceIdentifier)
                 .field(StageExecutionInfoKeys.stageExecutionId)
                 .descSortField(StageExecutionInfoKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("unique_stage_execution_info_idx")
                 .field(StageExecutionInfoKeys.accountIdentifier)
                 .field(StageExecutionInfoKeys.orgIdentifier)
                 .field(StageExecutionInfoKeys.projectIdentifier)
                 .field(StageExecutionInfoKeys.envIdentifier)
                 .field(StageExecutionInfoKeys.infraIdentifier)
                 .field(StageExecutionInfoKeys.serviceIdentifier)
                 .field(StageExecutionInfoKeys.stageExecutionId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("unique_stage_execution_info_deployment_identifier_idx")
                 .field(StageExecutionInfoKeys.accountIdentifier)
                 .field(StageExecutionInfoKeys.orgIdentifier)
                 .field(StageExecutionInfoKeys.projectIdentifier)
                 .field(StageExecutionInfoKeys.envIdentifier)
                 .field(StageExecutionInfoKeys.infraIdentifier)
                 .field(StageExecutionInfoKeys.serviceIdentifier)
                 .field(StageExecutionInfoKeys.deploymentIdentifier)
                 .field(StageExecutionInfoKeys.stageExecutionId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("unique_stage_execution_info_using_stage_execution_id_idx")
                 .field(StageExecutionInfoKeys.accountIdentifier)
                 .field(StageExecutionInfoKeys.stageExecutionId)
                 .field(StageExecutionInfoKeys.orgIdentifier)
                 .field(StageExecutionInfoKeys.projectIdentifier)
                 .build())
        .build();
  }
}
