package io.harness.ngsettings.entities;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccountAccess;
import io.harness.ng.core.setting.SettingCategory;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(HarnessTeam.PL)
@Data
@Builder
@FieldNameConstants(innerTypeName = "SettingKeys")
@Entity(value = "settings", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("settings")
@StoreIn(DbAliases.NG_MANAGER)
@Persistent
public class Setting implements PersistentEntity, NGAccountAccess {
  @Id @org.mongodb.morphia.annotations.Id String id;
  @NotEmpty
  @EntityIdentifier String identifier;
  @Trimmed @NotEmpty String accountIdentifier;
  @Trimmed String orgIdentifier;
  @Trimmed String projectIdentifier;
  @NotNull SettingCategory category;
  @NotNull Boolean allowOverrides;
  @NotNull String value;
  @LastModifiedDate Long lastModifiedAt;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_category_identifier_unique_idx")
                 .field(SettingKeys.accountIdentifier)
                 .field(SettingKeys.orgIdentifier)
                 .field(SettingKeys.projectIdentifier)
                 .field(SettingKeys.category)
                 .field(SettingKeys.identifier)
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_category_idx")
                 .field(SettingKeys.accountIdentifier)
                 .field(SettingKeys.orgIdentifier)
                 .field(SettingKeys.projectIdentifier)
                 .field(SettingKeys.category)
                 .unique(false)
                 .build())
        .build();
  }

  @Override
  public String getAccountIdentifier() {
    return this.accountIdentifier;
  }
}
