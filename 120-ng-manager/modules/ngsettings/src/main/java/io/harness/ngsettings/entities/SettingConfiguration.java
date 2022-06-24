/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.entities;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingValueType;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(HarnessTeam.PL)
@Data
@Builder
@FieldNameConstants(innerTypeName = "SettingConfigurationKeys")
@Entity(value = "settingConfigurations", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("settingConfigurations")
@StoreIn(DbAliases.NG_MANAGER)
@Persistent
public class SettingConfiguration implements PersistentEntity {
  @Id @org.mongodb.morphia.annotations.Id String id;
  @NotEmpty @EntityIdentifier String identifier;
  @NotEmpty @NGEntityName String name;
  @NotNull SettingCategory category;
  @NotNull String defaultValue;
  @NotNull SettingValueType valueType;
  @NotNull Set<String> allowedValues;
  @NotNull Boolean allowOverrides;
  @NotBlank Set<String> allowedScopes;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("category_identifier_allowedScopes_unique_idx")
                 .field(SettingConfigurationKeys.category)
                 .field(SettingConfigurationKeys.identifier)
                 .field(SettingConfigurationKeys.allowedScopes)
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("category_allowedScopes_idx")
                 .field(SettingConfigurationKeys.category)
                 .field(SettingConfigurationKeys.allowedScopes)
                 .unique(false)
                 .build())
        .build();
  }
}
