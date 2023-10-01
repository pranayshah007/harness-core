/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.mappers.NgSecretConfigMapperHelper.ngMetaDataFromDto;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.SecretManagementException;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.secretmanagerclient.NGSecretManagerMetadata;
import io.harness.secretmanagerclient.dto.VaultConfigDTO;
import io.harness.secretmanagerclient.dto.VaultConfigUpdateDTO;

import software.wings.beans.VaultConfig;

import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class VaultConfigMapper {
  public static VaultConfig fromDTO(VaultConfigDTO vaultConfigDTO) {
    VaultConfig vaultConfig = VaultConfig.builder()
                                  .vaultUrl(vaultConfigDTO.getVaultUrl())
                                  .name(vaultConfigDTO.getName())
                                  .authToken(vaultConfigDTO.getAuthToken())
                                  .secretEngineName(vaultConfigDTO.getSecretEngineName())
                                  .secretEngineVersion(vaultConfigDTO.getSecretEngineVersion())
                                  .basePath(vaultConfigDTO.getBasePath())
                                  .appRoleId(vaultConfigDTO.getAppRoleId())
                                  .secretId(vaultConfigDTO.getSecretId())
                                  .renewalInterval(vaultConfigDTO.getRenewalIntervalMinutes())
                                  .isReadOnly(vaultConfigDTO.isReadOnly())
                                  .delegateSelectors(vaultConfigDTO.getDelegateSelectors())
                                  .namespace(vaultConfigDTO.getNamespace())
                                  .useVaultAgent(vaultConfigDTO.isUseVaultAgent())
                                  .useAwsIam(vaultConfigDTO.isUseAwsIam())
                                  .awsRegion(vaultConfigDTO.getAwsRegion())
                                  .vaultAwsIamRole(vaultConfigDTO.getVaultAwsIamRole())
                                  .xVaultAwsIamServerId(vaultConfigDTO.getXVaultAwsIamServerId())
                                  .useK8sAuth(vaultConfigDTO.isUseK8sAuth())
                                  .vaultK8sAuthRole(vaultConfigDTO.getVaultK8sAuthRole())
                                  .serviceAccountTokenPath(vaultConfigDTO.getServiceAccountTokenPath())
                                  .k8sAuthEndpoint(vaultConfigDTO.getK8sAuthEndpoint())
                                  .sinkPath(vaultConfigDTO.getSinkPath())
                                  .renewAppRoleToken(vaultConfigDTO.isRenewAppRoleToken())
                                  .enableCache(vaultConfigDTO.getEnableCache())
                                  .build();
    vaultConfig.setNgMetadata(ngMetaDataFromDto(vaultConfigDTO));
    vaultConfig.setAccountId(vaultConfigDTO.getAccountIdentifier());
    vaultConfig.setEncryptionType(vaultConfigDTO.getEncryptionType());
    vaultConfig.setDefault(vaultConfigDTO.isDefault());
    return vaultConfig;
  }

  private static void checkEqualValues(Object x, Object y, String fieldName) {
    if (x != null && !x.equals(y)) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR,
          String.format(
              "Cannot change the value of %s since there are secrets already present in vault. Please delete or migrate them and try again.",
              fieldName),
          USER);
    }
  }

  public static VaultConfig applyUpdate(
      VaultConfig vaultConfig, VaultConfigUpdateDTO updateDTO, boolean secretsPresentInVault) {
    if (secretsPresentInVault) {
      checkEqualValues(vaultConfig.getVaultUrl(), updateDTO.getVaultUrl(), "url");
      checkEqualValues(vaultConfig.getBasePath(), updateDTO.getBasePath(), "base path");
      checkEqualValues(vaultConfig.getSecretEngineName(), updateDTO.getSecretEngineName(), "secret engine name");
      checkEqualValues(
          vaultConfig.getSecretEngineVersion(), updateDTO.getSecretEngineVersion(), "secret engine version");
    }
    vaultConfig.setVaultUrl(updateDTO.getVaultUrl());
    Optional.ofNullable(updateDTO.getAuthToken()).filter(authToken -> !authToken.isEmpty()).ifPresent(authToken -> {
      vaultConfig.setAuthToken(authToken);
      vaultConfig.setAppRoleId(null);
      vaultConfig.setSecretId(null);
    });
    Optional.ofNullable(updateDTO.getAppRoleId())
        .filter(appRoleId -> !appRoleId.isEmpty())
        .ifPresent(vaultConfig::setAppRoleId);
    Optional.ofNullable(updateDTO.getSecretId()).filter(secretId -> !secretId.isEmpty()).ifPresent(secretId -> {
      vaultConfig.setSecretId(secretId);
      vaultConfig.setAuthToken(null);
    });
    vaultConfig.setSecretEngineVersion(updateDTO.getSecretEngineVersion());
    vaultConfig.setSecretEngineName(updateDTO.getSecretEngineName());
    vaultConfig.setBasePath(updateDTO.getBasePath());
    vaultConfig.setRenewalInterval(updateDTO.getRenewalIntervalMinutes());
    vaultConfig.setReadOnly(updateDTO.isReadOnly());
    vaultConfig.setDefault(updateDTO.isDefault());
    vaultConfig.setName(updateDTO.getName());

    if (!Optional.ofNullable(vaultConfig.getNgMetadata()).isPresent()) {
      vaultConfig.setNgMetadata(NGSecretManagerMetadata.builder().build());
    }
    vaultConfig.getNgMetadata().setTags(TagMapper.convertToList(updateDTO.getTags()));
    vaultConfig.getNgMetadata().setDescription(updateDTO.getDescription());
    return vaultConfig;
  }
}
