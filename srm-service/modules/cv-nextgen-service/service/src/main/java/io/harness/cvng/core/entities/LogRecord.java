/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.cvng.core.utils.DateTimeUtils.instantToEpochMinute;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.cvng.analysis.beans.LogClusterDTO;
import io.harness.cvng.analysis.entities.VerificationTaskBase;
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
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@Builder
@EqualsAndHashCode()
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "LogRecordKeys")
@StoreIn(DbAliases.CVNG)
@Entity(value = "logRecords", noClassnameStored = true)
@HarnessEntity(exportable = false)
public final class LogRecord extends VerificationTaskBase implements PersistentEntity, UuidAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("query_idx")
                 .field(LogRecordKeys.verificationTaskId)
                 .field(LogRecordKeys.timestamp)
                 .build())
        .build();
  }

  @Id private String uuid;
  @FdIndex private String accountId;
  @FdIndex private String verificationTaskId;
  @NotEmpty private Instant timestamp;
  private String host;
  @JsonIgnore
  @SchemaIgnore
  @Builder.Default
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(1).toInstant());
  private String log;

  public LogClusterDTO toLogClusterDTO() {
    return LogClusterDTO.builder()
        .host(this.getHost())
        .epochMinute(instantToEpochMinute(this.getTimestamp()))
        .log(this.getLog())
        .build();
  }
}
