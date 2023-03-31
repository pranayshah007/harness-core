/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.plugin.beans;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.spec.server.idp.v1.model.PluginInfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@StoreIn(DbAliases.IDP)
@FieldNameConstants(innerTypeName = "PluginInfoEntityKeys")
@Entity(value = "pluginInfo", noClassnameStored = true)
@Document("pluginInfo")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class PluginInfoEntity implements PersistentEntity {
  @Id @org.mongodb.morphia.annotations.Id private String id;
  @FdUniqueIndex private String identifier;
  private String name;
  private String description;
  private String createdBy;
  private PluginInfo.CategoryEnum category;
  @Builder.Default private boolean core = false;
  private String source;
  private String config;
  private String iconUrl;
  private String imageUrl;
  @JsonProperty("exports") private ExportsData exports;
}
