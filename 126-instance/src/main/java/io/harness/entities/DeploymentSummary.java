/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.deploymentinfo.DeploymentInfo;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.pms.contracts.execution.Status;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "DeploymentSummaryKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "deploymentSummaryNG", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("deploymentSummaryNG")
@OwnedBy(HarnessTeam.DX)
public class DeploymentSummary implements PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("idx_instanceSyncKey_createdAt")
                 .field(DeploymentSummaryKeys.instanceSyncKey)
                 .descSortField(DeploymentSummaryKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("idx_accountIdentifier_orgIdentifier_projectIdentifier")
                 .field(DeploymentSummaryKeys.accountIdentifier)
                 .field(DeploymentSummaryKeys.orgIdentifier)
                 .field(DeploymentSummaryKeys.projectIdentifier)
                 .build())
        .build();
  }

  @Id @dev.morphia.annotations.Id private String id;
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private String pipelineExecutionId;
  private String pipelineExecutionName;
  private String stageNodeExecutionId;
  private Status stageStatus;
  private String stageSetupId;
  private RollbackStatus rollbackStatus;
  private ArtifactDetails artifactDetails;
  private String deployedById;
  private String deployedByName;
  private String infrastructureMappingId;
  private String infrastructureIdentifier;

  private String envGroupRef;
  private String infrastructureName;
  private String instanceSyncKey;
  private long deployedAt;
  private DeploymentInfo deploymentInfo;
  boolean isRollbackDeployment;
  @CreatedDate long createdAt;
  @LastModifiedDate long lastModifiedAt;
}
