/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.entities;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "IDPTelemetrySentStatusKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "idpTelemetrySentStatus", noClassnameStored = true)
@Document("idpTelemetrySentStatus")
@TypeAlias("idpTelemetrySentStatus")
@RecasterAlias("io.harness.idp.license.usage.entities.IDPTelemetrySentStatus")
@HarnessEntity(exportable = false)
@OwnedBy(IDP)
public class IDPTelemetrySentStatus implements UuidAware, PersistentEntity {
  @Id String uuid;
  String accountId;
  long lastSent;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("no_dup")
                 .unique(true)
                 .field(IDPTelemetrySentStatusKeys.accountId)
                 .build())
        .build();
  }
}
