/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.beans.entity;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "PluginsConfigEnvVariablesEntityKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "pluginsConfigEnvVariables", noClassnameStored = true)
@Document("pluginsConfigEnvVariables")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class PluginConfigEnvVariablesEntity implements PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_env_name")
                 .unique(true)
                 .field(PluginsConfigEnvVariablesEntityKeys.accountIdentifier)
                 .field(PluginsConfigEnvVariablesEntityKeys.envName)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id private String id;
  private String accountIdentifier;
  @NotNull String pluginId;
  @NotNull String pluginName;
  @NotNull String envName;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
  @NotNull Long enabledDisabledAt;
}