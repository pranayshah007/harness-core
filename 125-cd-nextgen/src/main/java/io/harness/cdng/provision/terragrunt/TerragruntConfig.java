/*

  * Copyright 2022 Harness Inc. All rights reserved.
  * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
  * that can be found in the licenses directory at the root of this repository, also available at
  * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.cdng.provision.terragrunt;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.GitStoreConfigDTO;
import io.harness.delegate.beans.terragrunt.request.TerragruntRunConfiguration;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "TerragruntConfigKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "terragruntConfig", noClassnameStored = true)
@Document("terragruntConfig")
@TypeAlias("terragruntConfig")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CDP)
public class TerragruntConfig implements PersistentEntity, CreatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_entityId_createdAt")
                 .field(TerragruntConfigKeys.accountId)
                 .field(TerragruntConfigKeys.orgId)
                 .field(TerragruntConfigKeys.projectId)
                 .field(TerragruntConfigKeys.entityId)
                 .descSortField(TerragruntConfigKeys.createdAt)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id private String uuid;
  @NotNull String accountId;
  @NotNull String orgId;
  @NotNull String projectId;
  @NotNull String entityId;
  @NotNull String pipelineExecutionId;
  @NotNull long createdAt;

  GitStoreConfigDTO configFiles;
  List<TerragruntVarFileConfig> varFileConfigs;
  TerragruntBackendConfigFileConfig backendConfigFile;

  Map<String, String> environmentVariables;
  String workspace;
  List<String> targets;
  TerragruntRunConfiguration runConfiguration;
  boolean useConnectorCredentials;
}
