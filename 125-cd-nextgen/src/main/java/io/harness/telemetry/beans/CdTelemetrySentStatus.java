/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.RecasterAlias;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@OwnedBy(CDP)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "CdTelemetrySentStatusKeys")
@Entity(value = "cdTelemetrySentStatus", noClassnameStored = true)
@StoreIn(DbAliases.NG_MANAGER)
@Document("cdTelemetrySentStatus")
@TypeAlias("cdTelemetrySentStatus")
@RecasterAlias("io.harness.telemetry.beans.CdTelemetrySentStatus")
@HarnessEntity(exportable = false)
public class CdTelemetrySentStatus implements UuidAware, PersistentEntity {
  @Id @org.mongodb.morphia.annotations.Id private String uuid;
  private String accountId;
  private long lastSent; // timestamp
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("no_dup_cd")
                 .unique(true)
                 .field(CdTelemetrySentStatusKeys.accountId)
                 .build())
        .build();
  }
}
