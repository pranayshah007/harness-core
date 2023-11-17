/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.secret;

import io.harness.delegate.core.beans.EncryptionConfig;
import io.harness.delegate.core.beans.HashicorpVaultConfig;

import software.wings.beans.VaultConfig;

import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

@Mapper(uses = {SecretManagerTypePojoProtoMapper.class, EncryptionTypePojoProtoMapper.class,
            VaultConfigPojoProtoMapper.class},
    nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedSourcePolicy = ReportingPolicy.IGNORE,
    collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED)
public interface VaultConfigPojoProtoMapper {
  VaultConfigPojoProtoMapper INSTANCE = Mappers.getMapper(VaultConfigPojoProtoMapper.class);

  @Mapping(target = "vaultConfig.authToken", source = "authToken",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
  @Mapping(target = "vaultConfig.secretEngineName", source = "secretEngineName",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
  @Mapping(target = "vaultConfig.secretEngineVersion", source = "secretEngineVersion",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
  @Mapping(target = "vaultConfig.namespace", source = "namespace",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
  @Mapping(target = "vaultConfig.basePath", source = "basePath",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
  @Mapping(target = "vaultConfig.isReadOnly", source = "readOnly",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "vaultConfig.appRoleId", source = "appRoleId",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "vaultConfig.secretId", source = "secretId",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "vaultConfig.useVaultAgent", source = "useVaultAgent",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "vaultConfig.sinkPath", source = "sinkPath",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "vaultConfig.useAwsIam", source = "useAwsIam",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "vaultConfig.vaultAwsIamRole", source = "vaultAwsIamRole",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "vaultConfig.awsRegion", source = "awsRegion",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "vaultConfig.XVaultAwsIamServerId", source = "XVaultAwsIamServerId",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "vaultConfig.useK8SAuth", source = "useK8sAuth",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "vaultConfig.vaultK8SAuthRole", source = "vaultK8sAuthRole",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "vaultConfig.serviceAccountTokenPath", source = "serviceAccountTokenPath",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "vaultConfig.k8SAuthEndpoint", source = "k8sAuthEndpoint",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  EncryptionConfig
  map(VaultConfig config);
}
