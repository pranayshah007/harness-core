/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
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

import com.google.common.collect.ImmutableList;
import java.time.Duration;
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
@FieldNameConstants(innerTypeName = "ApiKeyKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "ngApiKeys", noClassnameStored = true)
@Document("ngApiKeys")
@TypeAlias("ngApiKeys")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.PL)
public class ApiKey implements PersistentEntity, UuidAware, NGAccountAccess, NGOrgAccess, NGProjectAccess {
  public static long DEFAULT_TTL_FOR_TOKEN = Duration.ofDays(30).toMillis();

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_idx")
                 .field(ApiKeyKeys.accountIdentifier)
                 .field(ApiKeyKeys.orgIdentifier)
                 .field(ApiKeyKeys.projectIdentifier)
                 .field(ApiKeyKeys.identifier)
                 .field(ApiKeyKeys.parentIdentifier)
                 .field(ApiKeyKeys.apiKeyType)
                 .unique(true)
                 .collation(
                     Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.PRIMARY).build())
                 .build(),
            CompoundMongoIndex.builder()
                .name("list_keys_idx")
                .field(ApiKeyKeys.accountIdentifier)
                .field(ApiKeyKeys.orgIdentifier)
                .field(ApiKeyKeys.projectIdentifier)
                .field(ApiKeyKeys.parentIdentifier)
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
  @NotNull @Size(max = 1024) String description;
  @NotNull @Singular @Size(max = 128) List<NGTag> tags;

  @NotNull String parentIdentifier;
  ApiKeyType apiKeyType;
  @Builder.Default long defaultTimeToExpireToken = DEFAULT_TTL_FOR_TOKEN;
}
