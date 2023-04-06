/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.entities;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.ModuleType;
import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.mongo.collation.CollationLocale;
import io.harness.mongo.collation.CollationStrength;
import io.harness.mongo.index.Collation;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccountAccess;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PL)
@Data
@Builder
@FieldNameConstants(innerTypeName = "ProjectKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "projects", noClassnameStored = true)
@Document("projects")
@TypeAlias("projects")
@ChangeDataCapture(table = "projects", dataStore = "ng-harness", fields = {}, handler = "Projects")
@ChangeDataCapture(table = "tags_info_ng", dataStore = "ng-harness", fields = {}, handler = "TagsInfoNGCD")
public class Project implements PersistentEntity, NGAccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountIdentifier_organizationIdentifier_projectIdentifier")
                 .field(ProjectKeys.accountIdentifier)
                 .field(ProjectKeys.orgIdentifier)
                 .field(ProjectKeys.identifier)
                 .unique(true)
                 .collation(
                     Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.PRIMARY).build())
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountDeletedModulesLastModifiedAtIdx")
                 .field(ProjectKeys.accountIdentifier)
                 .field(ProjectKeys.deleted)
                 .field(ProjectKeys.modules)
                 .descSortField(ProjectKeys.lastModifiedAt)
                 .unique(false)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountDeletedLastModifiedAtIdx")
                 .field(ProjectKeys.accountIdentifier)
                 .field(ProjectKeys.deleted)
                 .descSortField(ProjectKeys.lastModifiedAt)
                 .unique(false)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountDeletedOrgIdentifierSortLastModifiedAtIdx")
                 .field(ProjectKeys.accountIdentifier)
                 .field(ProjectKeys.deleted)
                 .field(ProjectKeys.orgIdentifier)
                 .field(ProjectKeys.identifier)
                 .descSortField(ProjectKeys.lastModifiedAt)
                 .unique(false)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountOrgIdentifierDeletedCreatedAtLastModifiedAtIdx")
                 .field(ProjectKeys.accountIdentifier)
                 .field(ProjectKeys.orgIdentifier)
                 .field(ProjectKeys.identifier)
                 .field(ProjectKeys.deleted)
                 .sortField(ProjectKeys.createdAt)
                 .sortField(ProjectKeys.lastModifiedAt)
                 .unique(false)
                 .build())
        .build();
  }

  @Wither @Id @dev.morphia.annotations.Id String id;
  String accountIdentifier;
  @EntityIdentifier(allowBlank = false) String identifier;
  @EntityIdentifier(allowBlank = false) String orgIdentifier;

  @NGEntityName String name;
  @NotEmpty String color;
  @NotNull @Singular @Size(max = 1024) @Deprecated List<ModuleType> modules;
  @NotNull @Size(max = 1024) String description;
  @NotNull @Singular @Size(max = 128) List<NGTag> tags;

  @Wither @CreatedDate Long createdAt;
  @Wither @LastModifiedDate Long lastModifiedAt;
  @Wither @Version Long version;
  @Builder.Default Boolean deleted = Boolean.FALSE;
}
