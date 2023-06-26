/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.entities.embedded.azurekeyvaultconnector;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.azure.AzureEnvironmentType.AZURE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.connector.entities.Connector;
import io.harness.delegate.beans.connector.azureconnector.AzureManagedIdentityType;
import io.harness.ng.DbAliases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.morphia.annotations.Entity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(PL)
@Value
@Builder
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "VaultConnectorKeys")
@JsonInclude(JsonInclude.Include.NON_NULL)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "connectors", noClassnameStored = true)
@Persistent
@TypeAlias("io.harness.connector.entities.embedded.azurekeyvaultconnector.AzureKeyVaultConnector")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AzureKeyVaultConnector extends Connector {
  String clientId;
  String tenantId;
  String vaultName;
  String secretKeyRef;
  String subscription;
  boolean isDefault;
  Boolean useManagedIdentity;
  AzureManagedIdentityType azureManagedIdentityType;
  String managedClientId;
  @Getter(AccessLevel.NONE) Boolean vaultConfiguredManually;
  @Builder.Default AzureEnvironmentType azureEnvironmentType = AZURE;
  public boolean isVaultConfiguredManually() {
    return Boolean.TRUE.equals(vaultConfiguredManually);
  }
}
