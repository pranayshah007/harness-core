/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.cvng.CVConstants;
import io.harness.cvng.analysis.entities.VerificationTaskBase;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.jetbrains.annotations.NotNull;

@Data
@Builder
@EqualsAndHashCode(callSuper = false, exclude = {"validUntil", "timeSeriesGroupValues"})
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "TimeSeriesRecordKeys")
@StoreIn(DbAliases.CVNG)
@Entity(value = "timeSeriesRecords", noClassnameStored = true)
@HarnessEntity(exportable = false)
public final class TimeSeriesRecord
    extends VerificationTaskBase implements AccountAccess, PersistentEntity, Comparable<TimeSeriesRecord>, UuidAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("query_idx")
                 .field(TimeSeriesRecordKeys.verificationTaskId)
                 .field(TimeSeriesRecordKeys.bucketStartTime)
                 .field(TimeSeriesRecordKeys.metricName)
                 .build())
        .build();
  }

  @Id private String uuid;
  @FdIndex private String accountId;
  @FdIndex private String verificationTaskId;
  @FdIndex private String host;
  @FdIndex private String metricName;
  @FdIndex private String metricIdentifier;
  private double riskScore;
  private Instant bucketStartTime;
  private TimeSeriesMetricType metricType;

  @Default private Set<TimeSeriesGroupValue> timeSeriesGroupValues = new HashSet<>();

  @JsonIgnore
  @SchemaIgnore
  @Default
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plus(CVConstants.MAX_DATA_RETENTION_DURATION).toInstant());

  @Override
  public int compareTo(@NotNull TimeSeriesRecord o) {
    if (!bucketStartTime.equals(o.bucketStartTime)) {
      return bucketStartTime.compareTo(o.bucketStartTime);
    }
    return metricName.compareTo(o.metricName);
  }

  @Data
  @Builder
  @FieldNameConstants(innerTypeName = "TimeSeriesValueKeys")
  @EqualsAndHashCode(of = {"groupName", "timeStamp"})
  public static class TimeSeriesGroupValue implements Comparable<TimeSeriesGroupValue> {
    private String groupName;
    private Instant timeStamp;
    private double metricValue;
    @Builder.Default private double riskScore = -1;
    private Double percentValue;

    @Override
    public int compareTo(TimeSeriesGroupValue other) {
      return timeStamp.compareTo(other.getTimeStamp());
    }
  }
}
