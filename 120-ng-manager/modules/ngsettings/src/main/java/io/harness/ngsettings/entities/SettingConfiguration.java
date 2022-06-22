package io.harness.ngsettings.entities;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.setting.SettingCategory;
import io.harness.ng.core.setting.SettingValueType;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Set;
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
  Set<String> allowedScopeLevels;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("category_identifier_allowedScopes_unique_idx")
                 .field(SettingConfigurationKeys.category)
                 .field(SettingConfigurationKeys.identifier)
                 .field(SettingConfigurationKeys.allowedScopeLevels)
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("category_allowedScopes_idx")
                 .field(SettingConfigurationKeys.category)
                 .field(SettingConfigurationKeys.allowedScopeLevels)
                 .unique(false)
                 .build())
        .build();
  }
}
