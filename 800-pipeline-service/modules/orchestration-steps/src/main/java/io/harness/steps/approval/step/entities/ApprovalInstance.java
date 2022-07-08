/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.entities;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;
import io.harness.exception.InvalidArgumentsException;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.index.FdIndex;
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
import io.harness.timeout.TimeoutParameters;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
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
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(CDC)
@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "ApprovalInstanceKeys")
@Document("approvalInstances")
@Entity(value = "approvalInstances", noClassnameStored = true)
@Persistent
@StoreIn(DbAliases.PMS)
public abstract class ApprovalInstance implements PersistentEntity, PersistentRegularIterable {
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
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id String id;

  @NotNull Ambiance ambiance;
  @FdIndex @NotNull String nodeExecutionId;

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
    setNodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    setType(ApprovalType.fromName(stepParameters.getType()));
    setStatus(ApprovalStatus.WAITING);
    setDeadline(calculateDeadline(stepParameters.getTimeout()));
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
                                    .addFailureTypes(FailureType.UNKNOWN_FAILURE)
                                    .setLevel(Level.ERROR.name())
                                    .setCode(GENERAL_ERROR.name())
                                    .setMessage("Approval Step has been Rejected")
                                    .build();
      return FailureInfo.newBuilder().addFailureData(failureData).build();
    }
    return null;
  }
}
