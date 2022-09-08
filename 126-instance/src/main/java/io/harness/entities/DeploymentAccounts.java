/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "DeploymentAccountsKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "deploymentAccounts", noClassnameStored = true)
@Document("deploymentAccounts")
@HarnessEntity(exportable = false)
@Persistent
@OwnedBy(HarnessTeam.DX)
public class DeploymentAccounts implements PersistentEntity, PersistentRegularIterable {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_id")
                 .unique(true)
                 .field(DeploymentAccountsKeys.accountIdentifier)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id private String id;
  private String accountIdentifier;
  @FdIndex private Long instanceStatsMetricsPublisherIteration;

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (DeploymentAccountsKeys.instanceStatsMetricsPublisherIteration.equals(fieldName)) {
      this.instanceStatsMetricsPublisherIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (DeploymentAccountsKeys.instanceStatsMetricsPublisherIteration.equals(fieldName)) {
      return this.instanceStatsMetricsPublisherIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public String getUuid() {
    return id;
  }
}
