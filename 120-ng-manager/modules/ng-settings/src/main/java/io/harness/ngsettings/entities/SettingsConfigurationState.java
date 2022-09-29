/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;

import javax.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "SettingsConfigurationStateKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "settingsConfigurationState", noClassnameStored = true)
@Document("settingsConfigurationState")
@TypeAlias("NGSettingsConfigurationState")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.PL)
public class SettingsConfigurationState {
  @Id @org.mongodb.morphia.annotations.Id String id;
  @FdIndex @NotEmpty String identifier;
  int configVersion;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
  @Version Long documentVersion;
}
