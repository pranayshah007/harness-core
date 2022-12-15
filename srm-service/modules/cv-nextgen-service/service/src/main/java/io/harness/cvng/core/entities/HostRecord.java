/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.cvng.CVConstants;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@EqualsAndHashCode()
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "HostRecordKeys")
@StoreIn(DbAliases.CVNG)
@Entity(value = "hostRecords", noClassnameStored = true)
@HarnessEntity(exportable = false)
public final class HostRecord implements PersistentEntity, UuidAware, CreatedAtAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("insert_idx")
                 .field(HostRecordKeys.verificationTaskId)
                 .field(HostRecordKeys.startTime)
                 .field(HostRecordKeys.endTime)
                 .build())
        .build();
  }

  @Id private String uuid;
  @FdIndex private String accountId;
  private String verificationTaskId;
  @NotEmpty private Instant startTime;
  private Instant endTime;
  private Set<String> hosts;
  private long createdAt;
  @JsonIgnore
  @SchemaIgnore
  @Builder.Default
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plus(CVConstants.MAX_DATA_RETENTION_DURATION).toInstant());

  public Set<String> getHosts() {
    if (isNotEmpty(hosts)) {
      return hosts;
    } else {
      return new HashSet<>();
    }
  }
}
