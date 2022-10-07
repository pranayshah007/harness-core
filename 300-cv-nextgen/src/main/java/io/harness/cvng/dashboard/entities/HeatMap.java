/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.core.beans.monitoredService.DurationDTO;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@EqualsAndHashCode(callSuper = false, exclude = {"validUntil"})
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "HeatMapKeys")
@StoreIn(DbAliases.CVNG)
@Entity(value = "heatMaps", noClassnameStored = true)
@HarnessEntity(exportable = false)
public final class HeatMap implements UuidAware, CreatedAtAware, AccountAccess, PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("insert_idxv2")
                 .field(HeatMapKeys.accountId)
                 .field(HeatMapKeys.orgIdentifier)
                 .field(HeatMapKeys.projectIdentifier)
                 .field(HeatMapKeys.monitoredServiceIdentifier)
                 .field(HeatMapKeys.category)
                 .field(HeatMapKeys.heatMapResolution)
                 .field(HeatMapKeys.heatMapBucketStartTime)
                 .field(HeatMapKeys.heatMapBucketEndTime)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("query_idx")
                 .field(HeatMapKeys.accountId)
                 .field(HeatMapKeys.orgIdentifier)
                 .field(HeatMapKeys.projectIdentifier)
                 .field(HeatMapKeys.heatMapResolution)
                 .field(HeatMapKeys.heatMapBucketEndTime)
                 .build())
        .build();
  }

  @Id private String uuid;
  private String monitoredServiceIdentifier;
  private String projectIdentifier;
  private String orgIdentifier;
  private CVMonitoringCategory category;
  private HeatMapResolution heatMapResolution;
  private Instant heatMapBucketStartTime;
  private Instant heatMapBucketEndTime;
  private String accountId;
  private List<HeatMapRisk> heatMapRisks;

  @FdIndex private long createdAt;

  @JsonIgnore
  @SchemaIgnore
  @Builder.Default
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusDays(31).toInstant());

  public Set<HeatMapRisk> getHeatMapRisks() {
    Map<HeatMapRisk, Double> risks = new HashMap<>();
    heatMapRisks.forEach(heatMapRisk -> {
      if (!risks.containsKey(heatMapRisk)) {
        risks.put(heatMapRisk, heatMapRisk.riskScore);
      } else if (heatMapRisk.riskScore > risks.get(heatMapRisk)) {
        risks.put(heatMapRisk, heatMapRisk.riskScore);
      }
    });
    return risks.keySet();
  }

  @Data
  @Builder
  @FieldNameConstants(innerTypeName = "HeatMapRiskKeys")
  @EqualsAndHashCode(of = {"startTime", "endTime"})
  public static class HeatMapRisk implements Comparable<HeatMapRisk> {
    private Instant startTime;
    private Instant endTime;
    private double riskScore;

    /**
     * Additional metadata to summarize anomalous data
     */
    private long anomalousMetricsCount;
    private long anomalousLogsCount;

    @Override
    public int compareTo(HeatMapRisk o) {
      return this.startTime.compareTo(o.startTime);
    }
  }

  public enum HeatMapResolution {
    FIVE_MIN(Duration.ofMinutes(5), Duration.ofHours(4)),
    FIFTEEN_MINUTES(Duration.ofMinutes(15), Duration.ofHours(12)),
    THIRTY_MINUTES(Duration.ofMinutes(30), Duration.ofDays(1)),
    ONE_HOUR_THIRTY_MINUTES(Duration.ofMinutes(90), Duration.ofDays(3)),
    THREE_HOURS_THIRTY_MINUTES(Duration.ofMinutes(210), Duration.ofDays(7)),
    FIFTEEN_HOURS(Duration.ofHours(15), Duration.ofDays(30));

    private Duration resolution;
    private Duration bucketSize;
    HeatMapResolution(Duration resolution, Duration bucketSize) {
      this.resolution = resolution;
      this.bucketSize = bucketSize;
    }

    public Duration getResolution() {
      return resolution;
    }

    public Duration getBucketSize() {
      return bucketSize;
    }

    public Instant getNextResolutionEndTime(Instant instant) {
      long timeStamp = instant.toEpochMilli();
      return Instant.ofEpochMilli(timeStamp - (timeStamp % this.resolution.toMillis())).plus(this.resolution);
    }

    public static HeatMapResolution resolutionForDurationDTO(DurationDTO duration) {
      return DurationDTO.FOUR_HOURS.equals(duration) ? HeatMapResolution.FIVE_MIN : HeatMapResolution.THIRTY_MINUTES;
    }

    public static HeatMapResolution getHeatMapResolution(Instant startTime, Instant endTime) {
      for (HeatMapResolution heatMapResolution : HeatMapResolution.values()) {
        if (endTime.toEpochMilli() - startTime.toEpochMilli() <= heatMapResolution.getBucketSize().toMillis()) {
          return heatMapResolution;
        }
      }
      return HeatMapResolution.FIFTEEN_HOURS;
    }
  }
}
