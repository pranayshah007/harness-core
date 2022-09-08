/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.infrastructure.instance;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import software.wings.beans.entityinterface.ApplicationAccess;
import software.wings.beans.infrastructure.instance.info.ServerlessInstanceInfo;
import software.wings.beans.infrastructure.instance.key.AwsLambdaInstanceKey;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@EqualsAndHashCode(of = {"uuid", "appId"}, callSuper = false)
@FieldNameConstants(innerTypeName = "ServerlessInstanceKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "serverlessInstance", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class ServerlessInstance implements PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware, UpdatedAtAware,
                                           UpdatedByAware, ApplicationAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("serverless_instance_index1")
                 .field(ServerlessInstanceKeys.appId)
                 .field(ServerlessInstanceKeys.isDeleted)
                 .field(ServerlessInstanceKeys.deletedAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("serverless_instance_index2")
                 .field(ServerlessInstanceKeys.appId)
                 .field(ServerlessInstanceKeys.infraMappingId)
                 .field(ServerlessInstanceKeys.isDeleted)
                 .field(ServerlessInstanceKeys.deletedAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("serverless_instance_index3")
                 .field(ServerlessInstanceKeys.accountId)
                 .field(ServerlessInstanceKeys.createdAt)
                 .field(ServerlessInstanceKeys.isDeleted)
                 .field(ServerlessInstanceKeys.deletedAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("serverless_instance_index5")
                 .field(ServerlessInstanceKeys.appId)
                 .field(ServerlessInstanceKeys.serviceId)
                 .field(ServerlessInstanceKeys.createdAt)
                 .field(ServerlessInstanceKeys.isDeleted)
                 .field(ServerlessInstanceKeys.deletedAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("serverless_instance_index7")
                 .field(ServerlessInstanceKeys.accountId)
                 .field(ServerlessInstanceKeys.isDeleted)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("serverless_instance_index8")
                 .field(ServerlessInstanceKeys.appId)
                 .field(ServerlessInstanceKeys.serviceId)
                 .field(ServerlessInstanceKeys.isDeleted)
                 .build())
        .build();
  }

  @Id @NotNull(groups = {Update.class}) private String uuid;

  @FdIndex @NotNull protected String appId;

  private EmbeddedUser createdBy;

  @FdIndex private long createdAt;

  private EmbeddedUser lastUpdatedBy;

  @NotNull private long lastUpdatedAt;

  @NotEmpty private ServerlessInstanceType instanceType;
  private AwsLambdaInstanceKey lambdaInstanceKey;
  private String envId;
  private String envName;
  private EnvironmentType envType;
  @FdIndex private String accountId;
  private String appName;
  private String serviceId;
  private String serviceName;

  private String computeProviderId;
  private String computeProviderName;

  private String infraMappingId;
  private String infraMappingName;
  private String infraMappingType;

  private String lastPipelineExecutionId;
  private String lastPipelineExecutionName;

  private long lastDeployedAt;
  private String lastDeployedById;
  private String lastDeployedByName;

  private String lastWorkflowExecutionId;
  private String lastWorkflowExecutionName;

  private String lastArtifactSourceName;
  private String lastArtifactStreamId;
  private String lastArtifactBuildNum;
  private String lastArtifactId;
  private String lastArtifactName;

  private ServerlessInstanceInfo instanceInfo;

  @FdIndex private boolean isDeleted;
  @FdIndex private long deletedAt;

  @Builder(toBuilder = true)
  public ServerlessInstance(String uuid, String appId, EmbeddedUser createdBy, long createdAt,
      EmbeddedUser lastUpdatedBy, long lastUpdatedAt, ServerlessInstanceType instanceType,
      AwsLambdaInstanceKey lambdaInstanceKey, String envId, String envName, EnvironmentType envType, String accountId,
      String appName, String serviceId, String serviceName, String computeProviderId, String computeProviderName,
      String infraMappingId, String infraMappingName, String infraMappingType, String lastPipelineExecutionId,
      String lastPipelineExecutionName, long lastDeployedAt, String lastDeployedById, String lastDeployedByName,
      String lastWorkflowExecutionId, String lastWorkflowExecutionName, String lastArtifactSourceName,
      String lastArtifactStreamId, String lastArtifactBuildNum, String lastArtifactId, String lastArtifactName,
      ServerlessInstanceInfo instanceInfo, boolean isDeleted, long deletedAt) {
    this.uuid = uuid;
    this.appId = appId;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.lastUpdatedBy = lastUpdatedBy;
    this.lastUpdatedAt = lastUpdatedAt;
    this.instanceType = instanceType;
    this.lambdaInstanceKey = lambdaInstanceKey;
    this.envId = envId;
    this.envName = envName;
    this.envType = envType;
    this.accountId = accountId;
    this.appName = appName;
    this.serviceId = serviceId;
    this.serviceName = serviceName;
    this.computeProviderId = computeProviderId;
    this.computeProviderName = computeProviderName;
    this.infraMappingId = infraMappingId;
    this.infraMappingName = infraMappingName;
    this.infraMappingType = infraMappingType;
    this.lastPipelineExecutionId = lastPipelineExecutionId;
    this.lastPipelineExecutionName = lastPipelineExecutionName;
    this.lastDeployedAt = lastDeployedAt;
    this.lastDeployedById = lastDeployedById;
    this.lastDeployedByName = lastDeployedByName;
    this.lastWorkflowExecutionId = lastWorkflowExecutionId;
    this.lastWorkflowExecutionName = lastWorkflowExecutionName;
    this.lastArtifactSourceName = lastArtifactSourceName;
    this.lastArtifactStreamId = lastArtifactStreamId;
    this.lastArtifactBuildNum = lastArtifactBuildNum;
    this.lastArtifactId = lastArtifactId;
    this.lastArtifactName = lastArtifactName;
    this.instanceInfo = instanceInfo;
    this.isDeleted = isDeleted;
    this.deletedAt = deletedAt;
  }
}
