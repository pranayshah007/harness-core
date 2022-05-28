/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.beans.entity;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.persistance.GitSyncableEntity;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;
import io.harness.persistence.gitaware.GitAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import com.sun.istack.internal.NotNull;
import java.util.List;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import lombok.experimental.Wither;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PIPELINE)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "InputSetEntityKeys")
@Entity(value = "inputSetsPMS", noClassnameStored = true)
@Document("inputSetsPMS")
@TypeAlias("inputSetsPMS")
@HarnessEntity(exportable = true)
@StoreIn(DbAliases.PMS)
public class InputSetEntity
    implements GitAware, GitSyncableEntity, PersistentEntity, AccountAccess, UuidAware, CreatedAtAware, UpdatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_organizationId_projectId_pipelineId_inputSetId_repo_branch")
                 .unique(true)
                 .field(InputSetEntityKeys.accountId)
                 .field(InputSetEntityKeys.orgIdentifier)
                 .field(InputSetEntityKeys.projectIdentifier)
                 .field(InputSetEntityKeys.pipelineIdentifier)
                 .field(InputSetEntityKeys.identifier)
                 .field(InputSetEntityKeys.yamlGitConfigRef)
                 .field(InputSetEntityKeys.branch)
                 .build())
        // for full sync
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_organizationId_projectId_repo_branch")
                 .field(InputSetEntityKeys.accountId)
                 .field(InputSetEntityKeys.orgIdentifier)
                 .field(InputSetEntityKeys.projectIdentifier)
                 .field(InputSetEntityKeys.yamlGitConfigRef)
                 .field(InputSetEntityKeys.branch)
                 .descRangeField(InputSetEntityKeys.lastUpdatedAt)
                 .build())
        .build();
  }
  @Setter @NonFinal @Id @org.mongodb.morphia.annotations.Id String uuid;

  @Wither @NotEmpty @NonFinal @Setter String yaml;

  @NotEmpty String accountId;
  @NotEmpty String orgIdentifier;
  @Trimmed @NotEmpty String projectIdentifier;
  @Trimmed @NotEmpty String pipelineIdentifier;

  @NotEmpty String identifier;
  @Wither @EntityName String name;
  @Wither @Size(max = 1024) String description;
  @Wither @Singular @Size(max = 128) List<NGTag> tags;

  @NotEmpty InputSetEntityType inputSetEntityType;
  @Wither List<String> inputSetReferences;

  @Setter @NonFinal @SchemaIgnore @FdIndex @CreatedDate long createdAt;
  @Wither @Setter @NonFinal @SchemaIgnore @NotNull @LastModifiedDate long lastUpdatedAt;
  @Wither @Builder.Default Boolean deleted = Boolean.FALSE;
  @Wither @Version Long version;

  @Wither @Setter @NonFinal String objectIdOfYaml;
  @Setter @NonFinal Boolean isFromDefaultBranch;
  @Setter @NonFinal String branch;
  @Setter @NonFinal String yamlGitConfigRef;
  @Setter @NonFinal String filePath;
  @Setter @NonFinal String rootFolder;
  @Getter(AccessLevel.NONE) @Wither @NonFinal Boolean isEntityInvalid;

  // git experience parameters after simplification
  @Wither @Setter @NonFinal StoreType storeType;
  @Setter @NonFinal String repo;
  @Setter @NonFinal String connectorRef;

  @Wither @Builder.Default Boolean isInvalid = Boolean.FALSE;

  public String getData() {
    return yaml;
  }

  @Override
  public void setData(String data) {
    yaml = data;
  }

  @Override
  public String getAccountIdentifier() {
    return accountId;
  }

  @NonNull
  public Boolean getIsInvalid() {
    if (isInvalid == null) {
      return Boolean.FALSE;
    }
    return isInvalid;
  }

  @Override
  public boolean isEntityInvalid() {
    return Boolean.TRUE.equals(isEntityInvalid);
  }

  @Override
  public void setEntityInvalid(boolean isEntityInvalid) {
    this.isEntityInvalid = isEntityInvalid;
  }

  @Override
  public String getInvalidYamlString() {
    return yaml;
  }
}
