/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.reconciliation.deployment;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@FieldNameConstants(innerTypeName = "DeploymentReconRecordKeys")
@ToString
@StoreIn(DbAliases.HARNESS)
@Entity(value = "deploymentReconciliation", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class DeploymentReconRecord implements PersistentEntity, UuidAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_durationEndTs")
                 .field(DeploymentReconRecordKeys.accountId)
                 .field(DeploymentReconRecordKeys.durationEndTs)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_reconciliationStatus")
                 .field(DeploymentReconRecordKeys.accountId)
                 .field(DeploymentReconRecordKeys.reconciliationStatus)
                 .build())
        .build();
  }

  @Id private String uuid;
  private String accountId;
  private long durationStartTs;
  private long durationEndTs;
  private DetectionStatus detectionStatus;
  private ReconciliationStatus reconciliationStatus;
  private ReconcilationAction reconcilationAction;
  private long reconStartTs;
  private long reconEndTs;
  @Default @FdTtlIndex private Date ttl = Date.from(OffsetDateTime.now().plusMonths(1).toInstant());
}
