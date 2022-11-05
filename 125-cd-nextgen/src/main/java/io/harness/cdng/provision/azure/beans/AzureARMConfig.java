/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.azure.arm.AzureARMPreDeploymentData;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "AzureARMConfigKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "azureARMConfig", noClassnameStored = true)
@Document("azureARMConfig")
@TypeAlias("azureARMConfig")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CDP)
public class AzureARMConfig implements PersistentEntity, CreatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_provisionerIdentifier_stageExecutionId_createdAt")
                 .field(AzureARMConfigKeys.accountId)
                 .field(AzureARMConfigKeys.orgId)
                 .field(AzureARMConfigKeys.projectId)
                 .field(AzureARMConfigKeys.provisionerIdentifier)
                 .field(AzureARMConfigKeys.stageExecutionId)
                 .descSortField(AzureARMConfigKeys.createdAt)
                 .build())
        .build();
  }

  @org.springframework.data.annotation.Id @Id String uuid;
  @NotNull String accountId;
  @NotNull String orgId;
  @NotNull String projectId;
  @NotNull String stageExecutionId;
  @NotNull String provisionerIdentifier;
  @NotNull long createdAt;
  @NotNull AzureARMPreDeploymentData azureARMPreDeploymentData;
  @NotNull String connectorRef;
  @NotNull String scopeType;
}
