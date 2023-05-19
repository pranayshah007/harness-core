/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.service.impl.WorkflowExecutionServiceHelper.calculateCdPageCandidate;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ApiKeyInfo;
import io.harness.beans.CreatedByType;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.WorkflowType;
import io.harness.dataretention.AccountDataRetentionEntity;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.CompoundTextMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdSparseIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import software.wings.beans.Activity.ActivityKeys;
import software.wings.beans.ExecutionArgs.ExecutionArgsKeys;
import software.wings.beans.NameValuePair.NameValuePairKeys;
import software.wings.beans.PipelineExecution.PipelineExecutionKeys;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.concurrency.ConcurrencyStrategy;
import software.wings.beans.entityinterface.KeywordsAware;
import software.wings.beans.execution.WorkflowExecutionInfo;
import software.wings.persistence.artifact.Artifact;
import software.wings.sm.PipelineSummary;
import software.wings.sm.StateMachine;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.annotations.PrePersist;
import dev.morphia.annotations.Transient;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;

/**
 * The Class WorkflowExecution.
 */
@TargetModule(HarnessModule._957_CG_BEANS)
@OwnedBy(CDC)
@BreakDependencyOn("software.wings.service.impl.WorkflowExecutionServiceHelper")
@Data
@Builder
@FieldNameConstants(innerTypeName = "WorkflowExecutionKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "workflowExecutions", noClassnameStored = true)
@HarnessEntity(exportable = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@BreakDependencyOn("software.wings.service.impl.WorkflowExecutionServiceHelper")
public class WorkflowExecution implements PersistentRegularIterable, AccountDataRetentionEntity, UuidAware,
                                          CreatedAtAware, CreatedByAware, KeywordsAware, AccountAccess {
  public static String SEARCH2 = "search2";
  public static String ACCOUNT_ID_VALID_UNTIL = "accountId_validUntil";
  public static String ACCOUNT_ID_PIP_EXECUTIONID_CREATEDAT_APP_ID = "accountId_pipelineExecutionId_createdAt_appId";
  public static String SERVICE_GUARD = "service_guard";
  public static String LAST_INFRAMAPPING_SEARCH_2 = "lastInfraMappingSearch2";
  public static String WORKFLOW_EXECUTION_MONITOR = "workflowExecutionMonitor";
  public static String SEARCH_BY_SERVICEIDS = "searchByServiceIds";
  public static String ACCOUNTID_TAGS_CREATEDAT = "accountId_tags_createdAt";
  public static String APPID_WORKFLOWID_STATUS_CREATEDAT = "appid_workflowid_status_createdat";
  public static String APPID_WORKFLOWID_INFRAMAPPINGIDS_STATUS_CREATEDAT =
      "appid_workflowid_infraMappingIds_status_createdat";
  public static String APPID_STATUS_WORKFLOWID_INFRAMAPPINGIDS_CREATEDAT =
      "appid_status_workflowid_infraMappingIds_createdat";
  public static String ACCOUNTID_STARTTS_STATUS_PIPELINEEXECUTIONID = "accountId_startTs_status_pipelineExecutionId";
  public static String ACCOUNTID_ENDTS_STATUS_PIPELINEEXECUTIONID = "accountId_endTs_status_pipelineExecutionId";
  public static String ACCOUNTID_STARTTS_SERVICEIDS = "accountId_startTs_serviceIds";
  public static String APPID_CREATEDAT = "appId_createdAt";
  public static String ACCOUNTID_KEYWORDS_CREATEDAT_CDPAGECANDIDATE_APPID =
      "accountId_1_keywords_text_createdAt_-1_cdPageCandidate_1_appId_1";
  public static String APPID_WORKFLOWID_STATUS = "appid_workflowid_status_deployedServices_createdat";
  public static String APPID_WORKFLOWID_STATUS_DEPLOYEDSERVICES_CREATEDAT =
      "appid_workflowid_status_deployedServices_createdat";
  public static String ACCOUNTID_CREATEDAT_REJECTEDBYFREEZEWINDOWIDS =
      "accountId_1_createdAt_-1_rejectedByFreezeWindowIds_1";
  public static String ACCOUNTID_APPID_ONDEMANDROLLBACK_CREATEDAT = "accountId_appId_onDemandRollback_createdAt";
  public static String WFE_EXECUTIONS_SEARCH_WORKFLOWID = "wfe_executions_search_workflowId";
  public static String WFE_EXECUTIONS_SEARCH_SERVICEIDS = "wfe_executions_search_serviceIds";
  public static String WFE_EXECUTIONS_SEARCH_ENVIDS = "wfe_executions_search_envIds";
  public static String ACCOUNTID_APPID_WORKFLOWID_CREATEDAT_CDPAGECANDIDATE_STATUS =
      "accountId_appId_workflowId_createdAt_cdPageCandidate_status";
  public static String ACCOUNTID_APPID_SERVICEIDS_CREATEDAT_CDPAGECANDIDATE_STATUS =
      "accountId_appId_serviceIds_createdAt_cdPageCandidate_status";
  public static String ACCOUNTID_APPID_ENVIDS_CREATEDAT_CDPAGECANDIDATE_STATUS =
      "accountId_appId_envIds_createdAt_cdPageCandidate_status";
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name(SEARCH2)
                 .field(WorkflowExecutionKeys.workflowId)
                 .field(WorkflowExecutionKeys.status)
                 .descSortField(WorkflowExecutionKeys.endTs)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name(ACCOUNT_ID_PIP_EXECUTIONID_CREATEDAT_APP_ID)
                 .field(WorkflowExecutionKeys.accountId)
                 .field(WorkflowExecutionKeys.pipelineExecutionId)
                 .descSortField(WorkflowExecutionKeys.createdAt)
                 .rangeField(WorkflowExecutionKeys.appId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name(SERVICE_GUARD)
                 .field(WorkflowExecutionKeys.appId)
                 .field(WorkflowExecutionKeys.startTs)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name(LAST_INFRAMAPPING_SEARCH_2)
                 .field(WorkflowExecutionKeys.appId)
                 .field(WorkflowExecutionKeys.workflowType)
                 .field(WorkflowExecutionKeys.status)
                 .field(WorkflowExecutionKeys.infraMappingIds)
                 .descSortField(WorkflowExecutionKeys.createdAt)
                 .rangeField(WorkflowExecutionKeys.deployment)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name(WORKFLOW_EXECUTION_MONITOR)
                 .field(WorkflowExecutionKeys.status)
                 .field(WorkflowExecutionKeys.nextIteration)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name(SEARCH_BY_SERVICEIDS)
                 .field(WorkflowExecutionKeys.appId)
                 .field(WorkflowExecutionKeys.workflowId)
                 .field(WorkflowExecutionKeys.status)
                 .field(WorkflowExecutionKeys.serviceIds)
                 .descSortField(WorkflowExecutionKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name(ACCOUNTID_TAGS_CREATEDAT)
                 .field(WorkflowExecutionKeys.accountId)
                 .field(WorkflowExecutionKeys.tags_name)
                 .descSortField(WorkflowExecutionKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name(APPID_WORKFLOWID_STATUS_CREATEDAT)
                 .field(WorkflowExecutionKeys.appId)
                 .field(WorkflowExecutionKeys.workflowId)
                 .field(WorkflowExecutionKeys.status)
                 .descSortField(WorkflowExecutionKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name(APPID_WORKFLOWID_INFRAMAPPINGIDS_STATUS_CREATEDAT)
                 .field(WorkflowExecutionKeys.appId)
                 .field(WorkflowExecutionKeys.workflowId)
                 .field(WorkflowExecutionKeys.infraMappingIds)
                 .field(WorkflowExecutionKeys.status)
                 .descSortField(WorkflowExecutionKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name(APPID_STATUS_WORKFLOWID_INFRAMAPPINGIDS_CREATEDAT)
                 .field(WorkflowExecutionKeys.appId)
                 .field(WorkflowExecutionKeys.status)
                 .field(WorkflowExecutionKeys.workflowId)
                 .field(WorkflowExecutionKeys.infraMappingIds)
                 .descSortField(WorkflowExecutionKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name(ACCOUNTID_STARTTS_STATUS_PIPELINEEXECUTIONID)
                 .field(WorkflowExecutionKeys.accountId)
                 .field(WorkflowExecutionKeys.startTs)
                 .field(WorkflowExecutionKeys.status)
                 .field(WorkflowExecutionKeys.pipelineExecutionId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name(ACCOUNTID_ENDTS_STATUS_PIPELINEEXECUTIONID)
                 .field(WorkflowExecutionKeys.accountId)
                 .field(WorkflowExecutionKeys.endTs)
                 .field(WorkflowExecutionKeys.status)
                 .field(WorkflowExecutionKeys.pipelineExecutionId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name(ACCOUNTID_STARTTS_SERVICEIDS)
                 .field(WorkflowExecutionKeys.accountId)
                 .field(WorkflowExecutionKeys.startTs)
                 .field(WorkflowExecutionKeys.serviceIds)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name(APPID_CREATEDAT)
                 .field(WorkflowExecutionKeys.appId)
                 .descSortField(WorkflowExecutionKeys.createdAt)
                 .build())
        .add(CompoundTextMongoIndex.builder()
                 .name(ACCOUNTID_KEYWORDS_CREATEDAT_CDPAGECANDIDATE_APPID)
                 .field(WorkflowExecutionKeys.accountId)
                 .textField(WorkflowExecutionKeys.keywords)
                 .descSortField(WorkflowExecutionKeys.createdAt)
                 .rangeField(WorkflowExecutionKeys.cdPageCandidate)
                 .rangeField(WorkflowExecutionKeys.appId)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name(APPID_WORKFLOWID_STATUS_DEPLOYEDSERVICES_CREATEDAT)
                 .field(WorkflowExecutionKeys.appId)
                 .field(WorkflowExecutionKeys.workflowId)
                 .field(WorkflowExecutionKeys.status)
                 .field(WorkflowExecutionKeys.deployedServices)
                 .descSortField(WorkflowExecutionKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name(ACCOUNTID_CREATEDAT_REJECTEDBYFREEZEWINDOWIDS)
                 .field(WorkflowExecutionKeys.accountId)
                 .descSortField(WorkflowExecutionKeys.createdAt)
                 .rangeField(WorkflowExecutionKeys.rejectedByFreezeWindowIds)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name(ACCOUNTID_APPID_ONDEMANDROLLBACK_CREATEDAT)
                 .field(WorkflowExecutionKeys.accountId)
                 .field(WorkflowExecutionKeys.appId)
                 .field(WorkflowExecutionKeys.onDemandRollback)
                 .descSortField(WorkflowExecutionKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name(WFE_EXECUTIONS_SEARCH_WORKFLOWID)
                 .field(WorkflowExecutionKeys.accountId)
                 .descSortField(WorkflowExecutionKeys.createdAt)
                 .rangeField(WorkflowExecutionKeys.appId)
                 .rangeField(WorkflowExecutionKeys.workflowId)
                 .rangeField(WorkflowExecutionKeys.cdPageCandidate)
                 .rangeField(WorkflowExecutionKeys.status)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name(WFE_EXECUTIONS_SEARCH_SERVICEIDS)
                 .field(WorkflowExecutionKeys.accountId)
                 .descSortField(WorkflowExecutionKeys.createdAt)
                 .rangeField(WorkflowExecutionKeys.appId)
                 .rangeField(WorkflowExecutionKeys.serviceIds)
                 .rangeField(WorkflowExecutionKeys.cdPageCandidate)
                 .rangeField(WorkflowExecutionKeys.status)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name(WFE_EXECUTIONS_SEARCH_ENVIDS)
                 .field(WorkflowExecutionKeys.accountId)
                 .descSortField(WorkflowExecutionKeys.createdAt)
                 .rangeField(WorkflowExecutionKeys.appId)
                 .rangeField(WorkflowExecutionKeys.envIds)
                 .rangeField(WorkflowExecutionKeys.cdPageCandidate)
                 .rangeField(WorkflowExecutionKeys.status)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name(ACCOUNTID_APPID_WORKFLOWID_CREATEDAT_CDPAGECANDIDATE_STATUS)
                 .field(WorkflowExecutionKeys.accountId)
                 .field(WorkflowExecutionKeys.appId)
                 .field(WorkflowExecutionKeys.workflowId)
                 .descSortField(WorkflowExecutionKeys.createdAt)
                 .rangeField(WorkflowExecutionKeys.cdPageCandidate)
                 .rangeField(WorkflowExecutionKeys.status)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name(ACCOUNTID_APPID_SERVICEIDS_CREATEDAT_CDPAGECANDIDATE_STATUS)
                 .field(WorkflowExecutionKeys.accountId)
                 .field(WorkflowExecutionKeys.appId)
                 .field(WorkflowExecutionKeys.serviceIds)
                 .descSortField(WorkflowExecutionKeys.createdAt)
                 .rangeField(WorkflowExecutionKeys.cdPageCandidate)
                 .rangeField(WorkflowExecutionKeys.status)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name(ACCOUNTID_APPID_ENVIDS_CREATEDAT_CDPAGECANDIDATE_STATUS)
                 .field(WorkflowExecutionKeys.accountId)
                 .field(WorkflowExecutionKeys.appId)
                 .field(WorkflowExecutionKeys.envIds)
                 .descSortField(WorkflowExecutionKeys.createdAt)
                 .rangeField(WorkflowExecutionKeys.cdPageCandidate)
                 .rangeField(WorkflowExecutionKeys.status)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name(ACCOUNT_ID_VALID_UNTIL)
                 .field(ActivityKeys.accountId)
                 .field(ActivityKeys.validUntil)
                 .build())
        .build();
  }

  // TODO: Determine the right expiry duration for workflow exceptions
  public static final Duration EXPIRY = Duration.ofDays(7);

  @Id @NotNull(groups = {Update.class}) private String uuid;
  @NotNull protected String appId;
  private EmbeddedUser createdBy;
  private CreatedByType createdByType;
  @FdIndex private long createdAt;
  private String accountId;

  private String workflowId;

  private String stateMachineId;
  @JsonIgnore private StateMachine stateMachine;
  private String envId;
  private List<String> envIds;
  private List<String> workflowIds;
  private List<String> cloudProviderIds;
  @FdIndex private List<String> serviceIds;
  @FdIndex private List<String> infraMappingIds;
  private List<String> infraDefinitionIds;
  private String appName;
  private String envName;
  private EnvironmentType envType;
  private WorkflowType workflowType;
  private ExecutionStatus status;
  @Transient private Graph graph;

  @Transient private GraphNode executionNode; // used for workflow details.
  private PipelineExecution pipelineExecution; // used for pipeline details.

  private String pipelineExecutionId;
  private String stageName;
  private ErrorStrategy errorStrategy;

  private String name;
  private String releaseNo;
  private int total;
  private CountsByStatuses breakdown;

  private ExecutionArgs executionArgs;
  private List<ElementExecutionSummary> serviceExecutionSummaries;
  private LinkedHashMap<ExecutionStatus, StatusInstanceBreakdown> statusInstanceBreakdownMap;

  private Long startTs;
  private Long rollbackStartTs;
  private Long endTs;
  // Pre-calculated difference from endTs and startTs for indexing purposes
  private Long duration;
  private Long rollbackDuration;

  private EmbeddedUser triggeredBy;

  private PipelineSummary pipelineSummary;

  private List<EnvSummary> environments;

  private List<EnvSummary> deployedEnvironments;
  private List<String> deployedServices;
  private List<String> deployedCloudProviders;

  private List<BuildExecutionSummary> buildExecutionSummaries;

  private OrchestrationWorkflowType orchestrationType;

  private boolean isBaseline;

  private String deploymentTriggerId;
  private ApiKeyInfo triggeringApiKeyInfo;

  private List<Artifact> artifacts;
  private List<Artifact> rollbackArtifacts;
  private List<HelmChart> helmCharts;

  private Set<String> keywords;
  private boolean onDemandRollback;
  private boolean useSweepingOutputs;
  // Information of original execution is this is for OnDemand Rollback
  private WorkflowExecutionInfo originalExecution;

  private HelmExecutionSummary helmExecutionSummary;
  private List<AwsLambdaExecutionSummary> awsLambdaExecutionSummaries;
  private ConcurrencyStrategy concurrencyStrategy;
  private Boolean deployment;

  // For pipeline resume.
  //
  // pipelineResumeId is the pipeline execution id of the very first execution.
  // It makes getting the history very simple. Just filter by the same
  // pipelineResumeId and sort by createdAt.
  @FdSparseIndex private String pipelineResumeId;
  // latestPipelineResume is true only for the latest resumed execution.
  // It is required to make the list execution call efficient to fetch only
  // the latest execution.
  private boolean latestPipelineResume;

  // If a workflow execution is eligible to be displayed on CD page. (Indirect workflow executions and resumed pipelines
  // are not displayed on CD page)
  private boolean cdPageCandidate;

  private Long nextIteration;
  private List<NameValuePair> tags;
  private String message;
  @Transient private String failureDetails;

  private boolean isRollbackProvisionerAfterPhases;
  private boolean canOverrideFreeze;

  @Transient private String failedStepNames;
  @Transient private String failedStepTypes;

  private List<String> rejectedByFreezeWindowIds;
  private List<String> rejectedByFreezeWindowNames;

  // Making this consistent with data retention default of 183 days instead of "6 months"
  @Default
  @JsonIgnore
  @FdTtlIndex
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private Date validUntil = Date.from(OffsetDateTime.now().plusDays(183).toInstant());

  public String normalizedName() {
    if (isBlank(name)) {
      if (pipelineExecution != null && pipelineExecution.getPipeline() != null
          && isNotBlank(pipelineExecution.getPipeline().getName())) {
        return pipelineExecution.getPipeline().getName();
      }
      return String.valueOf(workflowType);
    }
    return name;
  }

  // TODO: this is silly, we should get rid of it
  public String displayName() {
    String dateSuffix = "";
    if (getCreatedAt() != 0) {
      dateSuffix = " - "
          + Instant.ofEpochMilli(getCreatedAt())
                .atZone(ZoneId.of("America/Los_Angeles"))
                .format(DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a"));
    }
    return name + dateSuffix;
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }

  @UtilityClass
  public static final class WorkflowExecutionKeys {
    public static final String executionArgs_pipelinePhaseElementId =
        executionArgs + "." + ExecutionArgsKeys.pipelinePhaseElementId;
    public static final String executionArgs_artifacts = executionArgs + "." + ExecutionArgsKeys.artifacts;
    public static final String executionArgs_artifacts_buildNo = executionArgs_artifacts + "."
        + "metadata"
        + "."
        + "buildNo";
    public static final String executionArgs_artifact_variables =
        executionArgs + "." + ExecutionArgsKeys.artifactVariables;
    public static final String tags_name = tags + "." + NameValuePairKeys.name;
    public static final String triggeredByID = triggeredBy + "." + User.BaseKeys.uuid;
    public static final String pipelineSummary_pipelineId = pipelineSummary + "." + ExecutionArgsKeys.pipelineId;
    public static final String pipelineExecution_pipelineStageExecutions =
        pipelineExecution + "." + PipelineExecutionKeys.pipelineStageExecutions;
    public static final String executionArgs_helmCharts = executionArgs + "." + ExecutionArgsKeys.helmCharts;
    public static final String executionArgs_helmCharts_displayName = executionArgs_helmCharts + "."
        + "displayName";
    public static final String serviceExecutionSummaries_instanceStatusSummaries_instanceElement_uuid =
        serviceExecutionSummaries + ".instanceStatusSummaries.instanceElement.uuid";
    public static final String originalExecution_executionId = originalExecution + ".executionId";
  }

  @PrePersist
  public void onSave() {
    this.cdPageCandidate = calculateCdPageCandidate(
        this.pipelineExecutionId, this.pipelineResumeId, this.latestPipelineResume, this.accountId);
  }
}
