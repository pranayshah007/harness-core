/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.NGConstants.ENTITY_REFERENCE_LOG_PREFIX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.TEMPLATE;
import static io.harness.ng.core.entitysetupusage.dto.SetupUsageDetailType.SECRET_REFERRED_BY_CONNECTOR;
import static io.harness.ng.core.entitysetupusage.dto.SetupUsageDetailType.TEMPLATE_REFERRED_BY_CONNECTOR;

import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.utils.TemplateDetails;
import io.harness.encryption.SecretRefData;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.eventsframework.schemas.entity.TemplateReferenceProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntityDetailWithSetupUsageDetailProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ConnectorEntityReferenceHelper {
  private static final String STABLE_VERSION = "__STABLE__";
  private static final String ACCOUNT_ID = "accountId";
  Producer eventProducer;
  SecretRefInputValidationHelper secretRefInputValidationHelper;

  private final String TEMPLATE_REFERENCE_CREATE_FAIL_LOG_MSG =
      "The entity reference was not created for the connector [{}], event ID [{}] with the exception[{}]";
  private final String TEMPLATE_REFERENCE_UPDATE_FAIL_LOG_MSG =
      "The entity reference was not updated for the connector [{}], event ID [{}] with the exception[{}] ";
  private final String deleteLogMsg =
      "The entity reference was not deleted when the connector [{}] was deleted that used secret [{}] with the exception[{}]";
  private final String createFailedLogMsg =
      "The entity reference was not created for the connector [{}] using the secret [{}] with the exception[{}]";
  private final String updateFailedLogMsg =
      "The entity reference was not updated for the connector [{}] using the secret [{}] with the exception[{}]";

  @Inject
  public ConnectorEntityReferenceHelper(@Named(EventsFrameworkConstants.SETUP_USAGE) Producer eventProducer,
      SecretRefInputValidationHelper secretRefInputValidationHelper) {
    this.eventProducer = eventProducer;
    this.secretRefInputValidationHelper = secretRefInputValidationHelper;
  }

  public boolean createSetupUsageForSecret(
      ConnectorInfoDTO connectorInfoDTO, String accountIdentifier, boolean isUpdate) {
    String logMessage = isUpdate ? updateFailedLogMsg : createFailedLogMsg;
    String connectorFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(accountIdentifier,
        connectorInfoDTO.getOrgIdentifier(), connectorInfoDTO.getProjectIdentifier(), connectorInfoDTO.getIdentifier());
    List<DecryptableEntity> decryptableEntities = connectorInfoDTO.getConnectorConfig().getDecryptableEntities();
    if (isEmpty(decryptableEntities)) {
      return produceEventForSetupUsage(
          connectorInfoDTO, new ArrayList<>(), accountIdentifier, connectorFQN, new ArrayList<>(), logMessage);
    }
    Map<String, SecretRefData> secrets = secretRefInputValidationHelper.getDecryptableFieldsData(decryptableEntities);

    NGAccess baseNGAccess = buildBaseNGAccess(connectorInfoDTO, accountIdentifier);
    List<String> secretFQNs = getAllSecretFQNs(secrets, baseNGAccess);
    List<EntityDetailWithSetupUsageDetailProtoDTO> allSecretDetails =
        getAllReferredSecretDetails(secrets, baseNGAccess);

    log.info(ENTITY_REFERENCE_LOG_PREFIX
            + "[{}] the entity reference when the connector [{}] was created using the secret [{}]",
        isUpdate ? "Updating" : "Creating", connectorFQN, secretFQNs);

    return produceEventForSetupUsage(
        connectorInfoDTO, allSecretDetails, accountIdentifier, connectorFQN, secretFQNs, logMessage);
  }

  public void createSetupUsageForTemplate(
      @Valid ConnectorInfoDTO connectorInfoDTO, String accountIdentifier, boolean isUpdate) {
    List<EntityDetailWithSetupUsageDetailProtoDTO> templateEntityDTO =
        getTemplateDTOFromConnectorInfoDTO(connectorInfoDTO, accountIdentifier);
    if (isEmpty(templateEntityDTO)) {
      return;
    }
    String logMessage = isUpdate ? TEMPLATE_REFERENCE_UPDATE_FAIL_LOG_MSG : TEMPLATE_REFERENCE_CREATE_FAIL_LOG_MSG;
    String connectorFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(accountIdentifier,
        connectorInfoDTO.getOrgIdentifier(), connectorInfoDTO.getProjectIdentifier(), connectorInfoDTO.getIdentifier());

    produceEventForTemplateSetupUsage(
        connectorInfoDTO, templateEntityDTO, accountIdentifier, connectorFQN, null, logMessage);
  }

  @VisibleForTesting
  protected List<EntityDetailWithSetupUsageDetailProtoDTO> getTemplateDTOFromConnectorInfoDTO(
      ConnectorInfoDTO connectorInfo, String accountIdentifier) {
    try {
      List<TemplateDetails> templateDetails = connectorInfo.getConnectorConfig().getTemplateDetails();
      if (isEmpty(templateDetails)) {
        return null;
      }
      List<EntityDetailWithSetupUsageDetailProtoDTO> allTemplateDetails = new ArrayList<>();

      for (TemplateDetails template : templateDetails) {
        EntityDetailProtoDTO entityDetailProtoDTO = getEntityDetailProtoDTO(
            template, accountIdentifier, connectorInfo.getOrgIdentifier(), connectorInfo.getProjectIdentifier());
        EntityDetailWithSetupUsageDetailProtoDTO.TemplateReferredByConnectorSetupUsageDetailProtoDTO detailProtoDTO =
            EntityDetailWithSetupUsageDetailProtoDTO.TemplateReferredByConnectorSetupUsageDetailProtoDTO.newBuilder()
                .setIdentifier(template.getTemplateRef())
                .setVersion(template.getVersionLabel())
                .build();
        allTemplateDetails.add(EntityDetailWithSetupUsageDetailProtoDTO.newBuilder()
                                   .setReferredEntity(entityDetailProtoDTO)
                                   .setType(TEMPLATE_REFERRED_BY_CONNECTOR.toString())
                                   .setTemplateConnectorDetail(detailProtoDTO)
                                   .build());
      }
      return allTemplateDetails;
    } catch (Exception e) {
      log.error("Could not add the reference in entity setup usage for acc :{}, project :{}, connector:{}: {}",
          accountIdentifier, connectorInfo.getProjectIdentifier(), connectorInfo.getIdentifier(), e);
    }
    return null;
  }
  @VisibleForTesting
  protected EntityDetailProtoDTO getEntityDetailProtoDTO(
      TemplateDetails template, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    String templateRef = template.getTemplateRef();
    String versionLabel = template.getVersionLabel();
    if (isEmpty(versionLabel)) {
      versionLabel = STABLE_VERSION;
    }

    final IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(templateRef, accountIdentifier, orgIdentifier, projectIdentifier);

    TemplateReferenceProtoDTO.Builder templateReferenceProtoDTO =
        TemplateReferenceProtoDTO.newBuilder()
            .setVersionLabel(StringValue.of(versionLabel))
            .setAccountIdentifier(StringValue.of(accountIdentifier))
            .setIdentifier(StringValue.of(identifierRef.getIdentifier()));

    switch (identifierRef.getScope()) {
      case ACCOUNT:
        templateReferenceProtoDTO.setScope(ScopeProtoEnum.ACCOUNT);
        break;
      case ORG:
        templateReferenceProtoDTO.setScope(ScopeProtoEnum.ORG)
            .setOrgIdentifier(StringValue.of(identifierRef.getOrgIdentifier()));
        break;
      case PROJECT:
        templateReferenceProtoDTO.setScope(ScopeProtoEnum.PROJECT)
            .setOrgIdentifier(StringValue.of(identifierRef.getOrgIdentifier()))
            .setProjectIdentifier(StringValue.of(identifierRef.getProjectIdentifier()));
        break;
      default:
        templateReferenceProtoDTO.setScope(ScopeProtoEnum.UNKNOWN);
    }

    return EntityDetailProtoDTO.newBuilder()
        .setType(TEMPLATE)
        .setTemplateRef(templateReferenceProtoDTO.build())
        .build();
  }

  public boolean deleteConnectorEntityReferenceWhenConnectorGetsDeleted(
      ConnectorInfoDTO connectorInfoDTO, String accountIdentifier) {
    String connectorFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(accountIdentifier,
        connectorInfoDTO.getOrgIdentifier(), connectorInfoDTO.getProjectIdentifier(), connectorInfoDTO.getIdentifier());

    List<DecryptableEntity> decryptableEntities = connectorInfoDTO.getConnectorConfig().getDecryptableEntities();
    if (isEmpty(decryptableEntities)) {
      return true;
    }
    Map<String, SecretRefData> secrets = secretRefInputValidationHelper.getDecryptableFieldsData(decryptableEntities);

    NGAccess baseNGAccess = buildBaseNGAccess(connectorInfoDTO, accountIdentifier);
    List<String> secretFQNs = getAllSecretFQNs(secrets, baseNGAccess);
    List<EntityDetailWithSetupUsageDetailProtoDTO> allSecretDetails = new ArrayList<>();
    return produceEventForSetupUsage(
        connectorInfoDTO, allSecretDetails, accountIdentifier, connectorFQN, secretFQNs, deleteLogMsg);
  }
  private boolean produceEventForSetupUsage(ConnectorInfoDTO connectorInfoDTO,
      List<EntityDetailWithSetupUsageDetailProtoDTO> allSecretDetails, String accountIdentifier, String connectorFQN,
      List<String> secretFQNs, String logMessage) {
    EntitySetupUsageCreateV2DTO entityReferenceDTO =
        createSetupUsageDTOForConnector(connectorInfoDTO, allSecretDetails, accountIdentifier);
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of(ACCOUNT_ID, accountIdentifier,
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.SECRETS.name(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
      return true;
    } catch (Exception ex) {
      log.info(ENTITY_REFERENCE_LOG_PREFIX + logMessage, connectorFQN, secretFQNs, ex.getMessage());
      return false;
    }
  }

  @VisibleForTesting
  protected boolean produceEventForTemplateSetupUsage(ConnectorInfoDTO connectorInfoDTO,
      List<EntityDetailWithSetupUsageDetailProtoDTO> allTemplateDetails, String accountIdentifier, String connectorFQN,
      List<String> templateFQN, String logMessage) {
    EntitySetupUsageCreateV2DTO entityReferenceDTO =
        createSetupUsageDTOForConnector(connectorInfoDTO, allTemplateDetails, accountIdentifier);
    String event = "";
    try {
      event = eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of(ACCOUNT_ID, accountIdentifier,
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, TEMPLATE.name(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
      return true;
    } catch (Exception ex) {
      log.info(ENTITY_REFERENCE_LOG_PREFIX + logMessage, connectorFQN, templateFQN, event, ex.getMessage());
      return false;
    }
  }
  private EntitySetupUsageCreateV2DTO createSetupUsageDTOForConnector(ConnectorInfoDTO connectorInfoDTO,
      List<EntityDetailWithSetupUsageDetailProtoDTO> secretDetails, String accountIdentifier) {
    IdentifierRefProtoDTO connectorReference =
        IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(accountIdentifier, connectorInfoDTO.getOrgIdentifier(),
            connectorInfoDTO.getProjectIdentifier(), connectorInfoDTO.getIdentifier());
    EntityDetailProtoDTO connectorDetails = EntityDetailProtoDTO.newBuilder()
                                                .setIdentifierRef(connectorReference)
                                                .setType(EntityTypeProtoEnum.CONNECTORS)
                                                .setName(connectorInfoDTO.getName())
                                                .build();

    return EntitySetupUsageCreateV2DTO.newBuilder()
        .setAccountIdentifier(accountIdentifier)
        .setReferredByEntity(connectorDetails)
        .addAllReferredEntityWithSetupUsageDetail(secretDetails)
        .setDeleteOldReferredByRecords(true)
        .build();
  }

  private List<IdentifierRef> getAllSecretIdentifiers(Map<String, SecretRefData> secrets, NGAccess baseNGAccess) {
    List<IdentifierRef> secretIdentifierRef = new ArrayList<>();
    for (Map.Entry<String, SecretRefData> secret : secrets.entrySet()) {
      if (secret != null && secret.getValue() != null && !secret.getValue().isNull()) {
        secretIdentifierRef.add(IdentifierRefHelper.getIdentifierRef(secret.getValue().toSecretRefStringValue(),
            baseNGAccess.getAccountIdentifier(), baseNGAccess.getOrgIdentifier(), baseNGAccess.getProjectIdentifier()));
      }
    }
    return secretIdentifierRef;
  }

  private NGAccess buildBaseNGAccess(ConnectorInfoDTO connectorInfoDTO, String accountIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(connectorInfoDTO.getOrgIdentifier())
        .projectIdentifier(connectorInfoDTO.getProjectIdentifier())
        .build();
  }

  private List<String> getAllSecretFQNs(Map<String, SecretRefData> secrets, NGAccess baseNGAccess) {
    List<IdentifierRef> secretIdentifiers = getAllSecretIdentifiers(secrets, baseNGAccess);
    List<String> secretFQNs = new ArrayList<>();
    for (IdentifierRef secretIdentifier : secretIdentifiers) {
      if (secretIdentifier != null) {
        secretFQNs.add(FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(baseNGAccess.getAccountIdentifier(),
            secretIdentifier.getOrgIdentifier(), secretIdentifier.getProjectIdentifier(),
            secretIdentifier.getIdentifier()));
      }
    }
    return secretFQNs;
  }

  private List<EntityDetailWithSetupUsageDetailProtoDTO> getAllReferredSecretDetails(
      Map<String, SecretRefData> secrets, NGAccess baseNGAccess) {
    List<EntityDetailWithSetupUsageDetailProtoDTO> allSecretDetails = new ArrayList<>();
    for (Map.Entry<String, SecretRefData> secret : secrets.entrySet()) {
      if (secret != null && secret.getValue() != null && !secret.getValue().isNull()) {
        IdentifierRef secretIdentifierRef = IdentifierRefHelper.getIdentifierRef(
            secret.getValue().toSecretRefStringValue(), baseNGAccess.getAccountIdentifier(),
            baseNGAccess.getOrgIdentifier(), baseNGAccess.getProjectIdentifier());
        if (secretIdentifierRef == null) {
          continue;
        }
        IdentifierRefProtoDTO secretReference = IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
            baseNGAccess.getAccountIdentifier(), secretIdentifierRef.getOrgIdentifier(),
            secretIdentifierRef.getProjectIdentifier(), secretIdentifierRef.getIdentifier());
        EntityDetailProtoDTO entityDetailProtoDTO = EntityDetailProtoDTO.newBuilder()
                                                        .setIdentifierRef(secretReference)
                                                        .setType(EntityTypeProtoEnum.SECRETS)
                                                        .build();
        EntityDetailWithSetupUsageDetailProtoDTO.SecretReferredByConnectorSetupUsageDetailProtoDTO detailProtoDTO =
            EntityDetailWithSetupUsageDetailProtoDTO.SecretReferredByConnectorSetupUsageDetailProtoDTO.newBuilder()
                .setFieldName(secret.getKey())
                .build();
        allSecretDetails.add(EntityDetailWithSetupUsageDetailProtoDTO.newBuilder()
                                 .setReferredEntity(entityDetailProtoDTO)
                                 .setType(SECRET_REFERRED_BY_CONNECTOR.toString())
                                 .setSecretConnectorDetail(detailProtoDTO)
                                 .build());
      }
    }
    return allSecretDetails;
  }

  public void deleteExistingSetupUsages(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    IdentifierRefProtoDTO connectorReference = IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    EntityDetailProtoDTO connectorDetails = EntityDetailProtoDTO.newBuilder()
                                                .setIdentifierRef(connectorReference)
                                                .setType(EntityTypeProtoEnum.CONNECTORS)
                                                .build();

    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(accountIdentifier)
                                                         .setReferredByEntity(connectorDetails)
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
}
