/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.newrelic;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.common.VerificationConstants.ML_RECORDS_TTL_MONTHS;
import static software.wings.service.impl.newrelic.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.SortOrder;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;

import software.wings.beans.Base;
import software.wings.metrics.RiskLevel;
import software.wings.service.impl.analysis.TimeSeriesMlAnalysisType;
import software.wings.sm.StateType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Transient;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "NewRelicMetricAnalysisRecordKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "newRelicMetricAnalysisRecords", noClassnameStored = true)
@HarnessEntity(exportable = false)
@OwnedBy(HarnessTeam.CV)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class NewRelicMetricAnalysisRecord
    extends Base implements Comparable<NewRelicMetricAnalysisRecord>, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_analysis")
                 .unique(true)
                 .field(NewRelicMetricAnalysisRecordKeys.workflowExecutionId)
                 .field(NewRelicMetricAnalysisRecordKeys.stateExecutionId)
                 .field(NewRelicMetricAnalysisRecordKeys.groupName)
                 .field(NewRelicMetricAnalysisRecordKeys.analysisMinute)
                 .build())
        .build();
  }

  @NotEmpty private StateType stateType;

  @NotEmpty private String message;

  @NotEmpty private RiskLevel riskLevel;

  @NotEmpty private String workflowExecutionId;

  @NotEmpty @FdIndex private String stateExecutionId;

  @FdIndex private String accountId;

  private String cvConfigId;

  private List<NewRelicMetricAnalysis> metricAnalyses;

  private int analysisMinute;

  private boolean showTimeSeries;

  private String baseLineExecutionId;

  private String groupName = DEFAULT_GROUP_NAME;

  private String dependencyPath;

  private TimeSeriesMlAnalysisType mlAnalysisType;

  @Transient private int progress;

  @JsonIgnore
  @SchemaIgnore
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(ML_RECORDS_TTL_MONTHS).toInstant());

  @Builder
  public NewRelicMetricAnalysisRecord(String uuid, String appId, EmbeddedUser createdBy, long createdAt,
      EmbeddedUser lastUpdatedBy, long lastUpdatedAt, String entityYamlPath, StateType stateType, String message,
      RiskLevel riskLevel, String workflowExecutionId, String stateExecutionId, String cvConfigId, String groupName,
      String dependencyPath, TimeSeriesMlAnalysisType mlAnalysisType, List<NewRelicMetricAnalysis> metricAnalyses,
      int analysisMinute, boolean showTimeSeries, String baseLineExecutionId, String accountId) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath);
    this.stateType = stateType;
    this.message = message;
    this.riskLevel = riskLevel;
    this.workflowExecutionId = workflowExecutionId;
    this.stateExecutionId = stateExecutionId;
    this.cvConfigId = cvConfigId;
    this.groupName = groupName;
    this.dependencyPath = dependencyPath;
    this.mlAnalysisType = mlAnalysisType;
    this.metricAnalyses = metricAnalyses;
    this.analysisMinute = analysisMinute;
    this.showTimeSeries = showTimeSeries;
    this.baseLineExecutionId = baseLineExecutionId;
    this.groupName = groupName;
    this.accountId = accountId;
    this.validUntil = Date.from(OffsetDateTime.now().plusMonths(ML_RECORDS_TTL_MONTHS).toInstant());
  }

  public void addNewRelicMetricAnalysis(NewRelicMetricAnalysis analysis) {
    metricAnalyses.add(analysis);
  }

  @Override
  public int compareTo(NewRelicMetricAnalysisRecord o) {
    if (this.mlAnalysisType != null) {
      int analysisTypeComparison = this.mlAnalysisType.compareTo(o.mlAnalysisType);
      if (analysisTypeComparison != 0) {
        return analysisTypeComparison;
      }
    }

    return StringUtils.compare(this.groupName, o.groupName);
  }

  @Data
  @Builder
  public static class NewRelicMetricAnalysis implements Comparable<NewRelicMetricAnalysis> {
    private String metricName;
    private RiskLevel riskLevel;
    private List<NewRelicMetricAnalysisValue> metricValues;
    @Transient private String displayName;
    @Transient private String fullMetricName;
    private String tag;

    public String getTag() {
      return isEmpty(tag) ? "DEFAULT" : tag;
    }

    public void addNewRelicMetricAnalysisValue(NewRelicMetricAnalysisValue metricAnalysisValue) {
      if (metricAnalysisValue.getTestValue() >= 0.0 || metricAnalysisValue.getControlValue() >= 0.0) {
        metricValues.add(metricAnalysisValue);
      }
    }

    @Override
    public int compareTo(NewRelicMetricAnalysis other) {
      int riskDiff = this.riskLevel.compareTo(other.riskLevel);

      if (riskDiff != 0) {
        return riskDiff;
      }
      if (isEmpty(metricValues) && isEmpty(other.metricValues)) {
        return metricName.compareTo(other.metricName);
      }

      if (isNotEmpty(metricValues) && isEmpty(other.metricValues)) {
        return -1;
      }

      if (isEmpty(metricValues) && isNotEmpty(other.metricValues)) {
        return 1;
      }

      for (SortOrder sortOrder : NewRelicMetricValueDefinition.SORTING_METRIC_NAME.values()) {
        NewRelicMetricAnalysisValue thisAnalysisValue =
            metricValues.stream()
                .filter(newRelicMetricAnalysisValue
                    -> newRelicMetricAnalysisValue.getName().equals(sortOrder.getFieldName()))
                .findFirst()
                .orElse(null);
        NewRelicMetricAnalysisValue otherAnalysisValue =
            other.metricValues.stream()
                .filter(newRelicMetricAnalysisValue
                    -> newRelicMetricAnalysisValue.getName().equals(sortOrder.getFieldName()))
                .findFirst()
                .orElse(null);
        if (thisAnalysisValue == null && otherAnalysisValue == null) {
          continue;
        }

        if (thisAnalysisValue == null) {
          thisAnalysisValue = NewRelicMetricAnalysisValue.builder().testValue(-1).build();
        }

        if (otherAnalysisValue == null) {
          otherAnalysisValue = NewRelicMetricAnalysisValue.builder().testValue(-1).build();
        }
        switch (sortOrder.getOrderType()) {
          case ASC:
            return Double.compare(thisAnalysisValue.getTestValue(), otherAnalysisValue.getTestValue());
          case DESC:
            return Double.compare(otherAnalysisValue.getTestValue(), thisAnalysisValue.getTestValue());
          default:
            throw new IllegalArgumentException("Invalid sort order " + sortOrder.getOrderType());
        }
      }

      return metricName.compareTo(other.metricName);
    }
  }
}
