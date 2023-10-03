/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secretusage;

import io.harness.beans.IdentifierRef;
import io.harness.events.SecretRuntimeUsageEventProducer;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.EntityUsageDetailProto;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entityactivity.EntityActivityCreateDTO;
import io.harness.ng.core.activityhistory.NGActivityStatus;
import io.harness.ng.core.activityhistory.NGActivityType;
import io.harness.ng.core.dto.secrets.SecretDTOV2;

import com.google.inject.Inject;

public class SecretRuntimeUsageServiceImpl implements SecretRuntimeUsageService {
  private final SecretRuntimeUsageEventProducer secretRuntimeUsageEventProducer;

  @Inject
  SecretRuntimeUsageServiceImpl(SecretRuntimeUsageEventProducer secretRuntimeUsageEventProducer) {
    this.secretRuntimeUsageEventProducer = secretRuntimeUsageEventProducer;
  }

  @Override
  public void createSecretRuntimeUsage(
      IdentifierRef secretIdentifierRef, EntityDetailProtoDTO referredByEntity, EntityUsageDetailProto usageDetail) {
    IdentifierRefProtoDTO identifierRefProtoDTO = IdentifierRefProtoDTOHelper.fromIdentifierRef(secretIdentifierRef);
    EntityActivityCreateDTO entityActivityCreateDTO = createRuntimeUsageDTOForSecret(
        secretIdentifierRef.getAccountIdentifier(), identifierRefProtoDTO, referredByEntity, usageDetail);
    secretRuntimeUsageEventProducer.publishEvent(
        secretIdentifierRef.getAccountIdentifier(), secretIdentifierRef.getIdentifier(), entityActivityCreateDTO);
  }

  @Override
  public void createSecretRuntimeUsage(String accountIdentifier, SecretDTOV2 secretDTOV2,
      EntityDetailProtoDTO referredByEntity, EntityUsageDetailProto usageDetail) {
    IdentifierRefProtoDTO identifierRefProtoDTO =
        IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(accountIdentifier, secretDTOV2.getOrgIdentifier(),
            secretDTOV2.getProjectIdentifier(), secretDTOV2.getIdentifier());
    EntityActivityCreateDTO entityActivityCreateDTO =
        createRuntimeUsageDTOForSecret(accountIdentifier, identifierRefProtoDTO, referredByEntity, usageDetail);
    secretRuntimeUsageEventProducer.publishEvent(
        accountIdentifier, secretDTOV2.getIdentifier(), entityActivityCreateDTO);
  }

  private EntityActivityCreateDTO createRuntimeUsageDTOForSecret(String accountIdentifier,
      IdentifierRefProtoDTO identifierRefProtoDTO, EntityDetailProtoDTO referredByEntity,
      EntityUsageDetailProto usageDetail) {
    return EntityActivityCreateDTO.newBuilder()
        .setType(NGActivityType.ENTITY_USAGE.toString())
        .setStatus(NGActivityStatus.SUCCESS.toString())
        .setActivityTime(System.currentTimeMillis())
        .setAccountIdentifier(accountIdentifier)
        .setReferredEntity(EntityDetailProtoDTO.newBuilder()
                               .setType(EntityTypeProtoEnum.SECRETS)
                               .setIdentifierRef(identifierRefProtoDTO)
                               .build())
        .setEntityUsageDetail(EntityActivityCreateDTO.EntityUsageActivityDetailProtoDTO.newBuilder()
                                  .setReferredByEntity(referredByEntity)
                                  .setUsageDetail(usageDetail)
                                  .build())
        .build();
  }
}
