/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.ng.core.infrastructure.InfrastructureKind.PDC;
import static io.harness.ng.core.infrastructure.InfrastructureKind.SSH_WINRM_AZURE;
import static io.harness.pms.yaml.YamlNode.UUID_FIELD_NAME;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryGenericArtifactOutcome;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAzureInfrastructureOutcome;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.services.NGHostService;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.pdcconnector.HostDTO;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterDTO;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterType;
import io.harness.delegate.beans.connector.pdcconnector.PhysicalDataCenterConnectorDTO;
import io.harness.delegate.task.ssh.AzureSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.AzureWinrmInfraDelegateConfig;
import io.harness.delegate.task.ssh.PdcSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.PdcWinRmInfraDelegateConfig;
import io.harness.delegate.task.ssh.SshInfraDelegateConfig;
import io.harness.delegate.task.ssh.WinRmInfraDelegateConfig;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.secretmanagerclient.services.SshKeySpecDTOHelper;
import io.harness.secretmanagerclient.services.WinRmCredentialsSpecDTOHelper;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import org.springframework.data.domain.Page;

@Singleton
@OwnedBy(CDP)
public class SshEntityHelper {
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Inject @Named("PRIVILEGED") private SecretNGManagerClient secretManagerClient;
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
  @Inject private SshKeySpecDTOHelper sshKeySpecDTOHelper;
  @Inject private WinRmCredentialsSpecDTOHelper winRmCredentialsSpecDTOHelper;
  @Inject private NGHostService ngHostService;
  @Inject private AzureHelperService azureHelperService;

  private static final int BATCH_SIZE = 100;

