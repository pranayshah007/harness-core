/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.service.entity;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.StoreIn;
import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
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
import org.mongodb.morphia.annotations.Entity;
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
@Entity(value = "servicesNG", noClassnameStored = true)
@Document("servicesNG")
@TypeAlias("io.harness.ng.core.service.entity.ServiceEntity")
@ChangeDataCapture(table = "services", dataStore = "ng-harness", fields = {}, handler = "Services")
@StoreIn(DbAliases.NG_MANAGER)
public class ServiceEntity implements PersistentEntity {
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
        .build();
  }

  @Wither @Id @org.mongodb.morphia.annotations.Id String id;
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
  @Wither @Setter @NonFinal String objectIdOfYaml;
  @Setter @NonFinal Boolean isFromDefaultBranch;
  @Setter @NonFinal String branch;
  @Setter @NonFinal String yamlGitConfigRef;
  @Setter @NonFinal String filePath;
  @Setter @NonFinal String rootFolder;
}
