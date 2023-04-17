/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.NGConstants.ENTITY_REFERENCE_LOG_PREFIX;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.entitysetupusage.dto.SetupUsageDetailType.SECRET_REFERRED_BY_SECRET;
import static io.harness.secrets.SecretReferenceUtils.getAllSecretFQNs;

import static software.wings.utils.Utils.emptyIfNull;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.connector.impl.SecretRefInputValidationHelper;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.entitysetupusageclient.EntitySetupUsageHelper;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.DeleteSetupUsageDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntityDetailWithSetupUsageDetailProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.exception.ReferencedEntityException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@Singleton
public class SecretEntityReferenceHelper {
  private static final String ACCOUNT_ID = "accountId";
  EntitySetupUsageHelper entityReferenceHelper;
  EntitySetupUsageService entitySetupUsageService;
  Producer eventProducer;
  IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;
  SecretRefInputValidationHelper secretRefInputValidationHelper;

  @Inject
  public SecretEntityReferenceHelper(EntitySetupUsageHelper entityReferenceHelper,
      EntitySetupUsageService entitySetupUsageService,
      @Named(EventsFrameworkConstants.SETUP_USAGE) Producer eventProducer,
      IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper,
      SecretRefInputValidationHelper secretRefInputValidationHelper) {
    this.entityReferenceHelper = entityReferenceHelper;
    this.entitySetupUsageService = entitySetupUsageService;
    this.eventProducer = eventProducer;
    this.identifierRefProtoDTOHelper = identifierRefProtoDTOHelper;
    this.secretRefInputValidationHelper = secretRefInputValidationHelper;
  }

