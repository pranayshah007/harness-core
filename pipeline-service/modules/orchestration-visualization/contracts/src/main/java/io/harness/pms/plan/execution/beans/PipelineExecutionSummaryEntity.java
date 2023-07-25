/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans;
import static java.time.Duration.ofDays;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.AbortedBy;
import io.harness.data.validator.Trimmed;
import io.harness.dto.FailureInfoDTO;
import io.harness.engine.executions.retry.RetryExecutionMetadata;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.governance.GovernanceMetadata;
import io.harness.mongo.collation.CollationLocale;
import io.harness.mongo.collation.CollationStrength;
import io.harness.mongo.index.Collation;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.pms.contracts.execution.ExecutionErrorInfo;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.yaml.core.NGLabel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import dev.morphia.annotations.Entity;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "PlanExecutionSummaryKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "planExecutionsSummary", noClassnameStored = true)
@Document("planExecutionsSummary")
@TypeAlias("planExecutionsSummary")
@HarnessEntity(exportable = true)
@ChangeDataCapture(table = "pipeline_execution_summary_ci", dataStore = "pms-harness", fields = {},
    handler = "PipelineExecutionSummaryEntity")
@ChangeDataCapture(table = "pipeline_execution_summary", dataStore = "pms-harness", fields = {},
    handler = "PipelineExecutionSummaryEntityAllStages")
@ChangeDataCapture(table = "pipeline_execution_summary_cd", dataStore = "pms-harness", fields = {},
    handler = "PipelineExecutionSummaryEntityCD")
@ChangeDataCapture(table = "service_infra_info", dataStore = "pms-harness", fields = {},
    handler = "PipelineExecutionSummaryEntityServiceAndInfra")
@ChangeDataCapture(table = "stage_execution_summary_ci", dataStore = "pms-harness", fields = {},
    handler = "PipelineExecutionSummaryEntityCIStage")
@ChangeDataCapture(table = "execution_tags_info_ng", dataStore = "pms-harness", fields = {}, handler = "TagsInfoNGCD")
@ChangeDataCapture(table = "runtime_inputs_info", dataStore = "pms-harness", fields = {}, handler = "RuntimeInputsInfo")
@ChangeDataCapture(table = "stage_execution", dataStore = "pms-harness", fields = {}, handler = "ApprovalStage")
public class PipelineExecutionSummaryEntity implements PersistentEntity, UuidAware {
  public static final Duration TTL = ofDays(183);
  public static final long TTL_MONTHS = 6;

  @Setter @NonFinal @Id @dev.morphia.annotations.Id String uuid;

  @NotEmpty @Builder.Default Integer runSequence = 0;
  @NotEmpty String accountId;
  @NotEmpty String orgIdentifier;
  @Trimmed @NotEmpty String projectIdentifier;

  @NotEmpty String pipelineIdentifier;
  // Get on PlanExecutionId index
  @NotEmpty @FdUniqueIndex String planExecutionId;
  @NotEmpty String name;

  @Builder.Default Boolean pipelineDeleted = Boolean.FALSE;

  Status internalStatus;
  ExecutionStatus status;
  AbortedBy abortedBy;

  String inputSetYaml;
  String pipelineTemplate; // saving the template here because after an execution, the pipeline can be updated
  Boolean executionInputConfigured;

  @Singular @Size(max = 128) List<NGTag> tags;
  @Singular @Size(max = 128) List<NGLabel> labels;

  @Builder.Default Map<String, org.bson.Document> moduleInfo = new HashMap<>();
  @Setter @NonFinal @Builder.Default Map<String, GraphLayoutNodeDTO> layoutNodeMap = new HashMap<>();
  String firstRollbackStageGraphId;
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

  String pipelineVersion;

  Boolean notifyOnlyMe;

  ExecutionMode executionMode; // this is used to filter out rollback mode executions from executions list API
  RollbackExecutionInfo rollbackExecutionInfo;

