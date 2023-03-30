/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.azure;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAzureInfrastructureOutcome;
import io.harness.cdng.ssh.SshEntityHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.azureconnector.AzureCapabilityHelper;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.ssh.AzureInfraDelegateConfig;
import io.harness.delegate.task.ssh.AzureSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.AzureWinrmInfraDelegateConfig;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.AzureSshWinrmDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.api.NGSecretServiceV2;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.ng.core.models.Secret;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.AzureSshInstanceSyncPerpetualTaskParamsNg;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.InstanceSyncPerpetualTaskHandler;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDP)
public class AzureSshWinrmInstanceSyncPerpetualTaskHandler extends InstanceSyncPerpetualTaskHandler {
  @Inject private NGSecretServiceV2 ngSecretServiceV2;
  @Inject private SshEntityHelper sshEntityHelper;
  @Inject private AzureHelperService azureHelperService;

  @Override
  public PerpetualTaskExecutionBundle getExecutionBundle(InfrastructureMappingDTO infrastructure,
      List<DeploymentInfoDTO> deploymentInfoDTOList, InfrastructureOutcome infrastructureOutcome) {
    SshWinRmAzureInfrastructureOutcome azureInfrastructureOutcome =
        (SshWinRmAzureInfrastructureOutcome) infrastructureOutcome;

    Secret secret = findSecret(infrastructure.getAccountIdentifier(), infrastructure.getOrgIdentifier(),
        infrastructure.getProjectIdentifier(), azureInfrastructureOutcome.getCredentialsRef());

    List<AzureSshWinrmDeploymentInfoDTO> azureDeploymentInfoDTOs =
        (List<AzureSshWinrmDeploymentInfoDTO>) (List<?>) deploymentInfoDTOList;
    List<String> hosts = azureDeploymentInfoDTOs.stream()
                             .map(AzureSshWinrmDeploymentInfoDTO::getHost)
                             .filter(EmptyPredicate::isNotEmpty)
                             .distinct()
                             .collect(Collectors.toList());

    SecretSpecDTO secretSpecDTO = secret.getSecretSpec().toDTO();
    String serviceType = azureDeploymentInfoDTOs.get(0).getType();

    BaseNGAccess access = BaseNGAccess.builder()
                              .accountIdentifier(infrastructure.getAccountIdentifier())
                              .orgIdentifier(infrastructure.getOrgIdentifier())
                              .projectIdentifier(infrastructure.getProjectIdentifier())
                              .build();

    ConnectorInfoDTO connectorInfoDTO = sshEntityHelper.getConnectorInfoDTO(infrastructureOutcome, access);
    AzureConnectorDTO azureConnector = (AzureConnectorDTO) connectorInfoDTO.getConnectorConfig();
    List<EncryptedDataDetail> encryptedData = azureHelperService.getEncryptionDetails(azureConnector, access);

    AzureInfraDelegateConfig azureInfraDelegateConfig =
        getAzureInfraDelegateConfig(secretSpecDTO, azureConnector, encryptedData, azureInfrastructureOutcome);

    AzureSshInstanceSyncPerpetualTaskParamsNg azureInstanceSyncPerpetualTaskParamsNg =
        AzureSshInstanceSyncPerpetualTaskParamsNg.newBuilder()
            .setAccountId(infrastructure.getAccountIdentifier())
            .setServiceType(serviceType)
            .setInfrastructureKey(infrastructure.getInfrastructureKey())
            .addAllHosts(hosts)
            .setAzureSshWinrmInfraDelegateConfig(ByteString.copyFrom(
                getKryoSerializer(infrastructure.getAccountIdentifier()).asBytes(azureInfraDelegateConfig)))
            .build();

    Any perpetualTaskPack = Any.pack(azureInstanceSyncPerpetualTaskParamsNg);
    List<ExecutionCapability> executionCapabilities =
        getExecutionCapabilities(azureInfraDelegateConfig.getAzureConnectorDTO());

    return createPerpetualTaskExecutionBundle(perpetualTaskPack, executionCapabilities,
        infrastructure.getOrgIdentifier(), infrastructure.getProjectIdentifier(),
        infrastructure.getAccountIdentifier());
  }

