/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.reconciliation.deployment;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.event.reconciliation.DetectionStatus;
import io.harness.event.reconciliation.ReconcilationAction;
import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;

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
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_entityClass_durationEndTs_sorted")
                 .field(DeploymentReconRecordKeys.accountId)
                 .field(DeploymentReconRecordKeys.entityClass)
                 .descSortField(DeploymentReconRecordKeys.durationEndTs)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_reconciliationStatus_entityClass")
                 .field(DeploymentReconRecordKeys.accountId)
                 .field(DeploymentReconRecordKeys.reconciliationStatus)
                 .field(DeploymentReconRecordKeys.entityClass)
                 .build())
        .build();
  }

  @Id private String uuid;
  private String accountId;
  private String entityClass;
  private long durationStartTs;
  private long durationEndTs;
  private DetectionStatus detectionStatus;
  private ReconciliationStatus reconciliationStatus;
  private ReconcilationAction reconcilationAction;
  private long reconStartTs;
  private long reconEndTs;
  @Default @FdTtlIndex private Date ttl = Date.from(OffsetDateTime.now().plusMonths(1).toInstant());

  public static DBObject getHint(String indexName) {
    Map<String, Object> map = new LinkedHashMap<>();

    switch (indexName) {
      case "accountId_entityClass_durationEndTs_sorted":
        map.put(DeploymentReconRecordKeys.accountId, 1);
        map.put(DeploymentReconRecordKeys.entityClass, 1);
        map.put(DeploymentReconRecordKeys.durationEndTs, -1);
        break;
      default:
        break;
    }

    return new BasicDBObject(map);
  }
}
