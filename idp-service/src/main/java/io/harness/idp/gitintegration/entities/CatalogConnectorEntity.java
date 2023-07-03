/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.idp.gitintegration.beans.CatalogInfraConnectorType;
import io.harness.idp.gitintegration.beans.CatalogRepositoryDetails;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import java.util.Set;
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
@FieldNameConstants(innerTypeName = "CatalogConnectorKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@StoreIn(DbAliases.IDP)
@Entity(value = "catalogConnector", noClassnameStored = true)
@Document("catalogConnector")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.IDP)
public class CatalogConnectorEntity
    implements PersistentEntity, UuidAware, UpdatedAtAware, CreatedAtAware, CreatedByAware, UpdatedByAware {
  @Id private String uuid;
  @NotNull String accountIdentifier;
  @NotNull String identifier;
  @NotNull String connectorIdentifier;
  @NotNull String connectorProviderType;
  @NotNull Set<String> delegateSelectors;
  @NotNull String host;
  @NotNull CatalogInfraConnectorType type;
  CatalogRepositoryDetails catalogRepositoryDetails;
  @SchemaIgnore @CreatedBy private EmbeddedUser createdBy;
  @SchemaIgnore @LastModifiedBy private EmbeddedUser lastUpdatedBy;
  @CreatedDate private long createdAt;
  @LastModifiedDate private long lastUpdatedAt;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_connectorId")
                 .field(CatalogConnectorKeys.accountIdentifier)
                 .field(CatalogConnectorKeys.connectorIdentifier)
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_connectorProviderType")
                 .field(CatalogConnectorKeys.accountIdentifier)
                 .field(CatalogConnectorKeys.connectorProviderType)
                 .unique(true)
                 .build())
        .build();
  }
}
