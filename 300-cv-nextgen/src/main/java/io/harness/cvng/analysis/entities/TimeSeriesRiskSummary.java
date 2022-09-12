/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.entities;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.cvng.CVConstants;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "TimeSeriesRiskSummaryKeys")
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.CVNG)
@Entity(value = "timeseriesRiskSummary", noClassnameStored = true)
@HarnessEntity(exportable = false)
public final class TimeSeriesRiskSummary implements PersistentEntity, UuidAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("query_idx")
                 .field(TimeSeriesRiskSummaryKeys.analysisEndTime)
                 .field(TimeSeriesRiskSummaryKeys.verificationTaskId)
                 .build())
        .build();
  }

  @Id private String uuid;
  @FdIndex private String verificationTaskId;
  @NotEmpty private Instant analysisStartTime;
  @NotEmpty private Instant analysisEndTime;
  private List<TransactionMetricRisk> transactionMetricRiskList;
  private double overallRisk;
  @Builder.Default
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plus(CVConstants.MAX_DATA_RETENTION_DURATION).toInstant());
  public List<TransactionMetricRisk> getTransactionMetricRiskList() {
    if (transactionMetricRiskList == null) {
      return new ArrayList<>();
    }
    return transactionMetricRiskList;
  }

  @Data
  @Builder
  @FieldNameConstants(innerTypeName = "TransactionMetricRiskKeys")
  public static class TransactionMetricRisk {
    private String transactionName;
    @Deprecated private String metricName;
    private String metricIdentifier;
    private Integer metricRisk;
    private double metricScore;
    private boolean longTermPattern;
    private long lastSeenTime;
    public Risk getMetricRisk() {
      return Risk.valueOf(metricRisk);
    }
    public boolean isAnomalous() {
      return getMetricRisk().isGreaterThanEq(Risk.OBSERVE);
    }

    public String getMetricIdentifier() {
      if (isEmpty(metricIdentifier)) {
        String identifier = metricName.replaceAll(" ", "_").toLowerCase();
        identifier = identifier.replaceAll("\\(", "");
        identifier = identifier.replaceAll("\\)", "");
        return identifier;
      }
      return metricIdentifier;
    }
  }
}
