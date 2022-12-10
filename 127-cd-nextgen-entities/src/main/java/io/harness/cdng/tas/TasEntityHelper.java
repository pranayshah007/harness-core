/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.cdng.tas;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.TaskType.CF_COMMAND_TASK_NG;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.pcf.request.CfInfraMappingDataRequestNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class TasEntityHelper {
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject ExceptionManager exceptionManager;
  @VisibleForTesting static final int defaultTimeoutInSecs = 150;

  public List<EncryptedDataDetail> getEncryptionDataDetails(
      @Nonnull ConnectorInfoDTO connectorDTO, @Nonnull NGAccess ngAccess) {
    if (connectorDTO.getConnectorType() == ConnectorType.TAS) {
      TasConnectorDTO tasConnectorDTO = (TasConnectorDTO) connectorDTO.getConnectorConfig();
      List<DecryptableEntity> tasDecryptableEntities = tasConnectorDTO.getDecryptableEntities();
      if (isNotEmpty(tasDecryptableEntities)) {
        return secretManagerClientService.getEncryptionDetails(ngAccess, tasDecryptableEntities.get(0));
      } else {
        return emptyList();
      }
    }
    throw new UnsupportedOperationException(
        format("Unsupported connector type : [%s]", connectorDTO.getConnectorType()));
  }

  public ConnectorInfoDTO getConnectorInfoDTO(
      String connectorId, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(connectorId, accountIdentifier, orgIdentifier, projectIdentifier);
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(identifierRef.getAccountIdentifier(),
        identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
    if (connectorDTO.isPresent()) {
      return connectorDTO.get().getConnector();
    }
    throw new InvalidRequestException(format("Connector not found for identifier : [%s] ", connectorId), USER);
  }

  public TasInfraConfig getTasInfraConfig(InfrastructureOutcome infrastructureOutcome, NGAccess ngAccess) {
    ConnectorInfoDTO connectorDTO = getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(),
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    if (InfrastructureKind.TAS.equals(infrastructureOutcome.getKind())) {
      TanzuApplicationServiceInfrastructureOutcome tasInfrastructureOutcome =
          (TanzuApplicationServiceInfrastructureOutcome) infrastructureOutcome;
      return TasInfraConfig.builder()
          .encryptionDataDetails(getEncryptionDataDetails(connectorDTO, ngAccess))
          .organization(tasInfrastructureOutcome.getOrganization())
          .tasConnectorDTO((TasConnectorDTO) connectorDTO.getConnectorConfig())
          .space(tasInfrastructureOutcome.getSpace())
          .build();
    }
    throw new UnsupportedOperationException(
        format("Unsupported Infrastructure type: [%s]", infrastructureOutcome.getKind()));
  }

  public BaseNGAccess getBaseNGAccess(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }
  public DelegateResponseData executeSyncTask(TaskParameters params, BaseNGAccess ngAccess, String ifFailedMessage) {
    return getResponseData(null, ngAccess, params, Optional.empty());
  }
  public DelegateResponseData getResponseData(
      Ambiance ambiance, BaseNGAccess ngAccess, TaskParameters params, Optional<Integer> customTimeoutInSec) {
    Set<String> taskSelectors =
        ((CfInfraMappingDataRequestNG) params).getTasInfraConfig().getTasConnectorDTO().getDelegateSelectors();
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .executionTimeout(java.time.Duration.ofSeconds(customTimeoutInSec.orElse(defaultTimeoutInSecs)))
            .taskSetupAbstraction(SetupAbstractionKeys.orgIdentifier, ngAccess.getOrgIdentifier())
            .taskSetupAbstraction(SetupAbstractionKeys.ng, "true")
            .taskSetupAbstraction(
                SetupAbstractionKeys.owner, ngAccess.getOrgIdentifier() + "/" + ngAccess.getProjectIdentifier())
            .taskSetupAbstraction(SetupAbstractionKeys.projectIdentifier, ngAccess.getProjectIdentifier())
            .taskParameters(params)
            .taskType(CF_COMMAND_TASK_NG.name())
            .taskSelectors(taskSelectors)
            .logStreamingAbstractions(createLogStreamingAbstractions(ngAccess, ambiance))
            .build();
    try {
      return delegateGrpcClientWrapper.executeSyncTask(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    }
  }
  private LinkedHashMap<String, String> createLogStreamingAbstractions(BaseNGAccess ngAccess, Ambiance ambiance) {
    LinkedHashMap<String, String> logStreamingAbstractions = new LinkedHashMap<>();
    logStreamingAbstractions.put(SetupAbstractionKeys.accountId, ngAccess.getAccountIdentifier());
    logStreamingAbstractions.put(SetupAbstractionKeys.orgIdentifier, ngAccess.getOrgIdentifier());
    logStreamingAbstractions.put(SetupAbstractionKeys.projectIdentifier, ngAccess.getProjectIdentifier());
    if (ambiance != null) {
      logStreamingAbstractions.put(SetupAbstractionKeys.pipelineId, ambiance.getMetadata().getPipelineIdentifier());
      logStreamingAbstractions.put(
          SetupAbstractionKeys.runSequence, String.valueOf(ambiance.getMetadata().getRunSequence()));

      for (int i = 0; i < ambiance.getLevelsList().size(); i++) {
        logStreamingAbstractions.put("level" + i, ambiance.getLevels(i).getIdentifier());
      }
    }
    return logStreamingAbstractions;
  }
}
