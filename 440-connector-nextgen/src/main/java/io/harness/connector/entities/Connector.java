/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.entities;

import static io.harness.connector.entities.Connector.ConnectorKeys;

import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.connector.ConnectorActivityDetails;
import io.harness.connector.ConnectorCategory;
import io.harness.connector.ConnectorConnectivityDetails;
import io.harness.connector.ConnectorConnectivityDetails.ConnectorConnectivityDetailsKeys;
import io.harness.connector.entities.embedded.vaultconnector.VaultConnector.VaultConnectorKeys;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.data.validator.Trimmed;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.gitsync.persistance.GitSyncableEntity;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccountAccess;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.Singular;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@FieldNameConstants(innerTypeName = "ConnectorKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "connectors", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("connectors")
@Persistent
@OwnedBy(HarnessTeam.DX)
@ChangeDataCapture(table = "connectors", dataStore = "ng-harness",
    fields = {ConnectorKeys.accountIdentifier, ConnectorKeys.orgIdentifier, ConnectorKeys.projectIdentifier,
        ConnectorKeys.identifier, ConnectorKeys.name, ConnectorKeys.scope, ConnectorKeys.createdAt,
        ConnectorKeys.createdBy, ConnectorKeys.lastUpdatedBy, ConnectorKeys.type, ConnectorKeys.categories},
    handler = "Connectors")
public abstract class Connector implements PersistentEntity, NGAccountAccess, GitSyncableEntity {
  @Id @dev.morphia.annotations.Id String id;
  @NotEmpty @EntityIdentifier String identifier;
  @NotEmpty @NGEntityName String name;
  @NotEmpty io.harness.encryption.Scope scope;
  String description;
  @Trimmed @NotEmpty String accountIdentifier;
  @Trimmed String orgIdentifier;
  @Trimmed String projectIdentifier;
  @NotEmpty String fullyQualifiedIdentifier;
  @NotEmpty ConnectorType type;
  @NotEmpty List<ConnectorCategory> categories;
  @NotNull @Singular @Size(max = 128) List<NGTag> tags;
  Set<String> delegateSelectors;
  @CreatedBy private EmbeddedUser createdBy;
  @LastModifiedBy private EmbeddedUser lastUpdatedBy;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
  @Version Long version;
  Long timeWhenConnectorIsLastUpdated;
  ConnectorConnectivityDetails connectivityDetails;
  ConnectorActivityDetails activityDetails;
  Boolean deleted = Boolean.FALSE;
  String heartbeatPerpetualTaskId;
  String objectIdOfYaml;
  Boolean isFromDefaultBranch;
  String branch;
  String yamlGitConfigRef;
  String filePath;
  String rootFolder;
  Boolean executeOnDelegate = Boolean.TRUE;
  @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE) Boolean isEntityInvalid;
  String invalidYamlString; // TODO: remove this field after RenameInvalidYamlStringToYamlMigration runs
  String yaml;

  public void setEntityInvalid(boolean isEntityInvalid) {
    this.isEntityInvalid = isEntityInvalid;
  }

  public boolean isEntityInvalid() {
    return Boolean.TRUE.equals(isEntityInvalid);
  }

  @Override
  public String getAccountIdentifier() {
    return accountIdentifier;
  }

  public static final String CONNECTOR_COLLECTION_NAME = "connectors";

  @Override
  public String getUuid() {
    return getId();
  }

  public boolean getDeleted() {
    if (deleted == null) {
      return Boolean.FALSE;
    }
    return deleted;
  }

  @UtilityClass
  public static final class ConnectorKeys {
    public static final String connectionStatus =
        ConnectorKeys.connectivityDetails + "." + ConnectorConnectivityDetailsKeys.status;
    public static final String tagKey = ConnectorKeys.tags + "." + NGTagKeys.key;
    public static final String tagValue = ConnectorKeys.tags + "." + NGTagKeys.value;
    public static final String featuresEnabled = "featuresEnabled";
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_name_Index")
                 .fields(Arrays.asList(ConnectorKeys.accountIdentifier, ConnectorKeys.orgIdentifier,
                     ConnectorKeys.projectIdentifier, ConnectorKeys.name))
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("fullyQualifiedIdentifier_deleted_Index")
                 .fields(Arrays.asList(ConnectorKeys.fullyQualifiedIdentifier, ConnectorKeys.deleted))
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_type_status_deletedAt_decreasing_sort_Index")
                 .fields(Arrays.asList(ConnectorKeys.accountIdentifier, ConnectorKeys.orgIdentifier,
                     ConnectorKeys.projectIdentifier, ConnectorKeys.type, ConnectorKeys.connectionStatus,
                     ConnectorKeys.deleted))
                 .descSortField(ConnectorKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_identifier_repo_branch_unique_index")
                 .fields(Arrays.asList(ConnectorKeys.accountIdentifier, ConnectorKeys.orgIdentifier,
                     ConnectorKeys.projectIdentifier, ConnectorKeys.identifier, ConnectorKeys.yamlGitConfigRef,
                     ConnectorKeys.branch))
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_org_proj_identifier_isDefault_Index")
                 .fields(Arrays.asList(ConnectorKeys.accountIdentifier, ConnectorKeys.orgIdentifier,
                     ConnectorKeys.projectIdentifier, ConnectorKeys.identifier, ConnectorKeys.isFromDefaultBranch))
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_org_project_repo_branch_Index")
                 .fields(Arrays.asList(ConnectorKeys.accountIdentifier, ConnectorKeys.orgIdentifier,
                     ConnectorKeys.projectIdentifier, ConnectorKeys.yamlGitConfigRef, ConnectorKeys.branch))
                 .descSortField(ConnectorKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_org_project_isDefault_Index")
                 .fields(Arrays.asList(ConnectorKeys.accountIdentifier, ConnectorKeys.orgIdentifier,
                     ConnectorKeys.projectIdentifier, ConnectorKeys.isFromDefaultBranch))
                 .descSortField(ConnectorKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("nextTokenRenewIteration")
                 .field(VaultConnectorKeys.nextTokenRenewIteration)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("type_nextTokenRenewIteration")
                 .fields(Arrays.asList(ConnectorKeys.type, VaultConnectorKeys.nextTokenRenewIteration))
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_type_decreasing_sort_Index")
                 .fields(Arrays.asList(ConnectorKeys.accountIdentifier, ConnectorKeys.orgIdentifier,
                     ConnectorKeys.projectIdentifier, ConnectorKeys.type))
                 .descSortField(ConnectorKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("nextTokenLookupIteration")
                 .field(VaultConnectorKeys.nextTokenLookupIteration)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("type_nextTokenLookupIteration")
                 .fields(Arrays.asList(ConnectorKeys.type, VaultConnectorKeys.nextTokenLookupIteration))
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_type_status_featuresEnabled_decreasing_sort_Index")
                 .fields(Arrays.asList(ConnectorKeys.accountIdentifier, ConnectorKeys.type,
                     ConnectorKeys.connectionStatus, ConnectorKeys.featuresEnabled))
                 .descSortField(ConnectorKeys.lastModifiedAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_status_decreasing_sort_Index")
                 .fields(Arrays.asList(ConnectorKeys.accountIdentifier, ConnectorKeys.connectionStatus))
                 .descSortField(ConnectorKeys.lastModifiedAt)
                 .build())
        .build();
  }
}
