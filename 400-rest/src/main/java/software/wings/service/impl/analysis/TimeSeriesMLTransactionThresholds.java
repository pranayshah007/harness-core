/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;

import software.wings.beans.Base;
import software.wings.metrics.TimeSeriesCustomThresholdType;
import software.wings.metrics.TimeSeriesMetricDefinition;
import software.wings.sm.StateType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "TimeSeriesMLTransactionThresholdKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "timeseriesTransactionThresholds", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class TimeSeriesMLTransactionThresholds extends Base implements AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("timeseriesThresholdsQueryIndex")
                 .field("appId")
                 .field(TimeSeriesMLTransactionThresholdKeys.serviceId)
                 .field(TimeSeriesMLTransactionThresholdKeys.stateType)
                 .field(TimeSeriesMLTransactionThresholdKeys.groupName)
                 .field(TimeSeriesMLTransactionThresholdKeys.transactionName)
                 .field(TimeSeriesMLTransactionThresholdKeys.metricName)
                 .field(TimeSeriesMLTransactionThresholdKeys.cvConfigId)
                 .field(TimeSeriesMLTransactionThresholdKeys.thresholdType)
                 .build())
        .build();
  }
  @NotEmpty private String serviceId;

  @NotEmpty private String workflowId;

  @NotEmpty private StateType stateType;

  @NotEmpty private String groupName;

  @NotEmpty private String transactionName;

  @NotEmpty private String metricName;

  @NotEmpty private String cvConfigId;

  @FdIndex private String accountId;

  TimeSeriesMetricDefinition thresholds;

  TimeSeriesCustomThresholdType thresholdType = TimeSeriesCustomThresholdType.ACCEPTABLE;

  private String customThresholdRefId;

  private int version;

  public TimeSeriesMLTransactionThresholds cloneWithoutCustomThresholds() {
    return TimeSeriesMLTransactionThresholds.builder()
        .serviceId(serviceId)
        .workflowId(workflowId)
        .stateType(stateType)
        .groupName(groupName)
        .transactionName(transactionName)
        .metricName(metricName)
        .cvConfigId(cvConfigId)
        .accountId(accountId)
        .thresholds(TimeSeriesMetricDefinition.builder()
                        .metricName(thresholds.getMetricName())
                        .metricType(thresholds.getMetricType())
                        .tags(thresholds.getTags() != null ? Sets.newHashSet(thresholds.getTags()) : null)
                        .categorizedThresholds(thresholds.getCategorizedThresholds() != null
                                ? new HashMap<>(thresholds.getCategorizedThresholds())
                                : null)
                        .customThresholds(new ArrayList<>())
                        .build())
        .build();
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TimeSeriesMLTransactionThresholdsDTO {
    @NotEmpty private String serviceId;

    @NotEmpty private String workflowId;

    @NotEmpty private StateType stateType;

    @NotEmpty private String groupName;

    @NotEmpty private String transactionName;

    @NotEmpty private String metricName;

    @NotEmpty private String cvConfigId;

    private String accountId;

    TimeSeriesMetricDefinition thresholds;

    TimeSeriesCustomThresholdType thresholdType = TimeSeriesCustomThresholdType.ACCEPTABLE;

    public TimeSeriesMLTransactionThresholds toEntity(String customThresholdRefId) {
      return TimeSeriesMLTransactionThresholds.builder()
          .serviceId(this.getServiceId())
          .workflowId(this.getWorkflowId())
          .stateType(this.getStateType())
          .groupName(this.getGroupName())
          .transactionName(this.getTransactionName())
          .metricName(this.getMetricName())
          .cvConfigId(this.getCvConfigId())
          .accountId(this.getAccountId())
          .thresholds(this.getThresholds())
          .thresholdType(this.getThresholdType())
          .customThresholdRefId(customThresholdRefId)
          .build();
    }

    public boolean compareTo(TimeSeriesMLTransactionThresholds entity) {
      if ((serviceId != null && !serviceId.equals(entity.getServiceId()))
          || (entity.getServiceId() != null && serviceId == null)) {
        return false;
      }

      if (workflowId != null && !workflowId.equals(entity.getWorkflowId())
          || (entity.getWorkflowId() != null && workflowId == null)) {
        return false;
      }

      if (groupName != null && !groupName.equals(entity.getGroupName())
          || (entity.getGroupName() != null && groupName == null)) {
        return false;
      }

      if (!stateType.name().equals(entity.getStateType().name())) {
        return false;
      }

      if (transactionName != null && !transactionName.equals(entity.getTransactionName())
          || (entity.getTransactionName() != null && transactionName == null)) {
        return false;
      }

      if (metricName != null && !metricName.equals(entity.getMetricName())
          || (entity.getMetricName() != null && metricName == null)) {
        return false;
      }

      if (cvConfigId != null && !cvConfigId.equals(entity.getCvConfigId())
          || (entity.getCvConfigId() != null && cvConfigId == null)) {
        return false;
      }

      if (accountId != null && !accountId.equals(entity.getAccountId())
          || (entity.getAccountId() != null && accountId == null)) {
        return false;
      }

      if (thresholds != null && !thresholds.equals(entity.getThresholds())) {
        return false;
      }

      if (thresholdType != null && !thresholdType.name().equals(entity.getThresholdType().name())) {
        return false;
      }

      return true;
    }

    public static TimeSeriesMLTransactionThresholdsDTO fromTransactionThresholdsEntity(
        TimeSeriesMLTransactionThresholds threshold) {
      return TimeSeriesMLTransactionThresholdsDTO.builder()
          .serviceId(threshold.getServiceId())
          .workflowId(threshold.getWorkflowId())
          .stateType(threshold.getStateType())
          .groupName(threshold.getGroupName())
          .transactionName(threshold.getTransactionName())
          .metricName(threshold.getMetricName())
          .cvConfigId(threshold.getCvConfigId())
          .accountId(threshold.getAccountId())
          .thresholds(threshold.getThresholds())
          .thresholdType(threshold.getThresholdType())
          .build();
    }
  }
}
