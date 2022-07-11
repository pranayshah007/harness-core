/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.entity;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.encryption.Scope;
import io.harness.gitsync.persistance.GitSyncableEntity;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
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

@OwnedBy(CDC)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "TemplateEntityKeys")
@Entity(value = "templatesNG", noClassnameStored = true)
@Document("templatesNG")
@TypeAlias("templatesNG")
@HarnessEntity(exportable = true)
@StoreIn(DbAliases.TEMPLATE)
public class TemplateEntity
    implements GitSyncableEntity, PersistentEntity, AccountAccess, UuidAware, CreatedAtAware, UpdatedAtAware {
  @Setter @NonFinal @Id @org.mongodb.morphia.annotations.Id String uuid;

  @NotEmpty String accountId;
  @Wither @Trimmed String orgIdentifier;
  @Wither @Trimmed String projectIdentifier;
  @Wither @NotEmpty @EntityIdentifier String identifier;

  @Wither @EntityName String name;
  @Wither @Size(max = 1024) String description;
  @Wither @Singular @Size(max = 128) List<NGTag> tags;

  @Wither @NotEmpty String fullyQualifiedIdentifier;

  @Wither @NotEmpty String yaml;
  @Wither @Builder.Default Boolean deleted = Boolean.FALSE;

  @Wither String versionLabel;
  @Wither boolean isStableTemplate;
  @Wither boolean isLastUpdatedTemplate;
  @Wither TemplateEntityType templateEntityType;
  @Wither String childType;
  @Wither Scope templateScope;

  @Wither @Version Long version; // version for mongo operations
  @Setter @NonFinal @SchemaIgnore @FdIndex @CreatedDate long createdAt;
  @Setter @NonFinal @SchemaIgnore @NotNull @LastModifiedDate long lastUpdatedAt;

  @Wither @Setter @NonFinal String objectIdOfYaml;
  @Setter @NonFinal Boolean isFromDefaultBranch;
  @Setter @NonFinal String branch;
  @Setter @NonFinal String yamlGitConfigRef;
  @Setter @NonFinal String filePath;
  @Setter @NonFinal String rootFolder;
  @Wither @NonFinal Boolean isEntityInvalid;

  @Override
  public String getAccountIdentifier() {
    return accountId;
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_organizationId_projectId_identifier_label_repo_branch")
                 .unique(true)
                 .field(TemplateEntityKeys.accountId)
                 .field(TemplateEntityKeys.orgIdentifier)
                 .field(TemplateEntityKeys.projectIdentifier)
                 .field(TemplateEntityKeys.identifier)
                 .field(TemplateEntityKeys.versionLabel)
                 .field(TemplateEntityKeys.yamlGitConfigRef)
                 .field(TemplateEntityKeys.branch)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_organizationId_projectId_identifier")
                 .field(TemplateEntityKeys.accountId)
                 .field(TemplateEntityKeys.orgIdentifier)
                 .field(TemplateEntityKeys.projectIdentifier)
                 .field(TemplateEntityKeys.identifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_organizationId_projectId_identifier_isStable_repo_branch")
                 .field(TemplateEntityKeys.accountId)
                 .field(TemplateEntityKeys.orgIdentifier)
                 .field(TemplateEntityKeys.projectIdentifier)
                 .field(TemplateEntityKeys.identifier)
                 .field(TemplateEntityKeys.isStableTemplate)
                 .field(TemplateEntityKeys.yamlGitConfigRef)
                 .field(TemplateEntityKeys.branch)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_organizationId_projectId_identifier_isLastUpdated_repo_branch")
                 .field(TemplateEntityKeys.accountId)
                 .field(TemplateEntityKeys.orgIdentifier)
                 .field(TemplateEntityKeys.projectIdentifier)
                 .field(TemplateEntityKeys.identifier)
                 .field(TemplateEntityKeys.isLastUpdatedTemplate)
                 .field(TemplateEntityKeys.yamlGitConfigRef)
                 .field(TemplateEntityKeys.branch)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_org_project_childType")
                 .fields(Arrays.asList(TemplateEntityKeys.accountId, TemplateEntityKeys.orgIdentifier,
                     TemplateEntityKeys.projectIdentifier, TemplateEntityKeys.childType))
                 .build())
        .add(CompoundMongoIndex.builder().name("lastUpdatedAt_idx").field(TemplateEntityKeys.lastUpdatedAt).build())
        // for git sync indexes
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_organizationId_projectId_identifier_label_isDefault")
                 .field(TemplateEntityKeys.accountId)
                 .field(TemplateEntityKeys.orgIdentifier)
                 .field(TemplateEntityKeys.projectIdentifier)
                 .field(TemplateEntityKeys.identifier)
                 .field(TemplateEntityKeys.versionLabel)
                 .field(TemplateEntityKeys.isFromDefaultBranch)
                 .descSortField(TemplateEntityKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_project_org_repo_branch")
                 .fields(Arrays.asList(TemplateEntityKeys.accountId, TemplateEntityKeys.projectIdentifier,
                     TemplateEntityKeys.orgIdentifier, TemplateEntityKeys.yamlGitConfigRef, TemplateEntityKeys.branch))
                 .descSortField(TemplateEntityKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_project_org_isDefault")
                 .fields(Arrays.asList(TemplateEntityKeys.accountId, TemplateEntityKeys.projectIdentifier,
                     TemplateEntityKeys.orgIdentifier, TemplateEntityKeys.isFromDefaultBranch))
                 .descSortField(TemplateEntityKeys.createdAt)
                 .build())
        .build();
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
