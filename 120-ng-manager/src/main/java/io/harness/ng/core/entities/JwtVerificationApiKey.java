/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.entities;

import com.google.common.collect.ImmutableList;
import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.mongo.CollationLocale;
import io.harness.mongo.CollationStrength;
import io.harness.mongo.index.Collation;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccountAccess;
import io.harness.ng.core.NGOrgAccess;
import io.harness.ng.core.NGProjectAccess;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "JwtVerificationApiKeyKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "ngJwtVerificationApiKeys", noClassnameStored = true)
@Document("ngJwtVerificationApiKeys")
@TypeAlias("ngJwtVerificationApiKeys")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.PL)
public class JwtVerificationApiKey
    implements PersistentEntity, UuidAware, NGAccountAccess, NGOrgAccess, NGProjectAccess {

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                .name("unique_idx")
                .field(JwtVerificationApiKeyKeys.accountIdentifier)
                .field(JwtVerificationApiKeyKeys.orgIdentifier)
                .field(JwtVerificationApiKeyKeys.projectIdentifier)
                .field(JwtVerificationApiKeyKeys.identifier)
                .field(JwtVerificationApiKeyKeys.parentIdentifier)
                .field(JwtVerificationApiKeyKeys.apiKeyType)
                .field(JwtVerificationApiKeyKeys.keyToMatch)
                .field(JwtVerificationApiKeyKeys.valueToMatch)
                .unique(true)
                .collation(
                    Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.PRIMARY).build())
                .build(),
            CompoundMongoIndex.builder()
                .name("list_keys_idx")
                .field(JwtVerificationApiKeyKeys.accountIdentifier)
                .field(JwtVerificationApiKeyKeys.orgIdentifier)
                .field(JwtVerificationApiKeyKeys.projectIdentifier)
                .field(JwtVerificationApiKeyKeys.parentIdentifier)
                .build())
        .build();
  }

  @org.springframework.data.annotation.Id @Id String uuid;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;

  @NotNull String accountIdentifier;
  @EntityIdentifier(allowBlank = true) String orgIdentifier;
  @EntityIdentifier(allowBlank = true) String projectIdentifier;

  @EntityIdentifier String identifier;
  @NGEntityName String name;
  @Size(max = 1024) String description;
  @NotNull @Singular @Size(max = 128) List<NGTag> tags;

  @NotNull String parentIdentifier;
  @NotNull String keyToMatch;
  @NotNull String valueToMatch;
  @NotNull ApiKeyType apiKeyType;

  private byte[] certificate;
  private String certificateUrl;
}
