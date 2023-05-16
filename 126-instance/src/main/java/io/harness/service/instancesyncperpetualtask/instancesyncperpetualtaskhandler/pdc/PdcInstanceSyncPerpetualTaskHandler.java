/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.pdc;

import static io.harness.delegate.task.utils.PhysicalDataCenterUtils.extractHostnameFromHost;

import static java.lang.String.format;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SocketConnectivityBulkOrExecutionCapability;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.PdcDeploymentInfoDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.helper.InstanceSyncHelper;
import io.harness.ng.core.api.NGSecretServiceV2;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.ng.core.models.Secret;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.PdcPerpetualTaskParamsNg;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.InstanceSyncPerpetualTaskHandler;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Any;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class PdcInstanceSyncPerpetualTaskHandler extends InstanceSyncPerpetualTaskHandler {
  @Inject private NGSecretServiceV2 ngSecretServiceV2;
  @Inject private InstanceSyncHelper instanceSyncHelper;

  @Override
  public PerpetualTaskExecutionBundle getExecutionBundle(InfrastructureMappingDTO infrastructure,
      List<DeploymentInfoDTO> deploymentInfoDTOList, InfrastructureOutcome infrastructureOutcome) {
    PdcInfrastructureOutcome pdcInfrastructureOutcome = (PdcInfrastructureOutcome) infrastructureOutcome;

    int port = getPort(infrastructure, pdcInfrastructureOutcome);

    List<PdcDeploymentInfoDTO> pdcDeploymentInfoDTOs = (List<PdcDeploymentInfoDTO>) (List<?>) deploymentInfoDTOList;
    List<String> hosts = pdcDeploymentInfoDTOs.stream()
                             .map(pdcDeploymentInfoDTO -> extractHostnameFromHost(pdcDeploymentInfoDTO.getHost()))
                             .filter(Optional::isPresent)
                             .map(Optional::get)
                             .distinct()
                             .collect(Collectors.toList());

    PdcPerpetualTaskParamsNg PdcPerpetualTaskParamsNg =
        io.harness.perpetualtask.instancesync.PdcPerpetualTaskParamsNg.newBuilder()
            .setAccountId(infrastructure.getAccountIdentifier())
            .setServiceType(pdcDeploymentInfoDTOs.get(0).getType())
            .addAllHosts(hosts)
            .setPort(port)
            .setInfrastructureKey(infrastructure.getInfrastructureKey())
            .build();

    Any perpetualTaskPack = Any.pack(PdcPerpetualTaskParamsNg);
    List<ExecutionCapability> executionCapabilities = getExecutionCapabilities(hosts, port);

    return createPerpetualTaskExecutionBundle(perpetualTaskPack, executionCapabilities,
        infrastructure.getOrgIdentifier(), infrastructure.getProjectIdentifier());
  }

  private int getPort(InfrastructureMappingDTO infrastructure, PdcInfrastructureOutcome pdcInfrastructureOutcome) {
    Secret secret = findSecret(infrastructure.getAccountIdentifier(), infrastructure.getOrgIdentifier(),
        infrastructure.getProjectIdentifier(), pdcInfrastructureOutcome.getCredentialsRef());

    SecretSpecDTO secretSpecDTO = secret.getSecretSpec().toDTO();
    instanceSyncHelper.updateFeatureFlagForSsh(secretSpecDTO, infrastructure.getAccountIdentifier());
    int port;

    if (secretSpecDTO instanceof SSHKeySpecDTO) {
      port = ((SSHKeySpecDTO) secretSpecDTO).getPort();
    } else if (secretSpecDTO instanceof WinRmCredentialsSpecDTO) {
      port = ((WinRmCredentialsSpecDTO) secretSpecDTO).getPort();
    } else {
      log.error("SecretSpecDTO {} should be of SSH or Winrm", secretSpecDTO);
      throw new InvalidRequestException("Invalid SecretSpecDTO specified");
    }

    return port;
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
          PdcInstanceSyncPerpetualTaskHandler.class.getSimpleName()));
    }

    return secret.get();
  }

  private List<ExecutionCapability> getExecutionCapabilities(List<String> hosts, int port) {
    return singletonList(SocketConnectivityBulkOrExecutionCapability.builder().hostNames(hosts).port(port).build());
  }
}
