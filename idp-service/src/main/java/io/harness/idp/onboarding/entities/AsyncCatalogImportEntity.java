/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.idp.onboarding.beans.AsyncCatalogImportDetails;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.CatalogConnectorInfo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "AsyncCatalogImportKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@StoreIn(DbAliases.IDP)
@Entity(value = "asyncCatalogImport", noClassnameStored = true)
@Document("asyncCatalogImport")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.IDP)
public class AsyncCatalogImportEntity
    implements PersistentEntity, UuidAware, UpdatedAtAware, CreatedAtAware, CreatedByAware, UpdatedByAware {
  @Id private String uuid;
  @FdUniqueIndex @NotNull String accountIdentifier;
  @NotNull AsyncCatalogImportDetails catalogDomains;
  @NotNull AsyncCatalogImportDetails catalogSystems;
  @NotNull AsyncCatalogImportDetails catalogComponents;
  @NotNull String catalogInfraConnectorType;
  @NotNull CatalogConnectorInfo catalogConnectorInfo;
  @NotNull UserPrincipal userPrincipal;
  @SchemaIgnore @CreatedBy private EmbeddedUser createdBy;
  @SchemaIgnore @LastModifiedBy private EmbeddedUser lastUpdatedBy;
  @CreatedDate private long createdAt;
  @LastModifiedDate private long lastUpdatedAt;
}