  // TTL index
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
  PipelineStageInfo parentStageInfo;
  Boolean isLatestExecution;
  // Required Index for PipelineTelemetryPublisher
  @Setter @NonFinal @SchemaIgnore @FdIndex @CreatedDate @Builder.Default Long createdAt = 0L;
  @Setter @NonFinal @SchemaIgnore @NotNull @LastModifiedDate @Builder.Default Long lastUpdatedAt = 0L;
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
    return ImmutableList
        .<MongoIndex>builder()
        // Required from PmsExecutionSummaryRepository
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
                 .name("accountId_organizationId_projectId_createdAt_modules_idx")
                 .field(PlanExecutionSummaryKeys.modules)
                 .field(PlanExecutionSummaryKeys.projectIdentifier)
                 .field(PlanExecutionSummaryKeys.orgIdentifier)
                 .field(PlanExecutionSummaryKeys.accountId)
                 .field(PlanExecutionSummaryKeys.createdAt)
                 .build())
        // fetchPipelineSummaryEntityFromRootParentId in repoCustomImpl
        .add(SortCompoundMongoIndex.builder()
                 .name("rootExecution_createdAt_id")
                 .field(PlanExecutionSummaryKeys.rootExecutionId)
                 .descSortField(PlanExecutionSummaryKeys.createdAt)
                 .build())
        // Sort queries are added for list page
        // New Index having all filters index without repo and branch
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_startTs_repo_branch_pipelineIds_status_modules_parent_info_range_idx")
                 .field(PlanExecutionSummaryKeys.accountId)
                 .field(PlanExecutionSummaryKeys.orgIdentifier)
                 .field(PlanExecutionSummaryKeys.projectIdentifier)
                 .descSortField(PlanExecutionSummaryKeys.startTs)
                 // In pipeline Identifier list
                 .ascRangeField(PlanExecutionSummaryKeys.entityGitDetailsRepoName)
                 .ascRangeField(PlanExecutionSummaryKeys.entityGitDetailsBranch)
                 .ascRangeField(PlanExecutionSummaryKeys.pipelineIdentifier)
                 .ascRangeField(PlanExecutionSummaryKeys.status)
                 .ascRangeField(PlanExecutionSummaryKeys.modules)
                 .ascRangeField(PlanExecutionSummaryKeys.isChildPipeline)
                 .build())
        // Sort queries are added for list page
        .add(
            SortCompoundMongoIndex.builder()
                .name(
                    "accountId_orgId_projectId_name_startTs_repo_branch_pipelineIds_status_modules_parent_info_range_WithCollationIdx")
                .field(PlanExecutionSummaryKeys.accountId)
                .field(PlanExecutionSummaryKeys.orgIdentifier)
                .field(PlanExecutionSummaryKeys.projectIdentifier)
                .descSortField(PlanExecutionSummaryKeys.name)
                // For range in startTs
                .ascRangeField(PlanExecutionSummaryKeys.startTs)
                // In pipeline Identifier list
                .ascRangeField(PlanExecutionSummaryKeys.entityGitDetailsRepoName)
                .ascRangeField(PlanExecutionSummaryKeys.entityGitDetailsBranch)
                .ascRangeField(PlanExecutionSummaryKeys.pipelineIdentifier)
                .ascRangeField(PlanExecutionSummaryKeys.status)
                .ascRangeField(PlanExecutionSummaryKeys.modules)
                .ascRangeField(PlanExecutionSummaryKeys.isChildPipeline)
                .collation(
                    Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.SECONDARY).build())
                .build())
        // Sort queries are added for list page
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_status_startTs_repo_branch_pipelineIds_modules_parent_info_range_idx")
                 .field(PlanExecutionSummaryKeys.accountId)
                 .field(PlanExecutionSummaryKeys.orgIdentifier)
                 .field(PlanExecutionSummaryKeys.projectIdentifier)
                 .descSortField(PlanExecutionSummaryKeys.status)
                 // For range in startTs
                 .ascRangeField(PlanExecutionSummaryKeys.startTs)
                 // In pipeline Identifier list
                 .ascRangeField(PlanExecutionSummaryKeys.entityGitDetailsRepoName)
                 .ascRangeField(PlanExecutionSummaryKeys.entityGitDetailsBranch)
                 .ascRangeField(PlanExecutionSummaryKeys.pipelineIdentifier)
                 .ascRangeField(PlanExecutionSummaryKeys.modules)
                 .ascRangeField(PlanExecutionSummaryKeys.isChildPipeline)
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
    public String entityGitDetailsRepoName = PlanExecutionSummaryKeys.entityGitDetails + "."
        + "repoName";
    public String entityGitDetailsRepoIdentifier = PlanExecutionSummaryKeys.entityGitDetails + "."
        + "repoIdentifier";
    public String entityGitDetailsBranch = PlanExecutionSummaryKeys.entityGitDetails + "."
        + "branch";
    public String tagsKey = PlanExecutionSummaryKeys.tags + "."
        + "key";
    public String tagsValue = PlanExecutionSummaryKeys.tags + "."
        + "value";
    public String labelsKey = PlanExecutionSummaryKeys.labels + "."
        + "key";
    public String labelsValue = PlanExecutionSummaryKeys.labels + "."
        + "value";
    public String isChildPipeline = PlanExecutionSummaryKeys.parentStageInfo + "."
        + "hasParentPipeline";
    public String rollbackModeExecutionId = PlanExecutionSummaryKeys.rollbackExecutionInfo + "."
        + "rollbackModeExecutionId";
  }

  public boolean isStagesExecutionAllowed() {
    return allowStagesExecution != null && allowStagesExecution;
  }

  public String getRollbackModeExecutionId() {
    return rollbackExecutionInfo != null ? rollbackExecutionInfo.getRollbackModeExecutionId() : null;
  }
}
