/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.secret;

import io.harness.delegate.core.beans.EncryptionConfig;

import software.wings.beans.AzureVaultConfig;

import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

@Mapper(uses = {SecretManagerTypePojoProtoMapper.class, EncryptionTypePojoProtoMapper.class,
            AzureVaultConfigPojoProtoMapper.class},
    nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedSourcePolicy = ReportingPolicy.IGNORE,
    collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED)
public interface AzureVaultConfigPojoProtoMapper {
  AzureVaultConfigPojoProtoMapper INSTANCE = Mappers.getMapper(AzureVaultConfigPojoProtoMapper.class);

  @Mapping(target = "azureVaultConfig.clientId", source = "clientId",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
  @Mapping(target = "azureVaultConfig.secretKey", source = "secretKey",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
  @Mapping(target = "azureVaultConfig.tenantId", source = "tenantId",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
  @Mapping(target = "azureVaultConfig.vaultName", source = "vaultName",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
  @Mapping(target = "azureVaultConfig.subscription", source = "subscription",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
  @Mapping(target = "azureVaultConfig.useManagedIdentity", source = "useManagedIdentity",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "azureVaultConfig.managedClientId", source = "managedClientId",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  EncryptionConfig
  map(AzureVaultConfig config);
}
