/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.infrastructure.instance;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;

import software.wings.beans.Base;
import software.wings.beans.entityinterface.ApplicationAccess;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.key.ContainerInstanceKey;
import software.wings.beans.infrastructure.instance.key.HostInstanceKey;
import software.wings.beans.infrastructure.instance.key.PcfInstanceKey;
import software.wings.beans.infrastructure.instance.key.PodInstanceKey;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Represents the instance that the service get deployed onto.
 * We enforce unique constraint in code based on the instance key sub class.
 * @author rktummala
 */
@OwnedBy(HarnessTeam.DX)
@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "InstanceKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "instance", noClassnameStored = true)
@HarnessEntity(exportable = true)
@TargetModule(HarnessModule._957_CG_BEANS)
public class Instance extends Base implements AccountAccess, ApplicationAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("instance_index1")
                 .field(InstanceKeys.appId)
                 .field(InstanceKeys.isDeleted)
                 .field(InstanceKeys.deletedAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("instance_index2")
                 .field(InstanceKeys.appId)
                 .field(InstanceKeys.infraMappingId)
                 .field(InstanceKeys.isDeleted)
                 .field(InstanceKeys.deletedAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("instance_index3")
                 .field(InstanceKeys.accountId)
                 .field(InstanceKeys.createdAt)
                 .field(InstanceKeys.isDeleted)
                 .field(InstanceKeys.deletedAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("instance_index5")
                 .field(InstanceKeys.appId)
                 .field(InstanceKeys.serviceId)
                 .field(InstanceKeys.createdAt)
                 .field(InstanceKeys.isDeleted)
                 .field(InstanceKeys.deletedAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("instance_index6")
                 .field(InstanceKeys.accountId)
                 .field(InstanceKeys.isDeleted)
                 .field(InstanceKeys.serviceId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("instance_index7")
                 .field(InstanceKeys.accountId)
                 .field(InstanceKeys.createdAt)
                 .field(InstanceKeys.deletedAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("instance_index8")
                 .field(InstanceKeys.appId)
                 .field(InstanceKeys.serviceId)
                 .field(InstanceKeys.isDeleted)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("instance_index9")
                 .field(InstanceKeys.accountId)
                 .field(InstanceKeys.isDeleted)
                 .field(InstanceKeys.deletedAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("instance_index10")
                 .field(InstanceKeys.accountId)
                 .field(InstanceKeys.infraMappingId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("instance_index11")
                 .field(InstanceKeys.infraMappingId)
                 .field(InstanceKeys.appId)
                 .field(InstanceKeys.createdAt)
                 .field(InstanceKeys.isDeleted)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("instance_index13")
                 .field(InstanceKeys.appId)
                 .field(InstanceKeys.isDeleted)
                 .field(InstanceKeys.envId)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_computeProviderId_instanceInfoNamespace_instanceInfoPodName_createdAt")
                 .field(InstanceKeys.accountId)
                 .field(InstanceKeys.computeProviderId)
                 .field(InstanceKeys.instanceInfoNamespace)
                 .field(InstanceKeys.instanceInfoPodName)
                 .descSortField(InstanceKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("instance_index_appId_infraMappingId_lastWorkflowExecutionId_lastUpdatedAt")
                 .field(InstanceKeys.appId)
                 .field(InstanceKeys.infraMappingId)
                 .field(InstanceKeys.lastWorkflowExecutionId)
                 .descSortField(InstanceKeys.lastUpdatedAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("inframapping_lastUpdated")
                 .field(InstanceKeys.infraMappingId)
                 .descSortField(InstanceKeys.lastUpdatedAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("appid_inframapping_isdeleted_createdatdesc")
                 .field(InstanceKeys.appId)
                 .field(InstanceKeys.infraMappingId)
                 .field(InstanceKeys.isDeleted)
                 .descSortField(InstanceKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_createdAt_appId")
                 .field(InstanceKeys.accountId)
                 .descSortField(InstanceKeys.createdAt)
                 .rangeField(InstanceKeys.appId)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_deletedAt_createdAt")
                 .field(InstanceKeys.accountId)
                 .rangeField(InstanceKeys.deletedAt)
                 .rangeField(InstanceKeys.createdAt)
                 .build())
        .build();
  }

  @NotEmpty private InstanceType instanceType;
  private HostInstanceKey hostInstanceKey;
  private ContainerInstanceKey containerInstanceKey;
  private PcfInstanceKey pcfInstanceKey;
  private PodInstanceKey podInstanceKey;
  private String envId;
  private String envName;
  private EnvironmentType envType;
  private String accountId;
  private String serviceId;
  private String serviceName;
  private String appName;

  private String infraMappingId;
  private String infraMappingName;
  private String infraMappingType;

  private String computeProviderId;
  private String computeProviderName;

  private String lastArtifactStreamId;

  private String lastArtifactId;
  private String lastArtifactName;
  private String lastArtifactSourceName;
  private String lastArtifactBuildNum;

  private String lastDeployedById;
  private String lastDeployedByName;
  private long lastDeployedAt;
  private String lastWorkflowExecutionId;
  private String lastWorkflowExecutionName;

  private String lastPipelineExecutionId;
  private String lastPipelineExecutionName;

  private InstanceInfo instanceInfo;

  @FdIndex private boolean isDeleted;
  private long deletedAt;

  @FdIndex private boolean needRetry;

  @Builder
  public Instance(String uuid, String appId, EmbeddedUser createdBy, long createdAt, EmbeddedUser lastUpdatedBy,
      long lastUpdatedAt, String entityYamlPath, InstanceType instanceType, HostInstanceKey hostInstanceKey,
      ContainerInstanceKey containerInstanceKey, PcfInstanceKey pcfInstanceKey, PodInstanceKey podInstanceKey,
      String envId, String envName, EnvironmentType envType, String accountId, String serviceId, String serviceName,
      String appName, String infraMappingId, String infraMappingName, String infraMappingType, String computeProviderId,
      String computeProviderName, String lastArtifactStreamId, String lastArtifactId, String lastArtifactName,
      String lastArtifactSourceName, String lastArtifactBuildNum, String lastDeployedById, String lastDeployedByName,
      long lastDeployedAt, String lastWorkflowExecutionId, String lastWorkflowExecutionName,
      String lastPipelineExecutionId, String lastPipelineExecutionName, InstanceInfo instanceInfo, boolean isDeleted,
      long deletedAt, boolean needRetry) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath);
    this.instanceType = instanceType;
    this.hostInstanceKey = hostInstanceKey;
    this.containerInstanceKey = containerInstanceKey;
    this.pcfInstanceKey = pcfInstanceKey;
    this.podInstanceKey = podInstanceKey;
    this.envId = envId;
    this.envName = envName;
    this.envType = envType;
    this.accountId = accountId;
    this.serviceId = serviceId;
    this.serviceName = serviceName;
    this.appName = appName;
    this.infraMappingId = infraMappingId;
    this.infraMappingName = infraMappingName;
    this.infraMappingType = infraMappingType;
    this.computeProviderId = computeProviderId;
    this.computeProviderName = computeProviderName;
    this.lastArtifactStreamId = lastArtifactStreamId;
    this.lastArtifactId = lastArtifactId;
    this.lastArtifactName = lastArtifactName;
    this.lastArtifactSourceName = lastArtifactSourceName;
    this.lastArtifactBuildNum = lastArtifactBuildNum;
    this.lastDeployedById = lastDeployedById;
    this.lastDeployedByName = lastDeployedByName;
    this.lastDeployedAt = lastDeployedAt;
    this.lastWorkflowExecutionId = lastWorkflowExecutionId;
    this.lastWorkflowExecutionName = lastWorkflowExecutionName;
    this.lastPipelineExecutionId = lastPipelineExecutionId;
    this.lastPipelineExecutionName = lastPipelineExecutionName;
    this.instanceInfo = instanceInfo;
    this.isDeleted = isDeleted;
    this.deletedAt = deletedAt;
    this.needRetry = needRetry;
  }

  @UtilityClass
  public static final class InstanceKeys {
    // Temporary
    public static final String appId = "appId";
    public static final String uuid = "uuid";
    public static final String createdAt = "createdAt";
    public static final String isDeleted = "isDeleted";
    public static final String instanceInfoPodName = "instanceInfo.podName";
    public static final String instanceInfoNamespace = "instanceInfo.namespace";
    public static final String deploymentType = "deploymentType";
    public static final String serviceId = "serviceId";
    public static final String envId = "envId";
    public static final String lastArtifactBuildNum = "lastArtifactBuildNum";
    public static final String lastWorkflowExecutionId = "lastWorkflowExecutionId";
    public static final String lastWorkflowExecutionName = "lastWorkflowExecutionName";
    public static final String infraMappingId = "infraMappingId";
    public static final String infraMappingName = "infraMappingName";
    public static final String lastUpdatedAt = "lastUpdatedAt";
  }
}
