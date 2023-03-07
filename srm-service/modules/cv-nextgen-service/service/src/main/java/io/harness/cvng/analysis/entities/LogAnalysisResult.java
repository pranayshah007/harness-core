/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.entities;

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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "LogAnalysisResultKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.CVNG)
@Entity(value = "logAnalysisResults", noClassnameStored = true)
@HarnessEntity(exportable = false)
public final class LogAnalysisResult extends VerificationTaskBase implements PersistentEntity, UuidAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("query_idx")
                 .field(LogAnalysisResultKeys.verificationTaskId)
                 .field(LogAnalysisResultKeys.analysisStartTime)
                 .field(LogAnalysisResultKeys.analysisEndTime)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("verificationTaskId_analysisEndTime_idx")
                 .field(LogAnalysisResultKeys.verificationTaskId)
                 .field(LogAnalysisResultKeys.analysisEndTime)
                 .build())
        .build();
  }

  @Id private String uuid;

  private String verificationTaskId;
  private Instant analysisStartTime;
  private Instant analysisEndTime;
  @FdIndex private String accountId;

  private double overallRisk;
  private List<AnalysisResult> logAnalysisResults;

  public List<AnalysisResult> getLogAnalysisResults() {
    if (logAnalysisResults == null) {
      return new ArrayList<>();
    }
    return logAnalysisResults;
  }
  @JsonIgnore
  @SchemaIgnore
  @Builder.Default
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plus(CVConstants.MAX_DATA_RETENTION_DURATION).toInstant());
  @Data
  @Builder
  @FieldNameConstants(innerTypeName = "AnalysisResultKeys")
  public static class AnalysisResult {
    private long label;
    private LogAnalysisTag tag;
    private int count;
    private double riskScore;
    public Risk getRisk() {
      return Risk.getRiskFromRiskScore(riskScore);
    }

    public static class AnalysisResultBuilder {
      public AnalysisResultBuilder tag(LogAnalysisTag logAnalysisTag) {
        setTag(logAnalysisTag);
        return this;
      }

      public void setTag(LogAnalysisTag tag) {
        this.tag = tag;
        if (tag.equals(LogAnalysisTag.KNOWN)) {
          this.riskScore = 0.0;
        } else if (tag.equals(LogAnalysisTag.UNKNOWN)) {
          this.riskScore = 1.0;
        }
      }
    }
  }

  public static LogAnalysisTag RadarChartTagToLogAnalysisTag(RadarChartTag radarChartTag) {
    return LogAnalysisTag.values()[radarChartTag.ordinal()];
  }

  public static RadarChartTag LogAnalysisTagToRadarChartTag(LogAnalysisTag logAnalysisTag) {
    return RadarChartTag.values()[logAnalysisTag.ordinal()];
  }

  public enum RadarChartTag {
    KNOWN_EVENT(0, "Known"),
    UNEXPECTED_FREQUENCY(1, "Unexpected Frequency"),
    UNKNOWN_EVENT(2, "Unknown");

    private Integer severity;
    private String displayName;

    public String getDisplayName() {
      return displayName;
    }

    RadarChartTag(int severity, String displayName) {
      this.severity = severity;
      this.displayName = displayName;
    }
    public static Set<RadarChartTag> getAnomalousTags() {
      return Sets.newHashSet(UNKNOWN_EVENT, UNEXPECTED_FREQUENCY);
    }
  }

  public enum LogAnalysisTag {
    KNOWN(0),
    UNEXPECTED(1),
    UNKNOWN(2);

    private Integer severity;

    LogAnalysisTag(int severity) {
      this.severity = severity;
    }

    public boolean isMoreSevereThan(LogAnalysisTag other) {
      return this.severity > other.severity;
    }

    public static Set<LogAnalysisTag> getAnomalousTags() {
      return Sets.newHashSet(UNKNOWN, UNEXPECTED);
    }
  }
}