  private List<ExecutionCapability> getExecutionCapabilities(AzureConnectorDTO azureConnectorDTO) {
    return AzureCapabilityHelper.fetchRequiredExecutionCapabilities(azureConnectorDTO, null);
  }

  private Secret findSecret(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String secretIdentifierWithScope) {
    SecretRefData secretRefData = SecretRefHelper.createSecretRef(secretIdentifierWithScope);
    IdentifierRef secretIdentifiers = IdentifierRefHelper.getIdentifierRef(
        secretIdentifierWithScope, accountIdentifier, orgIdentifier, projectIdentifier);
    Optional<Secret> secret = ngSecretServiceV2.get(secretIdentifiers.getAccountIdentifier(),
        secretIdentifiers.getOrgIdentifier(), secretIdentifiers.getProjectIdentifier(), secretRefData.getIdentifier());

    if (!secret.isPresent()) {
      throw new InvalidRequestException(format("Secret ref %s is missing for %s", secretIdentifierWithScope,
          AzureSshWinrmInstanceSyncPerpetualTaskHandler.class.getSimpleName()));
    }

    return secret.get();
  }

  private int getPort(SecretSpecDTO secretSpecDTO) {
    if (secretSpecDTO instanceof SSHKeySpecDTO) {
      return ((SSHKeySpecDTO) secretSpecDTO).getPort();
    } else if (secretSpecDTO instanceof WinRmCredentialsSpecDTO) {
      return ((WinRmCredentialsSpecDTO) secretSpecDTO).getPort();
    }

    throw new InvalidArgumentsException(format("Invalid subclass %s provided for %s",
        secretSpecDTO.getClass().getSimpleName(), SecretSpecDTO.class.getSimpleName()));
  }

  private AzureInfraDelegateConfig getAzureInfraDelegateConfig(SecretSpecDTO secretSpecDTO,
      AzureConnectorDTO azureConnector, List<EncryptedDataDetail> encryptedData,
      SshWinRmAzureInfrastructureOutcome azureInfrastructureOutcome) {
    if (secretSpecDTO instanceof SSHKeySpecDTO) {
      return AzureSshInfraDelegateConfig.sshAzureBuilder()
          .sshKeySpecDto((SSHKeySpecDTO) secretSpecDTO)
          .azureConnectorDTO(azureConnector)
          .connectorEncryptionDataDetails(encryptedData)
          .resourceGroup(azureInfrastructureOutcome.getResourceGroup())
          .subscriptionId(azureInfrastructureOutcome.getSubscriptionId())
          .tags(sshEntityHelper.filterInfraTags(azureInfrastructureOutcome.getTags()))
          .hostConnectionType(azureInfrastructureOutcome.getHostConnectionType())
          .build();
    } else if (secretSpecDTO instanceof WinRmCredentialsSpecDTO) {
      return AzureWinrmInfraDelegateConfig.winrmAzureBuilder()
          .winRmCredentials((WinRmCredentialsSpecDTO) secretSpecDTO)
          .azureConnectorDTO(azureConnector)
          .connectorEncryptionDataDetails(encryptedData)
          .resourceGroup(azureInfrastructureOutcome.getResourceGroup())
          .subscriptionId(azureInfrastructureOutcome.getSubscriptionId())
          .tags(sshEntityHelper.filterInfraTags(azureInfrastructureOutcome.getTags()))
          .hostConnectionType(azureInfrastructureOutcome.getHostConnectionType())
          .build();
    }

    throw new InvalidArgumentsException(format("Invalid subclass %s provided for %s",
        secretSpecDTO.getClass().getSimpleName(), SecretSpecDTO.class.getSimpleName()));
  }
}
