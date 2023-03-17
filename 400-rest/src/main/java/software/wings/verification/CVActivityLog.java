/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.verification;

import static software.wings.beans.LogColor.Red;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogHelper.doneColoring;
import static software.wings.beans.LogWeight.Bold;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import software.wings.beans.dto.CVActivityLog.LogLevel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "CVActivityLogKeys")
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "cvActivityLogs", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class CVActivityLog implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  private static final long ACTIVITY_LOG_TTL_WEEKS = 2;
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("service_guard_idx")
                 .field(CVActivityLogKeys.cvConfigId)
                 .descSortField(CVActivityLogKeys.dataCollectionMinute)
                 .ascSortField(CVActivityLogKeys.createdAt)
                 .build())
        .build();
  }

  @Id private String uuid;
  private String cvConfigId;
  @FdIndex private String stateExecutionId;
  @JsonProperty(value = "timestamp") private long createdAt;
  private long lastUpdatedAt;
  @FdIndex private long dataCollectionMinute;
  @NonNull private String log;
  @NonNull private LogLevel logLevel;
  private List<Long> timestampParams;
  @FdIndex private String accountId;

  @Default
  @JsonIgnore
  @SchemaIgnore
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusWeeks(ACTIVITY_LOG_TTL_WEEKS).toInstant());

  @Override
  @JsonIgnore
  public long getLastUpdatedAt() {
    return lastUpdatedAt;
  }

  public List<Long> getTimestampParams() {
    if (timestampParams == null) {
      return Collections.emptyList();
    }
    return timestampParams;
  }

  public String getAnsiLog() {
    String ansiLog;
    if (logLevel == LogLevel.ERROR) {
      ansiLog = color(log, Red, Bold);
    } else if (logLevel == LogLevel.WARN) {
      ansiLog = color(log, Yellow, Bold);
    } else {
      ansiLog = log;
    }

    return doneColoring(ansiLog);
  }
}
