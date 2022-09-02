/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.envGroup.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.gitsync.persistance.GitSyncableEntity;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
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
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "environmentGroupNG", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "EnvironmentGroupKeys")
@Document("environmentGroupNG")
@TypeAlias("io.harness.cdng.envGroup.beans.EnvironmentGroupEntity")
public class EnvironmentGroupEntity implements PersistentEntity, GitSyncableEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_organizationIdentifier_projectIdentifier_envGroupIdentifier")
                 .unique(true)
                 .field(EnvironmentGroupKeys.accountId)
                 .field(EnvironmentGroupKeys.orgIdentifier)
                 .field(EnvironmentGroupKeys.projectIdentifier)
                 .field(EnvironmentGroupKeys.identifier)
                 .build())
        .build();
  }
  @Wither @Id @org.mongodb.morphia.annotations.Id private String uuid;

  // Yaml Of Env Group
  @Wither @NotEmpty String yaml;

  @Trimmed @NotEmpty private String accountId;
  @Trimmed private String orgIdentifier;
  @Trimmed private String projectIdentifier;

  @NotEmpty @EntityIdentifier private String identifier;
  @Wither @Trimmed @EntityName private String name;
  @Wither @Size(max = 1024) String description;
  @Wither @Size(max = 100) String color;
  @Wither @Singular @Size(max = 128) private List<NGTag> tags;

  @Wither @CreatedDate Long createdAt;
  @Wither @LastModifiedDate Long lastModifiedAt;
  @Wither @Version Long version;
  @Wither @Builder.Default Boolean deleted = Boolean.FALSE;

  // Linked Environment Identifiers
  @Wither private List<String> envIdentifiers;

  // Git Sync
  @Wither @NonFinal String objectIdOfYaml;
  @NonFinal Boolean isFromDefaultBranch;
  @NonFinal String branch;
  @NonFinal String yamlGitConfigRef;
  @NonFinal String filePath;
  @NonFinal String rootFolder;
  @Getter(AccessLevel.NONE) @Wither @NonFinal Boolean isEntityInvalid;

  @Override
  public String getAccountIdentifier() {
    return accountId;
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
