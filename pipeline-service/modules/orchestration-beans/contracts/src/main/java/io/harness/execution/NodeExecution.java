/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.execution;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.pms.steps.identity.IdentityStepParameters;
import io.harness.interrupts.InterruptEffect;
import io.harness.logging.UnitProgress;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAccess;
import io.harness.plan.Node;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.run.NodeRunInfo;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.OrchestrationMap;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.utils.OrchestrationMapBackwardCompatibilityUtils;
import io.harness.timeout.TimeoutDetails;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import dev.morphia.annotations.Entity;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import lombok.experimental.Wither;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(CDC)
@Value
@Builder
@FieldNameConstants(innerTypeName = "NodeExecutionKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "nodeExecutions", noClassnameStored = true)
@Document("nodeExecutions")
@TypeAlias("nodeExecution")
public class NodeExecution implements PersistentEntity, UuidAccess, PmsNodeExecution {
  public static final long TTL_MONTHS = 6;

  // Immutable
  @Wither @Id @dev.morphia.annotations.Id String uuid;
  @NotNull Ambiance ambiance;
  @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE) Node planNode;
  @NotNull ExecutionMode mode;
  // Required for debugging, can be removed later
  @Wither @FdIndex @CreatedDate Long createdAt;
  private Long startTs;
  private Long endTs;
  private Duration initialWaitDuration;
  private Integer levelCount;
  // TTL index
  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());

  // Resolved StepParameters stored just before invoking step.
  @Deprecated Map<String, Object> resolvedStepParameters;
  @Deprecated PmsStepParameters resolvedInputs;

  @Wither PmsStepParameters resolvedParams;

  // For Wait Notify
  String notifyId;

  // Relationships
  String parentId;
  String nextId;
  String previousId;

  // Mutable
  @Wither @LastModifiedDate Long lastUpdatedAt;
  Status status;
  @Wither @Version Long version;

  @Singular List<ExecutableResponse> executableResponses;
  @Singular List<InterruptEffect> interruptHistories;
  FailureInfo failureInfo;
  NodeRunInfo nodeRunInfo;

  @Builder.Default Boolean executionInputConfigured = false;
  // Retries
  @Singular List<String> retryIds;
  @Builder.Default Boolean oldRetry = false;

  // Timeout
  List<String> timeoutInstanceIds;
  TimeoutDetails timeoutDetails;

  // Todo: Move unitProgress and progressData to another collection
  @Singular @Deprecated List<UnitProgress> unitProgresses;
  Map<String, Object> progressData;

  AdviserResponse adviserResponse;
  // Timeouts for advisers
  List<String> adviserTimeoutInstanceIds;
  TimeoutDetails adviserTimeoutDetails;

  // If this is a retry node then this field is populated
  String originalNodeExecutionId;

  SkipType skipGraphType;
  String module;
  String name;
  StepType stepType;
  String nodeId;
  String identifier;
  String stageFqn;
  String group;

  public ExecutableResponse obtainLatestExecutableResponse() {
    if (isEmpty(executableResponses)) {
      return null;
    }
    return executableResponses.get(executableResponses.size() - 1);
  }

  @Override
  public NodeType getNodeType() {
    return NodeType.valueOf(AmbianceUtils.obtainNodeType(ambiance));
  }

  public String getPlanExecutionId() {
    return ambiance.getPlanExecutionId();
  }

  public String getPlanId() {
    return ambiance.getPlanId();
  }

  @UtilityClass
  public static class NodeExecutionKeys {
    public static final String id = "_id";
    public static final String planExecutionId = NodeExecutionKeys.ambiance + "."
        + "planExecutionId";

    public static final String stepCategory = NodeExecutionKeys.stepType + "."
        + "stepCategory";

    public static final String stageFqn = NodeExecutionKeys.planNode + "."
        + "stageFqn";
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList
        .<MongoIndex>builder()
        // used by getByPlanNodeUuid
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_nodeId_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.nodeId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_identifier_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.identifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_oldRetry_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.oldRetry)
                 .build())
        // Used by fetchNodeExecutionsStatusesWithoutOldRetries
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_status_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.status)
                 .build())
        // Used by findCountByParentIdAndStatusIn and fetchChildrenNodeExecutionsIterator
        .add(CompoundMongoIndex.builder()
                 .name("parentId_status_idx")
                 .field(NodeExecutionKeys.parentId)
                 .field(NodeExecutionKeys.status)
                 .field(NodeExecutionKeys.oldRetry)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_mode_status_oldRetry_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.mode)
                 .field(NodeExecutionKeys.status)
                 .field(NodeExecutionKeys.oldRetry)
                 .build())
        // Used by fetchAllStepNodeExecutions
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_stepCategory_identifier_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.stepCategory)
                 .field(NodeExecutionKeys.identifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_stageFqn_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.stageFqn)
                 .build())
        // updateRelationShipsForRetryNode
        .add(CompoundMongoIndex.builder().name("previous_id_idx").field(NodeExecutionKeys.previousId).build())
        // fetchChildrenNodeExecutionsIterator
        .add(SortCompoundMongoIndex.builder()
                 .name("planExecutionId_parentId_createdAt_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.parentId)
                 .descRangeField(NodeExecutionKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder().name("status_idx").field(NodeExecutionKeys.status).build())
        .build();
  }

  public ByteString getResolvedStepParametersBytes() {
    if (this.getNodeType().equals(NodeType.IDENTITY_PLAN_NODE)) {
      IdentityStepParameters build =
          IdentityStepParameters.builder().originalNodeExecutionId(originalNodeExecutionId).build();
      return ByteString.copyFromUtf8(emptyIfNull(RecastOrchestrationUtils.toJson(build)));
    }
    String resolvedStepParams = RecastOrchestrationUtils.toJson(this.getResolvedStepParameters());
    return ByteString.copyFromUtf8(emptyIfNull(resolvedStepParams));
  }

  public PmsStepParameters getPmsStepParameters() {
    return PmsStepParameters.parse(resolvedInputs);
  }

  public OrchestrationMap getPmsProgressData() {
    return OrchestrationMapBackwardCompatibilityUtils.extractToOrchestrationMap(progressData);
  }

  public PmsStepParameters getResolvedStepParameters() {
    if (resolvedStepParameters != null) {
      return PmsStepParameters.parse(
          OrchestrationMapBackwardCompatibilityUtils.extractToOrchestrationMap(resolvedStepParameters));
    }
    return resolvedParams;
  }

  public <T extends Node> T getNode() {
    return (T) planNode;
  }

  public String getModule() {
    if (EmptyPredicate.isNotEmpty(module)) {
      return module;
    }
    return planNode == null ? null : planNode.getServiceName();
  }

  public String getName() {
    if (EmptyPredicate.isNotEmpty(name)) {
      return name;
    }
    return planNode == null ? null : planNode.getName();
  }

  public StepType getStepType() {
    if (stepType != null) {
      return stepType;
    }
    return planNode == null ? null : planNode.getStepType();
  }

  public String getNodeId() {
    if (EmptyPredicate.isNotEmpty(nodeId)) {
      return nodeId;
    }
    return planNode == null ? null : planNode.getUuid();
  }

  public String getIdentifier() {
    if (EmptyPredicate.isNotEmpty(identifier)) {
      return identifier;
    }
    return planNode == null ? null : planNode.getIdentifier();
  }

  public String getStageFqn() {
    if (EmptyPredicate.isNotEmpty(stageFqn)) {
      return stageFqn;
    }
    return planNode == null ? null : planNode.getStageFqn();
  }

  public String getGroup() {
    if (EmptyPredicate.isNotEmpty(group)) {
      return group;
    }
    return planNode == null ? null : planNode.getGroup();
  }

  public SkipType getSkipGraphType() {
    if (skipGraphType != null) {
      return skipGraphType;
    }
    return planNode == null ? null : planNode.getSkipGraphType();
  }
}
