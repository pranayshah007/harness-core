/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.entities.instancesyncperpetualtaskinfo;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;

import dev.morphia.annotations.Entity;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "InstanceSyncPerpetualTaskInfoKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "instanceSyncPerpetualTasksInfoNG", noClassnameStored = true)
@Document("instanceSyncPerpetualTasksInfoNG")
@Persistent
@OwnedBy(HarnessTeam.DX)
public class InstanceSyncPerpetualTaskInfo {
  @Id @dev.morphia.annotations.Id String id;
  String accountIdentifier;
  @FdUniqueIndex String infrastructureMappingId;
  List<DeploymentInfoDetails> deploymentInfoDetailsList;
  @FdUniqueIndex String perpetualTaskId;
  String connectorIdentifier;
  @CreatedDate long createdAt;
  @LastModifiedDate long lastUpdatedAt;
}
