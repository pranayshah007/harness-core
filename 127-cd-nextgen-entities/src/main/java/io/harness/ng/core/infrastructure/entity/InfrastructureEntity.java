/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.entity;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.persistance.GitSyncableEntity;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.ScopeAware;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.gitaware.GitAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import lombok.Singular;
import lombok.With;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import lombok.experimental.Wither;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(PIPELINE)
@Data
@Builder
@FieldNameConstants(innerTypeName = "InfrastructureEntityKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "infrastructures", noClassnameStored = true)
@Document("infrastructures")
@TypeAlias("io.harness.ng.core.infrastructure.entity.InfrastructureEntity")
@ChangeDataCapture(table = "infrastructures", dataStore = "ng-harness", fields = {}, handler = "Infrastructures")
public class InfrastructureEntity implements PersistentEntity, GitAware, ScopeAware, GitSyncableEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_organizationIdentifier_projectIdentifier_envIdentifier_infraIdentifier")
                 .unique(true)
                 .field(InfrastructureEntityKeys.accountId)
                 .field(InfrastructureEntityKeys.orgIdentifier)
                 .field(InfrastructureEntityKeys.projectIdentifier)
                 .field(InfrastructureEntityKeys.envIdentifier)
                 .field(InfrastructureEntityKeys.identifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_organizationIdentifier_projectIdentifier_envIdentifier_createdAt")
                 .field(InfrastructureEntityKeys.accountId)
                 .field(InfrastructureEntityKeys.orgIdentifier)
                 .field(InfrastructureEntityKeys.projectIdentifier)
                 .field(InfrastructureEntityKeys.envIdentifier)
                 .field(InfrastructureEntityKeys.createdAt)
                 .build())
        .build();
  }

  @Wither @Id @dev.morphia.annotations.Id String id;
  @Trimmed @NotEmpty String accountId;
  @NotEmpty @EntityIdentifier String identifier;
  @Trimmed String orgIdentifier;
  @Trimmed String projectIdentifier;

  @Trimmed String envIdentifier;

  @Wither @Singular @Size(max = 128) private List<NGTag> tags;

  @With @NotEmpty @EntityName String name;
  @With @Size(max = 1024) String description;

  @Wither @CreatedDate Long createdAt;
  @Wither @LastModifiedDate Long lastModifiedAt;
  @With @NotNull InfrastructureType type;
  @Wither ServiceDefinitionType deploymentType;
  @Wither String yaml;
  @Builder.Default Boolean obsolete = Boolean.FALSE;

  // GitX field
  @Wither @Setter @NonFinal StoreType storeType;
  @Wither @Setter @NonFinal String repo;
  @Wither @Setter @NonFinal String connectorRef;
  @Wither @Setter @NonFinal String repoURL;
  @Wither @Setter @NonFinal String fallBackBranch;
  @Setter @NonFinal String filePath;

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
    return yaml;
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
