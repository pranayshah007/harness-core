/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.accountsetting.entities;

import io.harness.annotations.StoreIn;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccountAccess;
import io.harness.ng.core.accountsetting.dto.AccountSettingConfig;
import io.harness.ng.core.accountsetting.dto.AccountSettingType;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@FieldNameConstants(innerTypeName = "AccountSettingsKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "accountSettings", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("accountSettings")
@Persistent
public class AccountSettings implements PersistentEntity, NGAccountAccess {
  @Builder
  public AccountSettings(String accountIdentifier, String orgIdentifier, String projectIdentifier, Long createdAt,
      Long lastModifiedAt, AccountSettingType type, AccountSettingConfig config) {
    this.accountIdentifier = accountIdentifier;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.createdAt = createdAt;
    this.lastModifiedAt = lastModifiedAt;
    this.type = type;
    this.config = config;
  }
  @Id @org.mongodb.morphia.annotations.Id String id;
  @Trimmed @NotEmpty String accountIdentifier;
  @Trimmed String orgIdentifier;
  @Trimmed String projectIdentifier;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
  AccountSettingType type;

  AccountSettingConfig config;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_type_unique_Index")
                 .fields(Arrays.asList(AccountSettingsKeys.accountIdentifier, AccountSettingsKeys.orgIdentifier,
                     AccountSettingsKeys.projectIdentifier, AccountSettingsKeys.type))
                 .unique(true)
                 .build())
        .build();
  }
}
