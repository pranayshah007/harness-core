/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.instance;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.ArtifactDetails;
import io.harness.entities.instanceinfo.InstanceInfo;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@ToString
@FieldNameConstants(innerTypeName = "InstanceDeploymentInfoKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "instanceDeploymentInfo", noClassnameStored = true)
@Document("instanceDeploymentInfo")
@TypeAlias("instanceDeploymentInfo")
@HarnessEntity(exportable = true)
@StoreIn(DbAliases.NG_MANAGER)
@OwnedBy(HarnessTeam.CDP)
public class InstanceDeploymentInfo implements PersistentEntity, UuidAware {
  @org.springframework.data.annotation.Id @Id String uuid;
  @CreatedDate private long createdAt;
  @LastModifiedDate private long lastModifiedAt;

  @NotNull private String accountIdentifier;
  @NotNull private String orgIdentifier;
  @NotNull private String projectIdentifier;

  @NotNull private String envIdentifier;
  @NotNull private String infraIdentifier;
  @NotNull private String serviceIdentifier;

  @NotNull private String stageExecutionId;

  @Nullable private String deploymentIdentifier;

  @NotNull private InstanceInfo instanceInfo;
  @NotNull private ArtifactDetails artifactDetails;
  @NotNull private InstanceDeploymentInfoStatus status;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_instance_deployment_info_idx")
                 .field(InstanceDeploymentInfoKeys.accountIdentifier)
                 .field(InstanceDeploymentInfoKeys.orgIdentifier)
                 .field(InstanceDeploymentInfoKeys.projectIdentifier)
                 .field(InstanceDeploymentInfoKeys.envIdentifier)
                 .field(InstanceDeploymentInfoKeys.infraIdentifier)
                 .field(InstanceDeploymentInfoKeys.serviceIdentifier)
                 .field(InstanceDeploymentInfoKeys.instanceInfo + ".host")
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("stage_execution_instance_deployment_info_idx")
                 .field(InstanceDeploymentInfoKeys.accountIdentifier)
                 .field(InstanceDeploymentInfoKeys.orgIdentifier)
                 .field(InstanceDeploymentInfoKeys.projectIdentifier)
                 .field(InstanceDeploymentInfoKeys.envIdentifier)
                 .field(InstanceDeploymentInfoKeys.infraIdentifier)
                 .field(InstanceDeploymentInfoKeys.serviceIdentifier)
                 .field(InstanceDeploymentInfoKeys.stageExecutionId)
                 .build())
        .build();
  }
}
