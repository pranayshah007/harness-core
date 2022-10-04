/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities.demo;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.CVConstants;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@FieldNameConstants(innerTypeName = "cvngDemoDataIndexKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.CVNG)
@Entity(value = "cvngDemoDataIndices")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CV)
public class CVNGDemoDataIndex implements AccountAccess, PersistentEntity, UuidAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_verificationTaskId_dataColWorkerId_idx")
                 .unique(true)
                 .field(cvngDemoDataIndexKeys.accountId)
                 .field(cvngDemoDataIndexKeys.verificationTaskId)
                 .field(cvngDemoDataIndexKeys.dataCollectionWorkerId)
                 .build())
        .build();
  }
  @Id private String uuid;
  String accountId;
  String verificationTaskId;
  String dataCollectionWorkerId;
  int lastIndex;
  @JsonIgnore
  @SchemaIgnore
  @Builder.Default
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plus(CVConstants.MAX_DATA_RETENTION_DURATION).toInstant());
}
