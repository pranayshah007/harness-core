/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.beans;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.Trimmed;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.encryption.Scope;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.Arrays;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@TypeAlias("io.harness.gitsync.common.beans.yamlGitConfigs")
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.NG_MANAGER)
@Document("yamlGitConfigs")
@Entity(value = "yamlGitConfigs", noClassnameStored = true)
@OwnedBy(DX)
@FieldNameConstants(innerTypeName = "YamlGitConfigKeys")
public class YamlGitConfig implements PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware, UpdatedAtAware,
                                      UpdatedByAware, AccountAccess {
  @org.springframework.data.annotation.Id @dev.morphia.annotations.Id private String uuid;
  @NotEmpty @EntityIdentifier private String identifier;
  @NotEmpty private String name;
  @Trimmed @NotEmpty private String accountId;
  private String projectIdentifier;
  private String orgIdentifier;
  @NotEmpty String gitConnectorRef;

  /*
   * The git ConnectorDisconnectHandler which we are using to do full sync can be one of the default ConnectorDisconnectHandler
   * or it might belong to a repo and branch.
   * We are storing the repo and branch of the ConnectorDisconnectHandler, so that when we need to do get ConnectorDisconnectHandler,
   * we can use this repo and branch.
   *
   * For more detail refer:
   * https://tinyurl.com/3phuhy3f
   */
  String gitConnectorsRepo;
  String gitConnectorsBranch;
  @NotEmpty String repo;
  @NotEmpty String branch;
  @NotEmpty String webhookToken;
  Scope scope;
  List<YamlGitConfigDTO.RootFolder> rootFolders;
  YamlGitConfigDTO.RootFolder defaultRootFolder;
  @NotNull private ConnectorType gitConnectorType;
  @CreatedBy private EmbeddedUser createdBy;
  @CreatedDate private long createdAt;
  @LastModifiedBy private EmbeddedUser lastUpdatedBy;
  @LastModifiedDate private long lastUpdatedAt;
  @Version Long version;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_repo_unique_Index")
                 .fields(Arrays.asList(YamlGitConfigKeys.accountId, YamlGitConfigKeys.orgIdentifier,
                     YamlGitConfigKeys.projectIdentifier, YamlGitConfigKeys.repo))
                 .unique(true)
                 .build(),
            CompoundMongoIndex.builder()
                .name("accountId_orgId_projectId_identifier_unique_Index")
                .fields(Arrays.asList(YamlGitConfigKeys.accountId, YamlGitConfigKeys.orgIdentifier,
                    YamlGitConfigKeys.projectIdentifier, YamlGitConfigKeys.identifier))
                .unique(true)
                .build(),
            CompoundMongoIndex.builder()
                .name("repo_branch_index")
                .fields(Arrays.asList(YamlGitConfigKeys.repo, YamlGitConfigKeys.branch))
                .build())
        .build();
  }
}