  public SshInfraDelegateConfig getSshInfraDelegateConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    ConnectorInfoDTO connectorDTO = null;
    SSHKeySpecDTO sshKeySpecDto = null;
    switch (infrastructure.getKind()) {
      case PDC:
        PdcInfrastructureOutcome pdcDirectInfrastructure = (PdcInfrastructureOutcome) infrastructure;
        connectorDTO = getConnectorInfoDTO(infrastructure, ngAccess);
        PhysicalDataCenterConnectorDTO pdcConnectorDTO =
            (connectorDTO != null) ? (PhysicalDataCenterConnectorDTO) connectorDTO.getConnectorConfig() : null;
        sshKeySpecDto = getSshKeySpecDto(pdcDirectInfrastructure.getCredentialsRef(), ambiance);
        List<String> hosts = extractHostNames(pdcDirectInfrastructure, pdcConnectorDTO, ngAccess);
        return PdcSshInfraDelegateConfig.builder()
            .hosts(hosts)
            .physicalDataCenterConnectorDTO(pdcConnectorDTO)
            .sshKeySpecDto(sshKeySpecDto)
            .encryptionDataDetails(sshKeySpecDTOHelper.getSSHKeyEncryptionDetails(sshKeySpecDto, ngAccess))
            .build();

      case SSH_WINRM_AZURE:
        SshWinRmAzureInfrastructureOutcome azureInfrastructureOutcome =
            (SshWinRmAzureInfrastructureOutcome) infrastructure;
        connectorDTO = getConnectorInfoDTO(infrastructure, ngAccess);
        AzureConnectorDTO azureConnectorDTO = (AzureConnectorDTO) connectorDTO.getConnectorConfig();
        List<EncryptedDataDetail> encryptionDetails =
            azureHelperService.getEncryptionDetails(azureConnectorDTO, ngAccess);
        sshKeySpecDto = getSshKeySpecDto(azureInfrastructureOutcome.getCredentialsRef(), ambiance);
        return AzureSshInfraDelegateConfig.sshAzureBuilder()
            .azureConnectorDTO(azureConnectorDTO)
            .connectorEncryptionDataDetails(encryptionDetails)
            .sshKeySpecDto(sshKeySpecDto)
            .encryptionDataDetails(sshKeySpecDTOHelper.getSSHKeyEncryptionDetails(sshKeySpecDto, ngAccess))
            .subscriptionId(azureInfrastructureOutcome.getSubscriptionId())
            .resourceGroup(azureInfrastructureOutcome.getResourceGroup())
            .tags(filterInfraTags(azureInfrastructureOutcome.getTags()))
            .build();
      default:
        throw new UnsupportedOperationException(
            format("Unsupported Infrastructure type: [%s]", infrastructure.getKind()));
    }
  }

  public WinRmInfraDelegateConfig getWinRmInfraDelegateConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    ConnectorInfoDTO connectorDTO = null;
    WinRmCredentialsSpecDTO winRmCredentials = null;
    switch (infrastructure.getKind()) {
      case PDC:
        PdcInfrastructureOutcome pdcDirectInfrastructure = (PdcInfrastructureOutcome) infrastructure;
        connectorDTO = getConnectorInfoDTO(infrastructure, ngAccess);
        PhysicalDataCenterConnectorDTO pdcConnectorDTO =
            (connectorDTO != null) ? (PhysicalDataCenterConnectorDTO) connectorDTO.getConnectorConfig() : null;
        winRmCredentials = getWinRmCredentials(pdcDirectInfrastructure.getCredentialsRef(), ambiance);
        List<String> hosts = extractHostNames(pdcDirectInfrastructure, pdcConnectorDTO, ngAccess);
        return PdcWinRmInfraDelegateConfig.builder()
            .hosts(hosts)
            .physicalDataCenterConnectorDTO(pdcConnectorDTO)
            .winRmCredentials(winRmCredentials)
            .encryptionDataDetails(winRmCredentialsSpecDTOHelper.getWinRmEncryptionDetails(winRmCredentials, ngAccess))
            .build();
      case SSH_WINRM_AZURE:
        SshWinRmAzureInfrastructureOutcome azureInfrastructureOutcome =
            (SshWinRmAzureInfrastructureOutcome) infrastructure;
        connectorDTO = getConnectorInfoDTO(infrastructure, ngAccess);
        AzureConnectorDTO azureConnectorDTO = (AzureConnectorDTO) connectorDTO.getConnectorConfig();
        List<EncryptedDataDetail> encryptionDetails =
            azureHelperService.getEncryptionDetails(azureConnectorDTO, ngAccess);
        winRmCredentials = getWinRmCredentials(azureInfrastructureOutcome.getCredentialsRef(), ambiance);
        return AzureWinrmInfraDelegateConfig.winrmAzureBuilder()
            .azureConnectorDTO(azureConnectorDTO)
            .connectorEncryptionDataDetails(encryptionDetails)
            .winRmCredentials(winRmCredentials)
            .encryptionDataDetails(winRmCredentialsSpecDTOHelper.getWinRmEncryptionDetails(winRmCredentials, ngAccess))
            .subscriptionId(azureInfrastructureOutcome.getSubscriptionId())
            .resourceGroup(azureInfrastructureOutcome.getResourceGroup())
            .tags(filterInfraTags(azureInfrastructureOutcome.getTags()))
            .build();
      default:
        throw new UnsupportedOperationException(
            format("Unsupported Infrastructure type: [%s]", infrastructure.getKind()));
    }
  }

  private Map<String, String> filterInfraTags(Map<String, String> infraTags) {
    if (isEmpty(infraTags)) {
      return infraTags;
    }

    return infraTags.entrySet()
        .stream()
        .filter(entry -> !UUID_FIELD_NAME.equals(entry.getKey()))
        .collect(Collectors.toMap(map -> map.getKey(), map -> map.getValue()));
  }

  private List<String> extractHostNames(PdcInfrastructureOutcome pdcDirectInfrastructure,
      PhysicalDataCenterConnectorDTO pdcConnectorDTO, NGAccess ngAccess) {
    return pdcDirectInfrastructure.useInfrastructureHosts()
        ? pdcDirectInfrastructure.getHosts()
        : toStringHostNames(extractConnectorHostNames(pdcDirectInfrastructure, pdcConnectorDTO.getHosts(), ngAccess));
  }

  private List<HostDTO> extractConnectorHostNames(
      PdcInfrastructureOutcome pdcDirectInfrastructure, List<HostDTO> hosts, NGAccess ngAccess) {
    if (isEmpty(hosts)) {
      return emptyList();
    }

    if (isNotEmpty(pdcDirectInfrastructure.getHostFilters())) {
      // filter hosts based on host names
      List<List<HostDTO>> batches = Lists.partition(hosts, BATCH_SIZE);
      return IntStream.range(0, batches.size())
          .mapToObj(
              index -> filterConnectorHostsByHostName(ngAccess, pdcDirectInfrastructure, batches.get(index), index))
          .flatMap(Collection::stream)
          .collect(Collectors.toList());
    }

    if (isNotEmpty(pdcDirectInfrastructure.getAttributeFilters())) {
      // filter hosts based on host attributes
      List<List<HostDTO>> batches = Lists.partition(hosts, BATCH_SIZE);
      return IntStream.range(0, batches.size())
          .mapToObj(
              index -> filterConnectorHostsByAttributes(ngAccess, pdcDirectInfrastructure, batches.get(index), index))
          .flatMap(Collection::stream)
          .collect(Collectors.toList());
    }

    return hosts;
  }

  private List<HostDTO> filterConnectorHostsByAttributes(
      NGAccess ngAccess, PdcInfrastructureOutcome pdcDirectInfrastructure, List<HostDTO> batch, int currentPageIndex) {
    PageRequest pageRequest = PageRequest.builder().pageIndex(currentPageIndex).pageSize(batch.size()).build();
    Page<HostDTO> result = ngHostService.filterHostsByConnector(ngAccess.getAccountIdentifier(),
        ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier(), pdcDirectInfrastructure.getConnectorRef(),
        HostFilterDTO.builder()
            .type(HostFilterType.HOST_ATTRIBUTES)
            .filter(pdcDirectInfrastructure.getAttributeFilters()
                        .entrySet()
                        .stream()
                        .filter(e -> !YamlTypes.UUID.equals(e.getKey()))
                        .map(e -> e.getKey() + ":" + e.getValue())
                        .collect(joining(",")))
            .build(),
        pageRequest);
    return result.getContent();
  }

  private List<HostDTO> filterConnectorHostsByHostName(
      NGAccess ngAccess, PdcInfrastructureOutcome pdcDirectInfrastructure, List<HostDTO> batch, int currentPageIndex) {
    PageRequest pageRequest = PageRequest.builder().pageIndex(currentPageIndex).pageSize(batch.size()).build();
    Page<HostDTO> result = ngHostService.filterHostsByConnector(ngAccess.getAccountIdentifier(),
        ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier(), pdcDirectInfrastructure.getConnectorRef(),
        HostFilterDTO.builder()
            .type(HostFilterType.HOST_NAMES)
            .filter(pdcDirectInfrastructure.getHostFilters().stream().collect(joining(",")))
            .build(),
        pageRequest);
    return result.getContent();
  }

  private List<String> toStringHostNames(List<HostDTO> hosts) {
    return hosts.stream().map(host -> host.getHostName()).collect(Collectors.toList());
  }

  private SSHKeySpecDTO getSshKeySpecDto(String credentialsRef, Ambiance ambiance) {
    String sshKeyRef = credentialsRef;
    if (isEmpty(sshKeyRef)) {
      throw new InvalidRequestException("Missing SSH key for configured host(s)");
    }
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(sshKeyRef, AmbianceUtils.getAccountId(ambiance),
        AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
    String errorMSg = "No secret configured with identifier: " + sshKeyRef;
    SecretResponseWrapper secretResponseWrapper = NGRestUtils.getResponse(
        secretManagerClient.getSecret(identifierRef.getIdentifier(), identifierRef.getAccountIdentifier(),
            identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier()),
        errorMSg);
    if (secretResponseWrapper == null) {
      throw new InvalidRequestException(errorMSg);
    }
    SecretDTOV2 secret = secretResponseWrapper.getSecret();

    return (SSHKeySpecDTO) secret.getSpec();
  }

  private WinRmCredentialsSpecDTO getWinRmCredentials(String credentialsRef, Ambiance ambiance) {
    String winRmCredentialsRef = credentialsRef;
    if (isEmpty(winRmCredentialsRef)) {
      throw new InvalidRequestException("Missing WinRm credentials for configured host(s)");
    }
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(winRmCredentialsRef, AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
    String errorMSg = "No secret configured with identifier: " + winRmCredentialsRef;
    SecretResponseWrapper secretResponseWrapper = NGRestUtils.getResponse(
        secretManagerClient.getSecret(identifierRef.getIdentifier(), identifierRef.getAccountIdentifier(),
            identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier()),
        errorMSg);
    if (secretResponseWrapper == null) {
      throw new InvalidRequestException(errorMSg);
    }
    SecretDTOV2 secret = secretResponseWrapper.getSecret();

    return (WinRmCredentialsSpecDTO) secret.getSpec();
  }

  public ConnectorInfoDTO getConnectorInfoDTO(InfrastructureOutcome infrastructureOutcome, NGAccess ngAccess) {
    if (InfrastructureKind.PDC.equals(infrastructureOutcome.getKind())
        && Objects.isNull(infrastructureOutcome.getConnectorRef())) {
      return null;
    }

    return getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(), ngAccess);
  }

  private ConnectorInfoDTO getConnectorInfoDTO(String connectorId, NGAccess ngAccess) {
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        connectorId, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(identifierRef.getAccountIdentifier(),
        identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
    if (!connectorDTO.isPresent()) {
      throw new InvalidRequestException(format("Connector not found for identifier : [%s]", connectorId), USER);
    }
    return connectorDTO.get().getConnector();
  }

  public SshWinRmArtifactDelegateConfig getArtifactDelegateConfigConfig(
      ArtifactOutcome artifactOutcome, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    ConnectorInfoDTO connectorDTO;
    if (artifactOutcome instanceof ArtifactoryGenericArtifactOutcome) {
      ArtifactoryGenericArtifactOutcome artifactoryGenericArtifactOutcome =
          (ArtifactoryGenericArtifactOutcome) artifactOutcome;
      connectorDTO = getConnectorInfoDTO(artifactoryGenericArtifactOutcome.getConnectorRef(), ngAccess);
      return ArtifactoryArtifactDelegateConfig.builder()
          .repositoryName(artifactoryGenericArtifactOutcome.getRepositoryName())
          .identifier(artifactoryGenericArtifactOutcome.getIdentifier())
          .connectorDTO(connectorDTO)
          .encryptedDataDetails(getArtifactEncryptionDataDetails(connectorDTO, ngAccess))
          .artifactDirectory(artifactoryGenericArtifactOutcome.getArtifactDirectory())
          .artifactPath(artifactoryGenericArtifactOutcome.getArtifactPath())
          .repositoryFormat(artifactoryGenericArtifactOutcome.getRepositoryFormat())
          .build();
    } else {
      throw new UnsupportedOperationException(
          format("Unsupported Artifact type: [%s]", artifactOutcome.getArtifactType()));
    }
  }

  public List<EncryptedDataDetail> getArtifactEncryptionDataDetails(
      @Nonnull ConnectorInfoDTO connectorDTO, @Nonnull NGAccess ngAccess) {
    switch (connectorDTO.getConnectorType()) {
      case ARTIFACTORY:
        ArtifactoryConnectorDTO artifactoryConnectorDTO = (ArtifactoryConnectorDTO) connectorDTO.getConnectorConfig();
        List<DecryptableEntity> artifactoryDecryptableEntities = artifactoryConnectorDTO.getDecryptableEntities();
        if (isNotEmpty(artifactoryDecryptableEntities)) {
          return secretManagerClientService.getEncryptionDetails(
              ngAccess, artifactoryConnectorDTO.getAuth().getCredentials());
        } else {
          return emptyList();
        }
      default:
        throw new UnsupportedOperationException(
            format("Unsupported connector type : [%s]", connectorDTO.getConnectorType()));
    }
  }
}
