/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entitysetupusage.mapper;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.ng.core.entitysetupusage.dto.SetupUsageDetailType.ENTITY_REFERRED_BY_INFRA;
import static io.harness.ng.core.entitysetupusage.dto.SetupUsageDetailType.ENTITY_REFERRED_BY_OVERRIDES;
import static io.harness.ng.core.entitysetupusage.dto.SetupUsageDetailType.SECRET_REFERRED_BY_CONNECTOR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entitysetupusage.EntityDetailWithSetupUsageDetailProtoDTO;
import io.harness.ng.core.entitysetupusage.dto.EntityReferredByInfraSetupUsageDetail;
import io.harness.ng.core.entitysetupusage.dto.EntityReferredByOverridesSetupUsageDetail;
import io.harness.ng.core.entitysetupusage.dto.EntityReferredByPipelineSetupUsageDetail;
import io.harness.ng.core.entitysetupusage.dto.SecretReferredByConnectorSetupUsageDetail;
import io.harness.ng.core.entitysetupusage.dto.SetupUsageDetail;
import io.harness.ng.core.entitysetupusage.dto.SetupUsageDetailType;

import com.google.inject.Singleton;

@OwnedBy(DX)
@Singleton
public class SetupUsageDetailProtoToRestMapper {
  public SetupUsageDetail toRestDTO(EntityDetailWithSetupUsageDetailProtoDTO setupUsageDetailProtoDTO) {
    if (setupUsageDetailProtoDTO == null) {
      return null;
    }
    if (SECRET_REFERRED_BY_CONNECTOR.toString().equals(setupUsageDetailProtoDTO.getType())) {
      return SecretReferredByConnectorSetupUsageDetail.builder()
          .fieldName(setupUsageDetailProtoDTO.getSecretConnectorDetail().getFieldName())
          .build();
    }
    if (ENTITY_REFERRED_BY_INFRA.toString().equals(setupUsageDetailProtoDTO.getType())) {
      return EntityReferredByInfraSetupUsageDetail.builder()
          .environmentIdentifier(setupUsageDetailProtoDTO.getEntityInInfraDetail().getEnvironmentIdentifier())
          .environmentName(setupUsageDetailProtoDTO.getEntityInInfraDetail().getEnvironmentName())
          .build();
    }

    if (ENTITY_REFERRED_BY_OVERRIDES.toString().equals(setupUsageDetailProtoDTO.getType())) {
      return EntityReferredByOverridesSetupUsageDetail.builder()
          .identifier(setupUsageDetailProtoDTO.getOverridesDetail().getIdentifier())
          .overrideType(setupUsageDetailProtoDTO.getOverridesDetail().getOverrideType())
          .accountId(setupUsageDetailProtoDTO.getOverridesDetail().getAccountId())
          .orgId(setupUsageDetailProtoDTO.getOverridesDetail().getOrgId())
          .projectId(setupUsageDetailProtoDTO.getOverridesDetail().getProjectId())
          .build();
    }

    if (SetupUsageDetailType.isReferredByPipeline(setupUsageDetailProtoDTO.getType())) {
      return EntityReferredByPipelineSetupUsageDetail.builder()
          .identifier(setupUsageDetailProtoDTO.getEntityInPipelineDetail().getIdentifier())
          .referenceType(String.valueOf(setupUsageDetailProtoDTO.getEntityInPipelineDetail().getType()))
          .build();
    }
    return null;
  }
}
