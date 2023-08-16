/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.entity;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.persistance.GitSyncableEntity;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;
import io.harness.persistence.gitaware.GitAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import java.util.Set;
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
@FieldNameConstants(innerTypeName = "GlobalTemplateEntityKeys")
@StoreIn(DbAliases.TEMPLATE)
@Entity(value = "globalTemplatesNG", noClassnameStored = true)
@Document("globalTemplatesNG")
@TypeAlias("globalTemplatesNG")
@HarnessEntity(exportable = true)
public class GlobalTemplateEntity
    implements GitAware, GitSyncableEntity, PersistentEntity, AccountAccess, UuidAware, CreatedAtAware, UpdatedAtAware {
  @Setter @NonFinal @Id @dev.morphia.annotations.Id String uuid;

  @NotEmpty String accountId;
  @Wither @Trimmed String orgIdentifier;
  @Wither @Trimmed String projectIdentifier;
  @Wither @NotEmpty @EntityIdentifier String identifier;

  @Wither @EntityName String name;
  @Wither @Size(max = 1024) String description;
  @Wither @Singular @Size(max = 128) List<NGTag> tags;

  @Wither @NotEmpty String fullyQualifiedIdentifier;

  @Wither @NotEmpty @NonFinal @Setter String yaml;
  @Wither @Builder.Default Boolean deleted = Boolean.FALSE;

  @Wither String versionLabel;
  @JsonProperty("isStableTemplate") @Wither boolean isStableTemplate;
  @Wither boolean isLastUpdatedTemplate;
  @Wither TemplateEntityType templateEntityType;
  @Wither String childType;
  @Wither Scope templateScope;

  @Wither @Version Long version; // version for mongo operations
  @Setter @NonFinal @SchemaIgnore @FdIndex @CreatedDate long createdAt;
  @Setter @NonFinal @SchemaIgnore @NotNull @LastModifiedDate long lastUpdatedAt;

  @Setter @NonFinal Set<String> modules;

  @Wither @Setter @NonFinal String objectIdOfYaml;
  @Setter @NonFinal Boolean isFromDefaultBranch;
  @Setter @NonFinal String branch;
  @Setter @NonFinal String yamlGitConfigRef;
  @Wither @Setter @NonFinal String filePath;
  @Wither @Setter @NonFinal String readMe;
  @Setter @NonFinal String rootFolder;
  @Wither @NonFinal Boolean isEntityInvalid;

  // git experience parameters after simplification
  @Wither @Setter @NonFinal StoreType storeType;
  @Wither @Setter @NonFinal String repo;
  @Wither @Setter @NonFinal String connectorRef;
  @Wither @Setter @NonFinal String repoURL;
  @Wither @Setter @NonFinal String fallBackBranch;
  @Wither @Setter @NonFinal String owner;

  // icon support for templates
  @Wither @Setter @NonFinal String icon;

  @Override
  public String getAccountIdentifier() {
    return accountId;
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_identifier_label")
                 .unique(true)
                 .field(GlobalTemplateEntityKeys.identifier)
                 .field(GlobalTemplateEntityKeys.versionLabel)
                 .build())
        .add(CompoundMongoIndex.builder().name("identifier").field(GlobalTemplateEntityKeys.identifier).build())
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

  @Override
  public String getData() {
    return yaml;
  }

  @Override
  public void setData(String yaml) {
    this.yaml = yaml;
  }
}