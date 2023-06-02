/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.entities;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.mongo.collation.CollationLocale;
import io.harness.mongo.collation.CollationStrength;
import io.harness.mongo.index.Collation;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PL)
@Data
@Builder
@FieldNameConstants(innerTypeName = "OrganizationKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "organizations", noClassnameStored = true)
@Document("organizations")
@TypeAlias("organizations")
@ChangeDataCapture(table = "organizations", dataStore = "ng-harness", fields = {}, handler = "Organizations")
@ChangeDataCapture(table = "tags_info_ng", dataStore = "ng-harness", fields = {}, handler = "TagsInfoNGCD")
public class Organization implements PersistentEntity, NGAccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountIdentifier_organizationIdentifier")
                 .field(OrganizationKeys.accountIdentifier)
                 .field(OrganizationKeys.identifier)
                 .unique(true)
                 .collation(
                     Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.PRIMARY).build())
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountIdentifierDeletedHarnessManagedNameWithCollationIdx")
                 .field(OrganizationKeys.accountIdentifier)
                 .field(OrganizationKeys.deleted)
                 .descSortField(OrganizationKeys.harnessManaged)
                 .ascSortField(OrganizationKeys.name)
                 .unique(false)
                 .collation(
                     Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.PRIMARY).build())
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountIdentifierIdentifierDeletedHarnessManagedNameWithCollationIdx")
                 .field(OrganizationKeys.accountIdentifier)
                 .field(OrganizationKeys.identifier)
                 .field(OrganizationKeys.deleted)
                 .descSortField(OrganizationKeys.harnessManaged)
                 .ascSortField(OrganizationKeys.name)
                 .unique(false)
                 .collation(
                     Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.PRIMARY).build())
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountIdentifierDeletedIdentifierIdx")
                 .field(OrganizationKeys.accountIdentifier)
                 .field(OrganizationKeys.deleted)
                 .field(OrganizationKeys.identifier)
                 .unique(false)
                 .build())
        .build();
  }

  @Wither @Id @dev.morphia.annotations.Id String id;
  String accountIdentifier;
  @EntityIdentifier(allowBlank = false) @FdIndex String identifier;

  @NGEntityName String name;

  @NotNull @Size(max = 1024) String description;
  @NotNull @Singular @Size(max = 128) List<NGTag> tags;

  @Builder.Default Boolean harnessManaged = Boolean.FALSE;
  @Wither @CreatedDate Long createdAt;
  @Wither @LastModifiedDate Long lastModifiedAt;
  @Wither @Version Long version;
  @Builder.Default Boolean deleted = Boolean.FALSE;
}
