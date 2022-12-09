/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.beans.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.StoreIn;
import io.harness.beans.PluginMetadata;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "PluginMetadataConfigKeys")
@StoreIn(DbAliases.CIMANAGER)
@Entity(value = "pluginMetadataConfig", noClassnameStored = true)
@Document("pluginMetadataConfig")
@TypeAlias("pluginMetadataConfig")
@RecasterAlias("io.harness.ci.beans.entities.PluginMetadataConfig")
@HarnessEntity(exportable = false)
public class PluginMetadataConfig implements UuidAware, PersistentEntity {
  @Id @org.mongodb.morphia.annotations.Id String uuid;
  int version;
  PluginMetadata metadata;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder().name("version").field(PluginMetadataConfigKeys.version).build())
        .add(CompoundMongoIndex.builder().name("name").field(PluginMetadataConfigKeys.metadata + ".name").build())
        .build();
  }
}
