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
@FieldNameConstants(innerTypeName = "PluginProxyInfoEntityKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "pluginsProxyInfo", noClassnameStored = true)
@Document("pluginsProxyInfo")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class PluginsProxyInfoEntity implements PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_proxy_host")
                 .unique(true)
                 .field(PluginProxyInfoEntityKeys.accountIdentifier)
                 .field(PluginProxyInfoEntityKeys.host)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("compound_account_plugin_id")
                 .field(PluginProxyInfoEntityKeys.accountIdentifier)
                 .field(PluginProxyInfoEntityKeys.pluginId)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id private String id;
  private String accountIdentifier;
  @NotNull String pluginId;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
  @NotNull String host;
  @NotNull Boolean proxy;
  List<String> delegateSelectors;
}
