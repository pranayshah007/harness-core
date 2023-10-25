/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Data
@Builder
@ToString
@FieldNameConstants(innerTypeName = "DeploymentStagePlanCreationInfoKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "deploymentStagePlanCreationInfo", noClassnameStored = true)
@Document("deploymentStagePlanCreationInfo")
@TypeAlias("deploymentStagePlanCreationInfo")
@OwnedBy(HarnessTeam.CDC)
public class DeploymentStagePlanCreationInfo implements PersistentEntity, UuidAware {
  // This class is used for saving only CD Stage data at plan creation state
  @org.springframework.data.annotation.Id @Id String uuid;
  @CreatedDate private long createdAt;
  @LastModifiedDate private long lastModifiedAt;

  @NotNull private String accountIdentifier;
  @NotNull private String orgIdentifier;
  @NotNull private String projectIdentifier;
  @NotNull private String planExecutionId;
  private String pipelineIdentifier;
  private String stageName;
  @NotNull private String stageIdentifier;
  private ServiceDefinitionType deploymentType;

  @NotNull private DeploymentStageType stageType;
  @JsonTypeInfo(use = NAME, property = "stageType", include = EXTERNAL_PROPERTY, visible = true)
  @Nullable
  private DeploymentStageDetailsInfo deploymentStageDetailsInfo;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("deployment_stage_plan_creation_info_using_plan_execution_id_idx")
                 .field(DeploymentStagePlanCreationInfoKeys.accountIdentifier)
                 .field(DeploymentStagePlanCreationInfoKeys.orgIdentifier)
                 .field(DeploymentStagePlanCreationInfoKeys.projectIdentifier)
                 .field(DeploymentStagePlanCreationInfoKeys.planExecutionId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("unique_deployment_stage_plan_creation_info_using_plan_execution_id_stage_id_idx")
                 .field(DeploymentStagePlanCreationInfoKeys.accountIdentifier)
                 .field(DeploymentStagePlanCreationInfoKeys.orgIdentifier)
                 .field(DeploymentStagePlanCreationInfoKeys.projectIdentifier)
                 .field(DeploymentStagePlanCreationInfoKeys.planExecutionId)
                 .field(DeploymentStagePlanCreationInfoKeys.stageIdentifier)
                 .unique(true)
                 .build())
        .build();
  }
}
