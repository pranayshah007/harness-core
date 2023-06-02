/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.service.entity;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.gitsync.beans.StoreType;
import io.harness.mongo.collation.CollationLocale;
import io.harness.mongo.collation.CollationStrength;
import io.harness.mongo.index.Collation;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.ScopeAware;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.gitaware.GitAware;
import io.harness.template.yaml.TemplateRefHelper;

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

@OwnedBy(PIPELINE)
@Data
@Builder
@FieldNameConstants(innerTypeName = "ServiceEntityKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "servicesNG", noClassnameStored = true)
@Document("servicesNG")
@TypeAlias("io.harness.ng.core.service.entity.ServiceEntity")
@ChangeDataCapture(table = "services", dataStore = "ng-harness", fields = {}, handler = "Services")
@ChangeDataCapture(table = "tags_info_ng", dataStore = "ng-harness", fields = {}, handler = "TagsInfoNGCD")
public class ServiceEntity implements PersistentEntity, GitAware, ScopeAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_organizationIdentifier_projectIdentifier_serviceIdentifier")
                 .unique(true)
                 .field(ServiceEntityKeys.accountId)
                 .field(ServiceEntityKeys.orgIdentifier)
                 .field(ServiceEntityKeys.projectIdentifier)
                 .field(ServiceEntityKeys.identifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("index_accountId_orgId_projectId_createdAt_deleted_deletedAt")
                 .field(ServiceEntityKeys.accountId)
                 .field(ServiceEntityKeys.orgIdentifier)
                 .field(ServiceEntityKeys.projectIdentifier)
                 .field(ServiceEntityKeys.createdAt)
                 .field(ServiceEntityKeys.deleted)
                 .field(ServiceEntityKeys.deletedAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_deleted_collation_en")
                 .field(ServiceEntityKeys.accountId)
                 .field(ServiceEntityKeys.orgIdentifier)
                 .field(ServiceEntityKeys.projectIdentifier)
                 .field(ServiceEntityKeys.deleted)
                 .collation(
                     Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.PRIMARY).build())
                 .build())
        .build();
  }

  @Wither @Id @dev.morphia.annotations.Id String id;
  @Trimmed @NotEmpty String accountId;
  @NotEmpty @EntityIdentifier String identifier;
  @Trimmed String orgIdentifier;
  @Trimmed String projectIdentifier;
  @Wither @Singular @Size(max = 128) private List<NGTag> tags;

  @NotEmpty @EntityName String name;
  @Size(max = 1024) String description;

  @Wither @CreatedDate Long createdAt;
  @Wither @LastModifiedDate Long lastModifiedAt;
  @Wither @Version Long version;
  @Wither @Builder.Default Boolean deleted = Boolean.FALSE;
  @Builder.Default Boolean gitOpsEnabled;
  ServiceDefinitionType type;

  Long deletedAt;
  String yaml;

  // GitSync entities
  // Todo(Tathagat): Check if need to be deleted
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

  public String fetchNonEmptyYaml() {
    if (EmptyPredicate.isEmpty(yaml)) {
      NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(this);
      return NGServiceEntityMapper.toYaml(ngServiceConfig);
    }
    return yaml;
  }

  public boolean hasTemplateReferences() {
    if (EmptyPredicate.isEmpty(yaml)) {
      return false;
    }
    return TemplateRefHelper.hasTemplateRef(yaml);
  }

  @Override
  public String getData() {
    return this.yaml;
  }

  @Override
  public void setData(String data) {
    this.yaml = data;
  }
}