  public void createSetupUsageForSecretManager(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String secretIdentifier, String secretName, String secretManagerIdentifier) {
    String secretMangerFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, secretManagerIdentifier);
    String secretFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, secretIdentifier);
    IdentifierRefProtoDTO secretReference = identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
        accountIdentifier, orgIdentifier, projectIdentifier, secretIdentifier);

    IdentifierRefProtoDTO secretManagerReference = identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
        accountIdentifier, orgIdentifier, projectIdentifier, secretManagerIdentifier);

    EntityDetailProtoDTO secretDetails = EntityDetailProtoDTO.newBuilder()
                                             .setIdentifierRef(secretReference)
                                             .setType(EntityTypeProtoEnum.SECRETS)
                                             .setName(emptyIfNull(secretName))
                                             .build();

    EntityDetailProtoDTO secretManagerDetails = EntityDetailProtoDTO.newBuilder()
                                                    .setIdentifierRef(secretManagerReference)
                                                    .setType(EntityTypeProtoEnum.CONNECTORS)
                                                    .build();
    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(accountIdentifier)
                                                         .setReferredByEntity(secretDetails)
                                                         .addReferredEntities(secretManagerDetails)
                                                         .setDeleteOldReferredByRecords(false)
                                                         .build();
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier,
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.CONNECTORS.name(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    } catch (Exception ex) {
      log.info(ENTITY_REFERENCE_LOG_PREFIX
              + "The entity reference was not created when the secret [{}] was created from the secret manager [{}]",
          secretFQN, secretMangerFQN);
    }
  }

  /**
   * This method is responsible to create setup usage for secrets used as reference in another secret. For e.g. secrets
   * referenced in SSH or WinRm type of secret.
   * @param accountIdentifier
   * @param secretDTOV2 A secret that refernces other secret e.g. SSH secret.
   */
  public void createSetupUsageForSecret(String accountIdentifier, SecretDTOV2 secretDTOV2) {
    String secretFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(accountIdentifier,
        secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier(), secretDTOV2.getIdentifier());
    Optional<List<DecryptableEntity>> decryptableEntities = secretDTOV2.getSpec().getDecryptableEntities();
    if (decryptableEntities.isEmpty()) {
      produceSetupUsageForSecretEvent(accountIdentifier, secretDTOV2, secretFQN, new ArrayList<>(), new ArrayList<>());
    } else {
      Map<String, SecretRefData> secrets =
          secretRefInputValidationHelper.getDecryptableFieldsData(decryptableEntities.get());
      NGAccess baseNGAccess = BaseNGAccess.builder()
                                  .accountIdentifier(accountIdentifier)
                                  .orgIdentifier(secretDTOV2.getOrgIdentifier())
                                  .projectIdentifier(secretDTOV2.getProjectIdentifier())
                                  .build();
      List<String> referredSecretFQNs = getAllSecretFQNs(secrets, baseNGAccess);
      List<EntityDetailWithSetupUsageDetailProtoDTO> allSecretDetails =
          getAllReferredSecretDetails(secrets, baseNGAccess);
      produceSetupUsageForSecretEvent(accountIdentifier, secretDTOV2, secretFQN, referredSecretFQNs, allSecretDetails);
    }
  }

  private void produceSetupUsageForSecretEvent(String accountIdentifier, SecretDTOV2 secretDTOV2, String secretFQN,
      List<String> referredSecretFQNs, List<EntityDetailWithSetupUsageDetailProtoDTO> allSecretDetails) {
    EntitySetupUsageCreateV2DTO entityReferenceDTO =
        createSetupUsageDTOForSecret(secretDTOV2, allSecretDetails, accountIdentifier);
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of(ACCOUNT_ID, accountIdentifier,
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.SECRETS.name(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    } catch (Exception ex) {
      String message = String.format(ENTITY_REFERENCE_LOG_PREFIX
              + "Entity reference was not updated for the secret [%s] using the secret [%s] with the exception[%s]",
          secretFQN, referredSecretFQNs, ex.getMessage());
      log.info(message, ex);
    }
  }

  private EntitySetupUsageCreateV2DTO createSetupUsageDTOForSecret(
      SecretDTOV2 secretDTOV2, List<EntityDetailWithSetupUsageDetailProtoDTO> secretDetails, String accountIdentifier) {
    IdentifierRefProtoDTO secretReference = identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(accountIdentifier,
        secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier(), secretDTOV2.getIdentifier());
    EntityDetailProtoDTO referredBySecretDetails = EntityDetailProtoDTO.newBuilder()
                                                       .setIdentifierRef(secretReference)
                                                       .setType(EntityTypeProtoEnum.SECRETS)
                                                       .setName(secretDTOV2.getName())
                                                       .build();

    return EntitySetupUsageCreateV2DTO.newBuilder()
        .setAccountIdentifier(accountIdentifier)
        .setReferredByEntity(referredBySecretDetails)
        .addAllReferredEntityWithSetupUsageDetail(secretDetails)
        .setDeleteOldReferredByRecords(true)
        .build();
  }

  private List<EntityDetailWithSetupUsageDetailProtoDTO> getAllReferredSecretDetails(
      Map<String, SecretRefData> secrets, NGAccess baseNGAccess) {
    List<EntityDetailWithSetupUsageDetailProtoDTO> allSecretDetails = new ArrayList<>();
    for (Map.Entry<String, SecretRefData> secret : secrets.entrySet()) {
      if (secret == null || secret.getValue() == null || secret.getValue().isNull()) {
        continue;
      }
      BaseNGAccess secretRefScopeInfo = SecretRefHelper.getScopeIdentifierForSecretRef(secret.getValue(),
          baseNGAccess.getAccountIdentifier(), baseNGAccess.getOrgIdentifier(), baseNGAccess.getProjectIdentifier());
      IdentifierRef secretIdentifierRef = IdentifierRefHelper.getIdentifierRef(
          secret.getValue().toSecretRefStringValue(), secretRefScopeInfo.getAccountIdentifier(),
          secretRefScopeInfo.getOrgIdentifier(), secretRefScopeInfo.getProjectIdentifier());
      if (secretIdentifierRef == null) {
        continue;
      }
      IdentifierRefProtoDTO secretReference = identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
          baseNGAccess.getAccountIdentifier(), secretIdentifierRef.getOrgIdentifier(),
          secretIdentifierRef.getProjectIdentifier(), secretIdentifierRef.getIdentifier());
      EntityDetailProtoDTO entityDetailProtoDTO = EntityDetailProtoDTO.newBuilder()
                                                      .setIdentifierRef(secretReference)
                                                      .setType(EntityTypeProtoEnum.SECRETS)
                                                      .build();
      EntityDetailWithSetupUsageDetailProtoDTO.SecretReferredBySecretSetupUsageDetailProtoDTO detailProtoDTO =
          EntityDetailWithSetupUsageDetailProtoDTO.SecretReferredBySecretSetupUsageDetailProtoDTO.newBuilder()
              .setFieldName(secret.getKey())
              .build();
      allSecretDetails.add(EntityDetailWithSetupUsageDetailProtoDTO.newBuilder()
                               .setReferredEntity(entityDetailProtoDTO)
                               .setType(SECRET_REFERRED_BY_SECRET.toString())
                               .setSecretBySecretDetail(detailProtoDTO)
                               .build());
    }
    return allSecretDetails;
  }

  public void deleteSecretEntityReferenceWhenSecretGetsDeleted(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String secretIdentifier, String secretManagerIdentifier) {
    String secretMangerFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, secretManagerIdentifier);
    String secretFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, secretIdentifier);
    try {
      DeleteSetupUsageDTO deleteSetupUsageDTO = DeleteSetupUsageDTO.newBuilder()
                                                    .setAccountIdentifier(accountIdentifier)
                                                    .setReferredByEntityFQN(secretFQN)
                                                    .setReferredByEntityType(EntityTypeProtoEnum.SECRETS)
                                                    .setReferredEntityFQN(secretMangerFQN)
                                                    .setReferredEntityType(EntityTypeProtoEnum.CONNECTORS)
                                                    .build();
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier,
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.SECRETS.name(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.DELETE_ACTION))
              .setData(deleteSetupUsageDTO.toByteString())
              .build());
    } catch (Exception ex) {
      log.info(ENTITY_REFERENCE_LOG_PREFIX
              + "The entity reference was not deleted when the secret [{}] was deleted from the secret manager [{}] with the exception [{}]",
          secretFQN, secretMangerFQN, ex.getMessage());
    }
  }

  public void deleteExistingSetupUsage(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    IdentifierRefProtoDTO secretReference = identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    EntityDetailProtoDTO secretDetails = EntityDetailProtoDTO.newBuilder()
                                             .setIdentifierRef(secretReference)
                                             .setType(EntityTypeProtoEnum.SECRETS)
                                             .build();

    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(accountIdentifier)
                                                         .setReferredByEntity(secretDetails)
                                                         .setDeleteOldReferredByRecords(true)
                                                         .build();
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of(ACCOUNT_ID, accountIdentifier, EventsFrameworkMetadataConstants.ACTION,
                  EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    } catch (Exception ex) {
      log.error("Error deleting the setup usages for the connector with the identifier {} in project {} in org {}",
          identifier, projectIdentifier, orgIdentifier, ex);
    }
  }

  public void validateSecretIsNotUsedByOthers(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String secretIdentifier) {
    boolean isEntityReferenced;
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(accountIdentifier)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .identifier(secretIdentifier)
                                      .build();
    String referredEntityFQN = identifierRef.getFullyQualifiedName();
    try {
      isEntityReferenced =
          entitySetupUsageService.isEntityReferenced(accountIdentifier, referredEntityFQN, EntityType.SECRETS);
    } catch (Exception ex) {
      log.info("Encountered exception while requesting the Entity Reference records of [{}], with exception",
          secretIdentifier, ex);
      throw new UnexpectedException("Error while deleting the secret");
    }
    if (isEntityReferenced) {
      throw new ReferencedEntityException(
          String.format("Could not delete the secret %s as it is referenced by other entities", secretIdentifier));
    }
  }
}
