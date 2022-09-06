/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.DeviationType;
import io.harness.cvng.beans.ThresholdConfigType;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.beans.TimeSeriesThresholdActionType;
import io.harness.cvng.beans.TimeSeriesThresholdCriteria;
import io.harness.cvng.beans.TimeSeriesThresholdDTO;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@FieldNameConstants(innerTypeName = "TimeSeriesThresholdKeys")
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@StoreIn(DbAliases.CVNG)
@Entity(value = "timeSeriesThresholds", noClassnameStored = true)
@HarnessEntity(exportable = true)
public final class TimeSeriesThreshold
    implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("query_idx")
                 .field(TimeSeriesThresholdKeys.accountId)
                 .field(TimeSeriesThresholdKeys.projectIdentifier)
                 .build())
        .build();
  }

  @Id private String uuid;
  private long createdAt;
  private long lastUpdatedAt;
  @NotNull private String accountId;
  @NotNull private String orgIdentifier;
  @NotNull private String projectIdentifier;
  @NotNull private DataSourceType dataSourceType;
  @NotNull private String metricPackIdentifier;
  @NotNull private String metricName;
  @NotNull private String metricIdentifier;
  @NotNull private TimeSeriesMetricType metricType;
  @Default private String metricGroupName = "*";
  @NotNull private TimeSeriesThresholdActionType action;
  @NotNull private TimeSeriesThresholdCriteria criteria;
  private ThresholdConfigType thresholdConfigType;
  private DeviationType deviationType;

  public ThresholdConfigType getThresholdConfigType() {
    if (Objects.isNull(thresholdConfigType)) {
      return ThresholdConfigType.DEFAULT;
    }
    return thresholdConfigType;
  }

  public TimeSeriesThresholdDTO toDTO() {
    return TimeSeriesThresholdDTO.builder()
        .accountId(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .dataSourceType(dataSourceType)
        .metricPackIdentifier(metricPackIdentifier)
        .metricName(metricName)
        .metricType(metricType)
        .metricGroupName(metricGroupName)
        .action(action)
        .criteria(criteria)
        .build();
  }
}
