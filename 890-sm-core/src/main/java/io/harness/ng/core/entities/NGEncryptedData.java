/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entities;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionType;

import software.wings.settings.SettingVariableTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PL)
@Data
@Builder
@FieldNameConstants(innerTypeName = "NGEncryptedDataKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "ngEncryptedRecords", noClassnameStored = true)
@Document("ngEncryptedRecords")
@TypeAlias("ngEncryptedData")
@StoreIn(DbAliases.NG_MANAGER)
public class NGEncryptedData implements PersistentEntity, EncryptedRecord {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("uniqueNGEncryptedDataIdx")
                 .unique(true)
                 .field(NGEncryptedDataKeys.accountIdentifier)
                 .field(NGEncryptedDataKeys.orgIdentifier)
                 .field(NGEncryptedDataKeys.projectIdentifier)
                 .field(NGEncryptedDataKeys.identifier)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id String id;

  @NotNull String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String identifier;
  String name;

  SettingVariableTypes type;

  String path;
  Set<EncryptedDataParams> parameters;
  String encryptionKey;
  char[] encryptedValue;
  String secretManagerIdentifier;
  EncryptionType encryptionType;
  char[] backupEncryptedValue;
  String backupEncryptionKey;
  String backupKmsId;
  EncryptionType backupEncryptionType;
  boolean base64Encoded;
  AdditionalMetadata additionalMetadata;

  @Override
  @JsonIgnore
  public String getUuid() {
    return this.id;
  }

  @Override
  @JsonIgnore
  public String getKmsId() {
    return this.secretManagerIdentifier;
  }
}
