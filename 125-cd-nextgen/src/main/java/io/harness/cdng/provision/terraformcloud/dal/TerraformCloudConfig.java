/*

  * Copyright 2023 Harness Inc. All rights reserved.
  * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
  * that can be found in the licenses directory at the root of this repository, also available at
  * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.cdng.provision.terraformcloud.dal;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "TerraformCloudConfigKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "terraformCloudConfig", noClassnameStored = true)
@Document("terraformCloudConfig")
@TypeAlias("terraformCloudConfig")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudConfig implements PersistentEntity, CreatedAtAware {
  @Id @dev.morphia.annotations.Id private String uuid;
  @NotNull String accountId;
  @NotNull String orgId;
  @NotNull String projectId;
  @NotNull String provisionerIdentifier;
  @NotNull String stageExecutionId;
  @NotNull long createdAt;

  String connectorRef;
  String lastSuccessfulRun;
  String workspaceId;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_entityId_createdAt")
                 .field(TerraformCloudConfigKeys.accountId)
                 .field(TerraformCloudConfigKeys.orgId)
                 .field(TerraformCloudConfigKeys.projectId)
                 .field(TerraformCloudConfigKeys.provisionerIdentifier)
                 .descSortField(TerraformCloudConfigKeys.createdAt)
                 .build())
        .build();
  }
}
