/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.ConnectorConstants.CONNECTOR_DECORATOR_SERVICE;
import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.helpers.GlobalSecretManagerUtils.GLOBAL_ACCOUNT_ID;
import static io.harness.security.encryption.EncryptionType.CUSTOM_NG;
import static io.harness.security.encryption.EncryptionType.LOCAL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.helper.CustomSecretManagerHelper;
import io.harness.connector.helper.HarnessManagedConnectorHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.services.NGConnectorSecretManagerService;
import io.harness.encryptors.CustomEncryptor;
import io.harness.encryptors.CustomEncryptorsRegistry;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.SecretManagerConfigDTOMapper;
import io.harness.secretmanagerclient.dto.CustomSecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.LocalConfigDTO;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.security.encryption.EncryptedDataParams;

import software.wings.beans.CustomSecretNGManagerConfig;
import software.wings.beans.NameValuePairWithDefault;
import software.wings.service.impl.security.NGEncryptorService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@Singleton
public class NGConnectorSecretManagerServiceImpl implements NGConnectorSecretManagerService {
  private final ConnectorService connectorService;
  private final NGEncryptorService ngEncryptorService;
  private final HarnessManagedConnectorHelper harnessManagedConnectorHelper;
  private final CustomSecretManagerHelper customSecretManagerHelper;
  private final CustomEncryptorsRegistry customEncryptorsRegistry;

  @Inject
  public NGConnectorSecretManagerServiceImpl(@Named(CONNECTOR_DECORATOR_SERVICE) ConnectorService connectorService,
      NGEncryptorService ngEncryptorService, HarnessManagedConnectorHelper harnessManagedConnectorHelper,
      CustomSecretManagerHelper customSecretManagerHelper, CustomEncryptorsRegistry customEncryptorsRegistry) {
    this.connectorService = connectorService;
    this.ngEncryptorService = ngEncryptorService;
    this.harnessManagedConnectorHelper = harnessManagedConnectorHelper;
    this.customSecretManagerHelper = customSecretManagerHelper;
    this.customEncryptorsRegistry = customEncryptorsRegistry;
  }

  @Override
  public SecretManagerConfigDTO getUsingIdentifier(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String identifier, boolean maskSecrets) {
    ConnectorDTO connectorDTO = getConnectorDTO(accountIdentifier, orgIdentifier, projectIdentifier, identifier);

    if (!maskSecrets) {
      connectorDTO = decrypt(accountIdentifier, orgIdentifier, projectIdentifier, connectorDTO);
    }

    return SecretManagerConfigDTOMapper.fromConnectorDTO(
        accountIdentifier, connectorDTO, connectorDTO.getConnectorInfo().getConnectorConfig());
  }

  @Override
  public ConnectorDTO decrypt(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, ConnectorDTO connectorDTO) {
    if (HARNESS_SECRET_MANAGER_IDENTIFIER.equals(connectorDTO.getConnectorInfo().getIdentifier())
        || harnessManagedConnectorHelper.isHarnessManagedSecretManager(connectorDTO.getConnectorInfo())) {
      projectIdentifier = null;
      orgIdentifier = null;
      accountIdentifier = GLOBAL_ACCOUNT_ID;
      connectorDTO =
          getConnectorDTO(accountIdentifier, orgIdentifier, projectIdentifier, HARNESS_SECRET_MANAGER_IDENTIFIER);
    }
    ngEncryptorService.decryptEncryptionConfigSecrets(connectorDTO.getConnectorInfo().getConnectorConfig(),
        accountIdentifier, projectIdentifier, orgIdentifier, false);
    return connectorDTO;
  }

  @Override
  public ConnectorDTO getConnectorDTO(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    Optional<ConnectorResponseDTO> connectorResponseDTO = Optional.empty();
    final GitEntityInfo emptyInfo = GitEntityInfo.builder().build();
    final GitSyncBranchContext gitSyncBranchContext =
        GlobalContextManager.get(GitSyncBranchContext.NG_GIT_SYNC_CONTEXT);
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(emptyInfo).build());
      connectorResponseDTO = connectorService.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
      if (!connectorResponseDTO.isPresent()) {
        throw new NotFoundException(String.format("Connector with identifier [%s] in project [%s], org [%s] not found",
            identifier, projectIdentifier, orgIdentifier));
      }
    } finally {
      if (gitSyncBranchContext != null) {
        GlobalContextManager.upsertGlobalContextRecord(gitSyncBranchContext);
      }
    }

    ConnectorInfoDTO connectorInfoDTO = connectorResponseDTO.get().getConnector();
    return ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build();
  }

  @Override
  public void resolveSecretManagerScriptSecrets(String accountIdentifier, String path,
      CustomSecretNGManagerConfig encryptionConfig, SecretManagerConfigDTO secretManagerConfigDTO) {
    // use this path to replace secret var input
    CustomSecretManagerConfigDTO customNGSecretManagerConfigDTO = (CustomSecretManagerConfigDTO) secretManagerConfigDTO;
    Map<String, List<NameValuePairWithDefault>> inputValues = customSecretManagerHelper.mergeStringInInputValues(
        path, customNGSecretManagerConfigDTO.getTemplate().getTemplateInputs());
    customNGSecretManagerConfigDTO.getTemplate().setTemplateInputs(inputValues);
    // Preparing encrypted data for custom secret manager.
    Set<EncryptedDataParams> encryptedDataParamsSet =
        customSecretManagerHelper.prepareEncryptedDataParamsSet(customNGSecretManagerConfigDTO);
    encryptionConfig.setEncryptionType(CUSTOM_NG);
    CustomEncryptor customEncryptor = customEncryptorsRegistry.getCustomEncryptor(CUSTOM_NG);
    String script =
        customEncryptor.resolveSecretManagerConfig(accountIdentifier, encryptedDataParamsSet, encryptionConfig);
    encryptionConfig.setScript(script);
  }

  public String getPerpetualTaskId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    return connectorService.getHeartbeatPerpetualTaskId(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  public void resetHeartBeatTask(String accountId, String taskId) {
    connectorService.resetHeartBeatTask(accountId, taskId);
  }

  @Override
  public SecretManagerConfigDTO getLocalConfigDTO(String accountIdentifier) {
    return LocalConfigDTO.builder()
        .encryptionType(LOCAL)
        .identifier(HARNESS_SECRET_MANAGER_IDENTIFIER)
        .harnessManaged(true)
        .accountIdentifier(accountIdentifier)
        .isDefault(false)
        .build();
  }
}
