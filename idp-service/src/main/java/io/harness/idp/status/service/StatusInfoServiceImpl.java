/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.status.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.status.beans.StatusInfoEntity;
import io.harness.idp.status.enums.StatusType;
import io.harness.idp.status.k8s.HealthCheck;
import io.harness.idp.status.mappers.StatusInfoMapper;
import io.harness.idp.status.repositories.StatusInfoRepository;
import io.harness.spec.server.idp.v1.model.StatusInfo;
import io.harness.spec.server.idp.v1.model.StatusInfoV2;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class StatusInfoServiceImpl implements StatusInfoService {
  private StatusInfoRepository statusInfoRepository;
  private HealthCheck healthCheck;

  @Override
  public Optional<StatusInfo> findByAccountIdentifierAndType(String accountIdentifier, String type) {
    if (StatusType.INFRA.toString().equalsIgnoreCase(type)) {
      return healthCheck.getCurrentStatus(accountIdentifier);
    }
    Optional<StatusInfoEntity> statusEntity =
        statusInfoRepository.findByAccountIdentifierAndType(accountIdentifier, type.toUpperCase());
    if (statusEntity.isEmpty()) {
      statusEntity =
          Optional.ofNullable(StatusInfoEntity.builder().status(StatusInfo.CurrentStatusEnum.NOT_FOUND).build());
    }
    return statusEntity.map(StatusInfoMapper::toDTO);
  }

  @Override
  public StatusInfoV2 findByAccountIdentifierAndTypeV2(String accountIdentifier, String type) {
    StatusInfoV2 statusInfoV2 = new StatusInfoV2();
    switch (StatusType.valueOf(type.toUpperCase())) {
      case INFRA:
        statusInfoV2.put(StatusType.INFRA.toString().toLowerCase(), getStatusInfoForInfra(accountIdentifier));
        break;
      case ONBOARDING:
        statusInfoV2.put(
            StatusType.ONBOARDING.toString().toLowerCase(), getStatusInfoForOnboarding(accountIdentifier, type));
        break;
      case INFRA_ONBOARDING:
        statusInfoV2.put(StatusType.INFRA.toString().toLowerCase(), getStatusInfoForInfra(accountIdentifier));
        statusInfoV2.put(StatusType.ONBOARDING.toString().toLowerCase(),
            getStatusInfoForOnboarding(accountIdentifier, StatusType.ONBOARDING.toString().toUpperCase()));
        break;
      default:
        return null;
    }
    return statusInfoV2;
  }

  private StatusInfo getStatusInfoForInfra(String accountIdentifier) {
    return healthCheck.getCurrentStatus(accountIdentifier).get();
  }

  private StatusInfo getStatusInfoForOnboarding(String accountIdentifier, String type) {
    Optional<StatusInfoEntity> statusEntity =
        statusInfoRepository.findByAccountIdentifierAndType(accountIdentifier, type.toUpperCase());
    if (statusEntity.isEmpty()) {
      statusEntity =
          Optional.ofNullable(StatusInfoEntity.builder().status(StatusInfo.CurrentStatusEnum.NOT_FOUND).build());
    }
    return StatusInfoMapper.toDTO(statusEntity.get());
  }

  @Override
  public StatusInfo save(StatusInfo statusInfo, String accountIdentifier, String type) {
    StatusInfoEntity statusInfoEntity = StatusInfoMapper.fromDTO(statusInfo, accountIdentifier, type);
    return StatusInfoMapper.toDTO(statusInfoRepository.saveOrUpdate(statusInfoEntity));
  }
}
