/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.entities;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eraro.ErrorCode.APPROVAL_REJECTION;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eraro.Level;
import io.harness.exception.InvalidArgumentsException;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance.HarnessApprovalInstanceKeys;
import io.harness.timeout.TimeoutParameters;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "ApprovalInstanceKeys")
@StoreIn(DbAliases.PMS)
@Document("approvalInstances")
@Entity(value = "approvalInstances", noClassnameStored = true)
@Persistent
public abstract class ApprovalInstance implements PersistentEntity, PersistentRegularIterable {
  public static final long TTL_MONTHS = 6;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("status_deadline")
                 .field(ApprovalInstanceKeys.status)
                 .ascSortField(ApprovalInstanceKeys.deadline)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("status_type_nextIteration")
                 .field(ApprovalInstanceKeys.status)
                 .field(ApprovalInstanceKeys.type)
                 .descSortField(ApprovalInstanceKeys.nextIteration)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_status_type_nodeExecutionId")
                 .field(ApprovalInstanceKeys.planExecutionId)
                 .field(ApprovalInstanceKeys.status)
                 .field(ApprovalInstanceKeys.type)
                 .field(ApprovalInstanceKeys.nodeExecutionId)
                 .build())
        .add(
            SortCompoundMongoIndex.builder()
                .name(
                    "accountId_orgIdentifier_projectIdentifier_pipelineIdentifier_approvalKey_status_isAutoRejectEnabled_createdAt")
                .field(ApprovalInstanceKeys.accountId)
                .field(ApprovalInstanceKeys.orgIdentifier)
                .field(ApprovalInstanceKeys.projectIdentifier)
                .field(ApprovalInstanceKeys.pipelineIdentifier)
                .field(HarnessApprovalInstanceKeys.approvalKey)
                .field(ApprovalInstanceKeys.status)
                .field(HarnessApprovalInstanceKeys.isAutoRejectEnabled)
                .rangeField(ApprovalInstanceKeys.createdAt)
                .build())
        .build();
  }

  @Id @dev.morphia.annotations.Id String id;

  @NotNull Ambiance ambiance;

  // TTL index
  @FdTtlIndex Date validUntil;

  // preferably use these ambiance fields saved at first-level
  @FdIndex @NotNull String nodeExecutionId;
  @NotNull String planExecutionId;
  @NotNull String accountId;
  @NotNull String orgIdentifier;
  @NotNull String projectIdentifier;
  @NotNull String pipelineIdentifier;

  @NotNull ApprovalType type;
  @NotNull ApprovalStatus status;
  String errorMessage;
  long deadline;

  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
  @Version Long version;

  long nextIteration;

  @JsonIgnore
  public boolean hasExpired() {
    return deadline < System.currentTimeMillis();
  }

  public AutoLogContext autoLogContext() {
    return new AutoLogContext(logContextMap(), OVERRIDE_NESTS);
  }

  private Map<String, String> logContextMap() {
    Map<String, String> logContext = new HashMap<>(AmbianceUtils.logContextMap(ambiance));
    logContext.put("approvalInstanceId", id);
    logContext.put("approvalType", type.getDisplayName());
    logContext.put(ApprovalInstanceKeys.nodeExecutionId, nodeExecutionId);
    return logContext;
  }

  protected void updateFromStepParameters(Ambiance ambiance, StepElementParameters stepParameters) {
    if (stepParameters == null) {
      return;
    }

    setId(generateUuid());
    setAmbiance(ambiance);

    // set these ambiance fields as first level fields
    setNodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    setAccountId(AmbianceUtils.getAccountId(ambiance));
    setOrgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance));
    setProjectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance));
    setPipelineIdentifier(AmbianceUtils.getPipelineIdentifier(ambiance));
    setPlanExecutionId(ambiance.getPlanExecutionId());

    setType(ApprovalType.fromName(stepParameters.getType()));
    setStatus(ApprovalStatus.WAITING);
    setDeadline(calculateDeadline(stepParameters.getTimeout()));
    setValidUntil(Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant()));
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return this.nextIteration;
  }

  @Override
  public String getUuid() {
    return id;
  }

  private static long calculateDeadline(ParameterField<String> timeoutField) {
    if (ParameterField.isNull(timeoutField)) {
      return TimeoutParameters.DEFAULT_TIMEOUT_IN_MILLIS;
    } else if (timeoutField.isExpression()) {
      throw new InvalidArgumentsException(String.format("Invalid timeout: '%s'", timeoutField.fetchFinalValue()));
    } else if (timeoutField.getValue() == null) {
      return TimeoutParameters.DEFAULT_TIMEOUT_IN_MILLIS;
    }

    Timeout timeout = Timeout.fromString(timeoutField.getValue());
    if (timeout == null) {
      throw new InvalidArgumentsException(String.format("Invalid timeout: '%s'", timeoutField.fetchFinalValue()));
    }
    return System.currentTimeMillis() + timeout.getTimeoutInMillis();
  }

  public FailureInfo getFailureInfo() {
    if (status == ApprovalStatus.REJECTED) {
      FailureData failureData = FailureData.newBuilder()
                                    .addFailureTypes(FailureType.APPROVAL_REJECTION)
                                    .setLevel(Level.ERROR.name())
                                    .setCode(APPROVAL_REJECTION.name())
                                    .setMessage("Approval Step has been Rejected")
                                    .build();
      return FailureInfo.newBuilder().addFailureData(failureData).build();
    }
    return null;
  }
}
