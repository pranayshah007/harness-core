/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.environment.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.persistance.GitSyncableEntity;
import io.harness.mongo.collation.CollationLocale;
import io.harness.mongo.collation.CollationStrength;
import io.harness.mongo.index.Collation;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.ScopeAware;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.gitaware.GitAware;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import lombok.Singular;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_K8S, HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(PIPELINE)
@Data
@Builder
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "environmentsNG", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "EnvironmentKeys")
@Document("environmentsNG")
@ChangeDataCapture(table = "environments", dataStore = "ng-harness", fields = {}, handler = "Environments")
@TypeAlias("io.harness.ng.core.environment.beans.Environment")
public class Environment implements PersistentEntity, ScopeAware, GitAware, GitSyncableEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_organizationIdentifier_projectIdentifier_envIdentifier_repo_branch")
                 .unique(true)
                 .field(EnvironmentKeys.accountId)
                 .field(EnvironmentKeys.orgIdentifier)
                 .field(EnvironmentKeys.projectIdentifier)
                 .field(EnvironmentKeys.identifier)
                 .field(EnvironmentKeys.yamlGitConfigRef)
                 .field(EnvironmentKeys.branch)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_organizationIdentifier_projectIdentifier_deleted_collation_primary_en")
                 .field(EnvironmentKeys.accountId)
                 .field(EnvironmentKeys.orgIdentifier)
                 .field(EnvironmentKeys.projectIdentifier)
                 .field(EnvironmentKeys.deleted)
                 .collation(
                     Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.PRIMARY).build())
                 .build())
        .build();
  }
  @Wither @Id @dev.morphia.annotations.Id private String id;

  @Trimmed @NotEmpty private String accountId;
  @Trimmed private String orgIdentifier;
  @Trimmed private String projectIdentifier;

  @NotEmpty @EntityIdentifier private String identifier;
  @EntityName private String name;
  @Size(max = 1024) String description;
  @Size(max = 100) String color;
  @NotEmpty private EnvironmentType type;
  @Wither @Singular @Size(max = 128) private List<NGTag> tags;

  @Wither @CreatedDate Long createdAt;
  @Wither @LastModifiedDate Long lastModifiedAt;
  @Wither @Version Long version;
  @Builder.Default Boolean deleted = Boolean.FALSE;

  String yaml;

  // GitSync entities
  @Wither @Setter @NonFinal String objectIdOfYaml;
  @Setter @NonFinal Boolean isFromDefaultBranch;
  @Setter @NonFinal String branch;
  @Setter @NonFinal String yamlGitConfigRef;
  @Setter @NonFinal String filePath;
  @Setter @NonFinal String rootFolder;

  // GitX Entities
  @Wither @Setter @NonFinal StoreType storeType;
  @Wither @Setter @NonFinal String repo;
  @Wither @Setter @NonFinal String connectorRef;
  @Wither @Setter @NonFinal String repoURL;
  @Wither @Setter @NonFinal String fallBackBranch;

  // Service Override V2 migration
  @Builder.Default @Setter @NonFinal Boolean isMigratedToOverride = Boolean.FALSE;

  public String fetchNonEmptyYaml() {
    if (EmptyPredicate.isEmpty(yaml)) {
      NGEnvironmentConfig ngEnvironmentConfig = EnvironmentMapper.toNGEnvironmentConfig(this);
      return EnvironmentMapper.toYaml(ngEnvironmentConfig);
    }
    return yaml;
  }

  public String fetchRef() {
    return IdentifierRefHelper.getRefFromIdentifierOrRef(accountId, orgIdentifier, projectIdentifier, identifier);
  }

  @Override
  public String getUuid() {
    return id;
  }

  @Override
  public String getInvalidYamlString() {
    return yaml;
  }

  @Override
  public String getData() {
    return this.yaml;
  }

  @Override
  public void setData(String data) {
    this.yaml = data;
  }

  @Override
  public String getAccountIdentifier() {
    return accountId;
  }
}
