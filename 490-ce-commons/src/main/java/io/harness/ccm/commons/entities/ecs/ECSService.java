/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.ecs;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.Resource;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.amazonaws.services.ecs.model.LaunchType;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.CENG)
@Entity(value = "ecsService", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "ECSServiceKeys")
@OwnedBy(CE)
public final class ECSService implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_clusterId_serviceArn")
                 .unique(true)
                 .field(ECSServiceKeys.accountId)
                 .field(ECSServiceKeys.clusterId)
                 .field(ECSServiceKeys.serviceArn)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_serviceArn")
                 .field(ECSServiceKeys.accountId)
                 .field(ECSServiceKeys.serviceArn)
                 .build())
        .build();
  }
  @Id String uuid;
  String accountId;
  String awsAccountId;
  String clusterId;
  String serviceArn;
  String serviceName;
  LaunchType launchType;
  Resource resource;
  Map<String, String> labels;
  long createdAt;
  long lastUpdatedAt;
}
