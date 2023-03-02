/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.FileStorageConfigDTO;
import io.harness.cdng.manifest.yaml.GitStoreConfigDTO;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "TerraformConfigKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "terraformConfig", noClassnameStored = true)
@Document("terraformConfig")
@TypeAlias("terraformConfig")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CDP)
public class TerraformConfig implements PersistentEntity, CreatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_entityId_createdAt")
                 .field(TerraformConfigKeys.accountId)
                 .field(TerraformConfigKeys.orgId)
                 .field(TerraformConfigKeys.projectId)
                 .field(TerraformConfigKeys.entityId)
                 .descSortField(TerraformConfigKeys.createdAt)
                 .build())
        .build();
  }

  @Id @dev.morphia.annotations.Id private String uuid;
  @NotNull String accountId;
  @NotNull String orgId;
  @NotNull String projectId;
  @NotNull String entityId;
  @NotNull String pipelineExecutionId;
  @NotNull long createdAt;

  GitStoreConfigDTO configFiles;
  FileStorageConfigDTO fileStoreConfig;
  List<TerraformVarFileConfig> varFileConfigs;
  String backendConfig;
  TerraformBackendConfigFileConfig backendConfigFileConfig;
  Map<String, String> environmentVariables;
  String workspace;
  List<String> targets;
  boolean useConnectorCredentials;
  boolean isTerraformCloudCli;
}
