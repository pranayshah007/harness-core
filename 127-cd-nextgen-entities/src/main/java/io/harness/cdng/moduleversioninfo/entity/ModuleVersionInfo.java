/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.moduleversioninfo.entity;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.morphia.annotations.Entity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.mongodb.morphia.annotations.Id;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(CDP)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "moduleVersionInfo", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "ModuleVersionInfoKeys")
@Document("moduleVersionInfo")
@Persistent
@TypeAlias("io.harness.ng.core.ModuleVersionInfo.entity.ModuleVersionInfo")
public class ModuleVersionInfo implements PersistentEntity {
  @FdUniqueIndex
  @Id
  @dev.morphia.annotations.Id
  @EntityIdentifier
  @Schema(description = "Module Name")
  private String moduleName;
  @NotNull @Schema(description = "Module Version") private String version;
  @NotNull @Schema(description = "Module Display Name") private String displayName;
  @NotNull @Schema(description = "Module Last Modified") String lastModifiedAt;
  @NotNull @Schema(description = "Module versionUrl") private String versionUrl;
  @NotNull @Schema(description = "Module Release Notes Link") private String releaseNotesLink;
  @Wither private List<MicroservicesVersionInfo> microservicesVersionInfo;
}
