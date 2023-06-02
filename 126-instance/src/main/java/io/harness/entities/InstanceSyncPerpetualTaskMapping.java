/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "InstanceSyncPerpetualTaskMappingKey")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "instanceSyncPerpetualTaskMapping", noClassnameStored = true)
@Document("instanceSyncPerpetualTaskMapping")
@Persistent
@OwnedBy(HarnessTeam.CDP)
public class InstanceSyncPerpetualTaskMapping {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .unique(true)
                 .name("unique_connectorIdentifier_accountId_orgId_projectId_perpetualTaskId_idx")
                 .field(InstanceSyncPerpetualTaskMappingKey.accountId)
                 .field(InstanceSyncPerpetualTaskMappingKey.orgId)
                 .field(InstanceSyncPerpetualTaskMappingKey.projectId)
                 .field(InstanceSyncPerpetualTaskMappingKey.connectorIdentifier)
                 .field(InstanceSyncPerpetualTaskMappingKey.perpetualTaskId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("connectorIdentifier_accountId_orgId_projectId_idx")
                 .field(InstanceSyncPerpetualTaskMappingKey.accountId)
                 .field(InstanceSyncPerpetualTaskMappingKey.orgId)
                 .field(InstanceSyncPerpetualTaskMappingKey.projectId)
                 .field(InstanceSyncPerpetualTaskMappingKey.connectorIdentifier)
                 .build())
        .build();
  }

  @Id @dev.morphia.annotations.Id String id;
  String accountId;
  String projectId;
  String orgId;
  String connectorIdentifier;
  String perpetualTaskId;
}
