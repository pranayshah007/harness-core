/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.spot;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.ssh.SshEntityHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.delegate.beans.connector.spotconnector.SpotCapabilityHelper;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.SpotDeploymentInfoDTO;
import io.harness.ng.core.BaseNGAccess;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.SpotinstAmiInstanceSyncPerpetualTaskParamsNg;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.InstanceSyncPerpetualTaskHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class SpotInstanceSyncPerpetualTaskHandler extends InstanceSyncPerpetualTaskHandler {
  @Inject private SshEntityHelper sshEntityHelper;
  @Inject private EncryptionHelper encryptionHelper;

  @Override
  public PerpetualTaskExecutionBundle getExecutionBundle(InfrastructureMappingDTO infrastructure,
      List<DeploymentInfoDTO> deploymentInfoDTOList, InfrastructureOutcome infrastructureOutcome) {
    BaseNGAccess access = BaseNGAccess.builder()
                              .accountIdentifier(infrastructure.getAccountIdentifier())
                              .orgIdentifier(infrastructure.getOrgIdentifier())
                              .projectIdentifier(infrastructure.getProjectIdentifier())
                              .build();

    ConnectorInfoDTO connectorInfoDTO = sshEntityHelper.getConnectorInfoDTO(infrastructureOutcome, access);
    SpotConnectorDTO spotConnectorDTO = (SpotConnectorDTO) connectorInfoDTO.getConnectorConfig();
    List<String> elastigroupIds = deploymentInfoDTOList.stream()
                                      .map(d -> (SpotDeploymentInfoDTO) d)
                                      .map(SpotDeploymentInfoDTO::getElastigroupId)
                                      .distinct()
                                      .collect(Collectors.toList());
    List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(access, spotConnectorDTO);

    SpotinstAmiInstanceSyncPerpetualTaskParamsNg spotinstAmiInstanceSyncPerpetualTaskParamsNg =
        SpotinstAmiInstanceSyncPerpetualTaskParamsNg.newBuilder()
            .setAccountId(infrastructure.getAccountIdentifier())
            .setInfrastructureKey(infrastructure.getInfrastructureKey())
            .setSpotinstConfig(ByteString.copyFrom(kryoSerializer.asBytes(spotConnectorDTO)))
            .setSpotinstEncryptedData(ByteString.copyFrom(kryoSerializer.asBytes(encryptionDetails)))
            .addAllElastigroupIds(elastigroupIds)
            .build();

    Any perpetualTaskPack = Any.pack(spotinstAmiInstanceSyncPerpetualTaskParamsNg);
    List<ExecutionCapability> executionCapabilities = getExecutionCapabilities(spotConnectorDTO);

    return createPerpetualTaskExecutionBundle(perpetualTaskPack, executionCapabilities,
        infrastructure.getOrgIdentifier(), infrastructure.getProjectIdentifier());
  }

  private List<ExecutionCapability> getExecutionCapabilities(SpotConnectorDTO spotConnectorDTO) {
    return SpotCapabilityHelper.fetchRequiredExecutionCapabilities(spotConnectorDTO, null);
  }

  private List<EncryptedDataDetail> getEncryptedDataDetails(BaseNGAccess access, SpotConnectorDTO spotConnectorDTO) {
    final List<DecryptableEntity> decryptableEntityList = spotConnectorDTO.getDecryptableEntities();
    DecryptableEntity decryptableEntity = null;
    if (isNotEmpty(decryptableEntityList)) {
      decryptableEntity = decryptableEntityList.get(0);
    }
    return encryptionHelper.getEncryptionDetail(
        decryptableEntity, access.getAccountIdentifier(), access.getOrgIdentifier(), access.getProjectIdentifier());
  }
}
