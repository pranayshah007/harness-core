/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.batch;

import io.harness.annotations.StoreIn;
import io.harness.ccm.HarnessServiceInfoNG;
import io.harness.ccm.commons.beans.Container;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.ccm.commons.beans.InstanceState;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.beans.StorageResource;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@StoreIn(DbAliases.CENG)
@Entity(value = "instanceData", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "InstanceDataKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
public final class InstanceData implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  private static final String SUB_FIELD_FORMAT = "%s.%s";
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_usageStartTime_usageStopTime")
                 .field(InstanceDataKeys.accountId)
                 .field(InstanceDataKeys.usageStartTime)
                 .field(InstanceDataKeys.usageStopTime)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_instancetype_usageStartTime_usageStopTime")
                 .field(InstanceDataKeys.accountId)
                 .field(InstanceDataKeys.instanceType)
                 .field(InstanceDataKeys.usageStartTime)
                 .field(InstanceDataKeys.usageStopTime)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_clusterId_instanceState_usageStartTime")
                 .field(InstanceDataKeys.accountId)
                 .field(InstanceDataKeys.clusterId)
                 .field(InstanceDataKeys.instanceState)
                 .field(InstanceDataKeys.usageStartTime)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_clusterId_instanceName_usageStartTime")
                 .field(InstanceDataKeys.accountId)
                 .field(InstanceDataKeys.clusterId)
                 .field(InstanceDataKeys.instanceName)
                 .descSortField(InstanceDataKeys.usageStartTime)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_clusterId_instanceId_instanceState")
                 .field(InstanceDataKeys.accountId)
                 .field(InstanceDataKeys.clusterId)
                 .field(InstanceDataKeys.instanceId)
                 .field(InstanceDataKeys.instanceState)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_clusterId_instanceType_nodePoolName_instanceState_instanceFamily")
                 .field(InstanceDataKeys.accountId)
                 .field(InstanceDataKeys.clusterId)
                 .field(InstanceDataKeys.instanceType)
                 .field(String.format(
                     SUB_FIELD_FORMAT, InstanceDataKeys.metaData, InstanceMetaDataConstants.NODE_POOL_NAME))
                 .field(InstanceDataKeys.instanceState)
                 .field(String.format(
                     SUB_FIELD_FORMAT, InstanceDataKeys.metaData, InstanceMetaDataConstants.INSTANCE_FAMILY))
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_activeInstanceIterator_usageStartTime_instanceType")
                 .field(InstanceDataKeys.accountId)
                 .field(InstanceDataKeys.activeInstanceIterator)
                 .field(InstanceDataKeys.usageStartTime)
                 .field(InstanceDataKeys.instanceType)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_instanceId")
                 .field(InstanceDataKeys.accountId)
                 .field(InstanceDataKeys.instanceId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_clusterId_activeInstanceIterator_instanceType_usageStartTime")
                 .field(InstanceDataKeys.accountId)
                 .field(InstanceDataKeys.clusterId)
                 .field(InstanceDataKeys.activeInstanceIterator)
                 .field(InstanceDataKeys.instanceType)
                 .field(InstanceDataKeys.usageStartTime)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_clusterId_instanceState_usageStartTime_instanceId_usageStopTime")
                 .field(InstanceDataKeys.accountId)
                 .field(InstanceDataKeys.clusterId)
                 .field(InstanceDataKeys.instanceState)
                 .field(InstanceDataKeys.usageStartTime)
                 .field(InstanceDataKeys.instanceId)
                 .field(InstanceDataKeys.usageStopTime)
                 .build())
        .build();
  }

  @Id String uuid;
  String accountId;
  String settingId;
  @FdIndex String instanceId;
  String instanceName;
  String clusterName;
  String clusterId;
  String cloudProviderInstanceId;
  InstanceType instanceType;
  Resource totalResource;
  Resource limitResource;
  Resource allocatableResource;
  Resource pricingResource;
  StorageResource storageResource;
  List<String> pvcClaimNames;
  List<Container> containerList;
  Map<String, String> labels;
  Map<String, String> namespaceLabels;
  Map<String, String> metaData;
  Map<String, String> topOwnerLabels;
  Map<String, String> metadataAnnotations;
  Instant usageStartTime;
  Instant usageStopTime;
  Instant activeInstanceIterator;
  InstanceState instanceState;

  long createdAt;
  long lastUpdatedAt;

  HarnessServiceInfo harnessServiceInfo;
  HarnessServiceInfoNG harnessServiceInfoNG;

  @FdTtlIndex private Date ttl;
}
