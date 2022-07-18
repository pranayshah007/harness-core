/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans;

import static java.time.Duration.ofDays;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.Trimmed;
import io.harness.dto.FailureInfoDTO;
import io.harness.engine.executions.retry.RetryExecutionMetadata;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.governance.GovernanceMetadata;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;
import io.harness.pms.contracts.execution.ExecutionErrorInfo;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Setter;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "PlanExecutionSummaryKeys")
@Entity(value = "planExecutionsSummary", noClassnameStored = true)
@Document("planExecutionsSummary")
@TypeAlias("planExecutionsSummary")
@HarnessEntity(exportable = true)
@ChangeDataCapture(table = "pipeline_execution_summary_ci", dataStore = "pms-harness", fields = {},
    handler = "PipelineExecutionSummaryEntity")
@ChangeDataCapture(table = "pipeline_execution_summary_cd", dataStore = "pms-harness", fields = {},
    handler = "PipelineExecutionSummaryEntityCD")
@ChangeDataCapture(table = "service_infra_info", dataStore = "pms-harness", fields = {},
    handler = "PipelineExecutionSummaryEntityServiceAndInfra")
@StoreIn(DbAliases.PMS)
public class PipelineExecutionSummaryEntity implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware {
  public static final Duration TTL = ofDays(183);
  public static final long TTL_MONTHS = 6;

  @Setter @NonFinal @Id @org.mongodb.morphia.annotations.Id String uuid;

  @NotEmpty int runSequence;
  @NotEmpty String accountId;
  @NotEmpty String orgIdentifier;
  @Trimmed @NotEmpty String projectIdentifier;

  @NotEmpty String pipelineIdentifier;
  @NotEmpty @FdUniqueIndex String planExecutionId;
  @NotEmpty String name;

  @Builder.Default Boolean pipelineDeleted = Boolean.FALSE;

  Status internalStatus;
  ExecutionStatus status;

  String inputSetYaml;
  String pipelineTemplate; // saving the template here because after an execution, the pipeline can be updated

  @Singular @Size(max = 128) List<NGTag> tags;

  @Builder.Default Map<String, org.bson.Document> moduleInfo = new HashMap<>();
  @Setter @NonFinal @Builder.Default Map<String, GraphLayoutNodeDTO> layoutNodeMap = new HashMap<>();
  List<String> modules;
  Set<String> executedModules;
  String startingNodeId;

  ExecutionTriggerInfo executionTriggerInfo;
  @Deprecated ExecutionErrorInfo executionErrorInfo;
  @Deprecated ByteString gitSyncBranchContext;
  EntityGitDetails entityGitDetails;
  FailureInfoDTO failureInfo;
  GovernanceMetadata governanceMetadata;
  StagesExecutionMetadata stagesExecutionMetadata;
  Boolean allowStagesExecution;

  // git simplification params
  StoreType storeType;
  String connectorRef;

  Long startTs;
  Long endTs;

  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());

  // TODO: removing these getters after 6 months (13/10/21)
  public Boolean isLatestExecution() {
    if (isLatestExecution == null) {
      return true;
    }
    return isLatestExecution;
  }

  public RetryExecutionMetadata getRetryExecutionMetadata() {
    if (retryExecutionMetadata == null) {
      return RetryExecutionMetadata.builder()
          .parentExecutionId(planExecutionId)
          .rootExecutionId(planExecutionId)
          .build();
    }
    return retryExecutionMetadata;
  }

  RetryExecutionMetadata retryExecutionMetadata;
  Boolean isLatestExecution;
  @Setter @NonFinal @SchemaIgnore @FdIndex @CreatedDate long createdAt;
  @Setter @NonFinal @SchemaIgnore @NotNull @LastModifiedDate long lastUpdatedAt;
  @Setter @NonFinal @Version Long version;

  public ExecutionStatus getStatus() {
    if (internalStatus == null) {
      // For backwards compatibility when internalStatus was not there
      return status;
    }
    return internalStatus == Status.NO_OP ? ExecutionStatus.NOTSTARTED
                                          : ExecutionStatus.getExecutionStatus(internalStatus);
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_organizationId_projectId_planExecutionId")
                 .unique(true)
                 .field(PlanExecutionSummaryKeys.accountId)
                 .field(PlanExecutionSummaryKeys.orgIdentifier)
                 .field(PlanExecutionSummaryKeys.projectIdentifier)
                 .field(PlanExecutionSummaryKeys.planExecutionId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_organizationId_projectId_pipelineId")
                 .field(PlanExecutionSummaryKeys.accountId)
                 .field(PlanExecutionSummaryKeys.orgIdentifier)
                 .field(PlanExecutionSummaryKeys.projectIdentifier)
                 .field(PlanExecutionSummaryKeys.pipelineIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_organizationId_projectId_createdAt_idx")
                 .field(PlanExecutionSummaryKeys.projectIdentifier)
                 .field(PlanExecutionSummaryKeys.orgIdentifier)
                 .field(PlanExecutionSummaryKeys.accountId)
                 .field(PlanExecutionSummaryKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_organizationId_projectId_pipelineId_createdAt_idx")
                 .field(PlanExecutionSummaryKeys.pipelineIdentifier)
                 .field(PlanExecutionSummaryKeys.projectIdentifier)
                 .field(PlanExecutionSummaryKeys.orgIdentifier)
                 .field(PlanExecutionSummaryKeys.accountId)
                 .field(PlanExecutionSummaryKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_executed_modules_startTs_idx")
                 .field(PlanExecutionSummaryKeys.accountId)
                 .field(PlanExecutionSummaryKeys.executedModules)
                 .field(PlanExecutionSummaryKeys.startTs)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_organizationId_projectId_createdAt_modules_idx")
                 .field(PlanExecutionSummaryKeys.modules)
                 .field(PlanExecutionSummaryKeys.projectIdentifier)
                 .field(PlanExecutionSummaryKeys.orgIdentifier)
                 .field(PlanExecutionSummaryKeys.accountId)
                 .field(PlanExecutionSummaryKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("rootExecution_createdAt_id")
                 .field(PlanExecutionSummaryKeys.rootExecutionId)
                 .descSortField(PlanExecutionSummaryKeys.createdAt)
                 .build())
        .build();
  }

  @UtilityClass
  public static class PlanExecutionSummaryKeys {
    public String triggerType = PlanExecutionSummaryKeys.executionTriggerInfo + "."
        + "triggerType";
    public String triggeredBy = PlanExecutionSummaryKeys.executionTriggerInfo + "."
        + "triggeredBy";
    public String rootExecutionId = PlanExecutionSummaryKeys.retryExecutionMetadata + "."
        + "rootExecutionId";
    public String parentExecutionId = PlanExecutionSummaryKeys.retryExecutionMetadata + "."
        + "parentExecutionId";
  }

  public boolean isStagesExecutionAllowed() {
    return allowStagesExecution != null && allowStagesExecution;
  }
}
