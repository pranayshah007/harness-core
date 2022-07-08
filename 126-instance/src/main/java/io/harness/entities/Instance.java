/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.instanceinfo.InstanceInfo;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.environment.beans.EnvironmentType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "InstanceKeys")
@Entity(value = "instanceNG", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("instanceNG")
@StoreIn(DbAliases.NG_MANAGER)
@OwnedBy(HarnessTeam.DX)
public class Instance {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .unique(true)
                 .name("unique_instanceKey_infrastructureMappingId_idx")
                 .field(InstanceKeys.instanceKey)
                 .field(InstanceKeys.infrastructureMappingId)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_organizationId_projectId_isDeleted_createdAt_deletedAt_idx")
                 .field(InstanceKeys.accountIdentifier)
                 .field(InstanceKeys.orgIdentifier)
                 .field(InstanceKeys.projectIdentifier)
                 .field(InstanceKeys.isDeleted)
                 .sortField(InstanceKeys.createdAt)
                 .sortField(InstanceKeys.deletedAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_isDeleted_createdAt_deletedAt_idx")
                 .field(InstanceKeys.accountIdentifier)
                 .field(InstanceKeys.isDeleted)
                 .sortField(InstanceKeys.createdAt)
                 .sortField(InstanceKeys.deletedAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_organizationId_projectId_serviceId_isDeleted_createdAt_deletedAt_idx")
                 .field(InstanceKeys.accountIdentifier)
                 .field(InstanceKeys.orgIdentifier)
                 .field(InstanceKeys.projectIdentifier)
                 .field(InstanceKeys.serviceIdentifier)
                 .field(InstanceKeys.isDeleted)
                 .sortField(InstanceKeys.createdAt)
                 .sortField(InstanceKeys.deletedAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_organizationId_projectId_infrastructureMappingId_isDeleted_createdAt_deletedAt_idx")
                 .field(InstanceKeys.accountIdentifier)
                 .field(InstanceKeys.orgIdentifier)
                 .field(InstanceKeys.projectIdentifier)
                 .field(InstanceKeys.infrastructureMappingId)
                 .field(InstanceKeys.isDeleted)
                 .sortField(InstanceKeys.createdAt)
                 .sortField(InstanceKeys.deletedAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_organizationId_projectId_serviceId_envId_isDeleted_createdAt_deletedAt_idx")
                 .field(InstanceKeys.accountIdentifier)
                 .field(InstanceKeys.orgIdentifier)
                 .field(InstanceKeys.projectIdentifier)
                 .field(InstanceKeys.serviceIdentifier)
                 .field(InstanceKeys.envIdentifier)
                 .field(InstanceKeys.isDeleted)
                 .sortField(InstanceKeys.createdAt)
                 .sortField(InstanceKeys.deletedAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_instanceInfo.Namespace_instanceInfoPodName_createdAt_idx")
                 .field(InstanceKeys.accountIdentifier)
                 .field(InstanceKeysAdditional.instanceInfoNamespace)
                 .field(InstanceKeysAdditional.instanceInfoPodName)
                 .sortField(InstanceKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_lastDeployedAt_idx")
                 .field(InstanceKeys.accountIdentifier)
                 .field(InstanceKeys.lastDeployedAt)
                 .sortField(InstanceKeys.lastDeployedAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_organizationId_projectId_lastDeployedAt_idx")
                 .field(InstanceKeys.accountIdentifier)
                 .field(InstanceKeys.orgIdentifier)
                 .field(InstanceKeys.projectIdentifier)
                 .field(InstanceKeys.lastDeployedAt)
                 .sortField(InstanceKeys.lastDeployedAt)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id private String id;
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private String instanceKey;
  @NotEmpty private InstanceType instanceType;

  private String envIdentifier;
  private String envName;
  private EnvironmentType envType;

  private String serviceIdentifier;
  private String serviceName;

  private String infrastructureMappingId;
  private String infrastructureKind;
  private String connectorRef;

  private String infraIdentifier;
  private String infraName;

  private ArtifactDetails primaryArtifact;

  private String lastDeployedById;
  private String lastDeployedByName;
  private long lastDeployedAt;

  private String lastPipelineExecutionId;
  private String lastPipelineExecutionName;

  private InstanceInfo instanceInfo;

  private boolean isDeleted;
  private long deletedAt;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;

  @UtilityClass
  public static class InstanceKeysAdditional {
    public static final String instanceInfoPodName = "instanceInfo.podName";
    public static final String instanceInfoNamespace = "instanceInfo.namespace";
  }
}
