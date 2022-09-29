/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.customdeployment.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.TEMPLATE;

import static software.wings.beans.AccountType.log;

import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.eventsframework.schemas.entity.TemplateReferenceProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.plancreator.customDeployment.StepTemplateRef;
import io.harness.pms.merger.YamlConfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;

public class CustomDeploymentEntitySetupHelper {
  @Inject @Named(EventsFrameworkConstants.SETUP_USAGE) private Producer eventProducer;
  @JsonIgnore private final ObjectMapper jsonObjectMapper = new ObjectMapper();
  private static final String ACCOUNT_IDENTIFIER = "account.";
  private static final String ORG_IDENTIFIER = "org.";

  public void addReferencesInEntitySetupUsage(@NotNull InfrastructureEntity infraEntity) {
    EntityDetailProtoDTO entityDetailProtoDTO = getEntityProto(infraEntity);
    publishSetupUsageEvent(infraEntity, entityDetailProtoDTO);
  }
  public void deleteReferencesInEntitySetupUsage(@NotNull InfrastructureEntity infraEntity) {
    EntityDetailProtoDTO infraDetails = getEntityProtoForDelete(infraEntity);
    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(infraEntity.getAccountId())
                                                         .setReferredByEntity(infraDetails)
                                                         .setDeleteOldReferredByRecords(true)
                                                         .build();
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", infraEntity.getAccountId(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    } catch (Exception ex) {
      log.error("Error deleting the setup usages for the template with the identifier {} in project {} in org {}: {}",
          infraEntity.getAccountId(), infraEntity.getProjectIdentifier(), infraEntity.getOrgIdentifier(), ex);
    }
  }
  public void publishSetupUsageEvent(
      @NotNull InfrastructureEntity infraEntity, EntityDetailProtoDTO entityDetailProtoDTO) {
    Map<String, List<EntityDetailProtoDTO>> referredEntityTypeToReferredEntities = new HashMap<>();
    List<EntityDetailProtoDTO> entityDetailProtoDTOS =
        referredEntityTypeToReferredEntities.getOrDefault(entityDetailProtoDTO.getType().name(), new ArrayList<>());
    entityDetailProtoDTOS.add(entityDetailProtoDTO);
    referredEntityTypeToReferredEntities.put(entityDetailProtoDTO.getType().name(), entityDetailProtoDTOS);
    EntityDetailProtoDTO referredByEntity =
        EntityDetailProtoDTO.newBuilder()
            .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                  .setIdentifier(StringValue.of(infraEntity.getIdentifier()))
                                  .setAccountIdentifier(StringValue.of(infraEntity.getAccountId()))
                                  .setOrgIdentifier(StringValue.of(infraEntity.getOrgIdentifier()))
                                  .setProjectIdentifier(StringValue.of(infraEntity.getProjectIdentifier()))
                                  .putMetadata("envId", infraEntity.getEnvIdentifier())
                                  .build())
            .setType(EntityTypeProtoEnum.INFRASTRUCTURE)
            .setName(infraEntity.getName())
            .build();
    for (Map.Entry<String, List<EntityDetailProtoDTO>> entry : referredEntityTypeToReferredEntities.entrySet()) {
      List<EntityDetailProtoDTO> entityDetailProtoDTOs = entry.getValue();
      EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                           .setAccountIdentifier(infraEntity.getAccountId())
                                                           .setReferredByEntity(referredByEntity)
                                                           .addAllReferredEntities(entityDetailProtoDTOs)
                                                           .setDeleteOldReferredByRecords(true)
                                                           .build();
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", infraEntity.getAccountId(),
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, entry.getKey(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    }
  }

  private StepTemplateRef getStepTemplateRefFromYaml(@NotNull InfrastructureEntity infraEntity) {
    YamlConfig yamlConfig = new YamlConfig(infraEntity.getYaml());
    JsonNode yamlMap = yamlConfig.getYamlMap();
    JsonNode infraDef = yamlMap.get("infrastructureDefinition");
    try {
      if (infraDef.isNull()) {
        log.error("Infra definition is null in yaml for account id :{}", infraEntity.getAccountId());
        throw new InvalidRequestException("Infra definition is null in yaml");
      }
      JsonNode spec = infraDef.get("spec");
      if (spec.isNull()) {
        log.error("spec is null in yaml for account id :{}", infraEntity.getAccountId());
        throw new InvalidRequestException("Infra definition spec is null in yaml");
      }
      JsonNode customDeploymentRef = spec.get("customDeploymentRef");
      if (customDeploymentRef.isNull()) {
        log.error("customDeploymentRef is null in yaml for account id :{}", infraEntity.getAccountId());
        throw new InvalidRequestException("customDeploymentRef is null in yaml");
      }
      StepTemplateRef stepTemplateRef = jsonObjectMapper.treeToValue(customDeploymentRef, StepTemplateRef.class);
      if (isEmpty(stepTemplateRef.getTemplateRef())) {
        log.error("templateRef is empty in yaml for account id :{}", infraEntity.getAccountId());
        throw new InvalidRequestException("templateRef is null in yaml");
      }
      return stepTemplateRef;
    } catch (Exception e) {
      log.error("Could not fetch the template reference from yaml for acc :{}, project :{}, infraRef:{}: {}",
          infraEntity.getAccountId(), infraEntity.getProjectIdentifier(), infraEntity.getIdentifier(), e);
      throw new InvalidRequestException("Could not fetch the template reference from yaml " + e.getMessage());
    }
  }

  private EntityDetailProtoDTO getEntityProto(@NotNull InfrastructureEntity infraEntity) {
    try {
      StepTemplateRef stepTemplateRef = getStepTemplateRefFromYaml(infraEntity);
      String templateRef = stepTemplateRef.getTemplateRef();
      String versionLabel = stepTemplateRef.getVersionLabel();
      TemplateReferenceProtoDTO.Builder templateReferenceProtoDTO =
          TemplateReferenceProtoDTO.newBuilder().setAccountIdentifier(StringValue.of(infraEntity.getAccountId()));
      if (templateRef.contains(ACCOUNT_IDENTIFIER)) {
        templateReferenceProtoDTO.setScope(ScopeProtoEnum.ACCOUNT)
            .setIdentifier(StringValue.of(templateRef.replace(ACCOUNT_IDENTIFIER, "")));
      } else if (templateRef.contains(ORG_IDENTIFIER)) {
        templateReferenceProtoDTO.setScope(ScopeProtoEnum.ORG)
            .setOrgIdentifier(StringValue.of(infraEntity.getOrgIdentifier()))
            .setIdentifier(StringValue.of(templateRef.replace(ORG_IDENTIFIER, "")));
      } else {
        templateReferenceProtoDTO.setScope(ScopeProtoEnum.PROJECT)
            .setOrgIdentifier(StringValue.of(infraEntity.getOrgIdentifier()))
            .setProjectIdentifier(StringValue.of(infraEntity.getProjectIdentifier()))
            .setIdentifier(StringValue.of(templateRef));
      }
      templateReferenceProtoDTO.setVersionLabel(StringValue.of(versionLabel));
      return EntityDetailProtoDTO.newBuilder()
          .setType(TEMPLATE)
          .setTemplateRef(templateReferenceProtoDTO.build())
          .build();
    } catch (Exception e) {
      log.error("Could not delete the reference in entity setup usage for acc :{}, project :{}, infraRef:{}: {}",
          infraEntity.getAccountId(), infraEntity.getProjectIdentifier(), infraEntity.getIdentifier(), e);
      throw new InvalidRequestException("Could not delete the reference in entity setup usage " + e.getMessage());
    }
  }
  private EntityDetailProtoDTO getEntityProtoForDelete(@NotNull InfrastructureEntity infraEntity) {
    IdentifierRefProtoDTO identifierRefProtoDTO =
        IdentifierRefProtoDTO.newBuilder()
            .setAccountIdentifier(StringValue.of(infraEntity.getAccountId()))
            .setOrgIdentifier(StringValue.of(infraEntity.getOrgIdentifier()))
            .setProjectIdentifier(StringValue.of(infraEntity.getProjectIdentifier()))
            .setIdentifier(StringValue.of(infraEntity.getIdentifier()))
            .build();

    return EntityDetailProtoDTO.newBuilder()
        .setIdentifierRef(identifierRefProtoDTO)
        .setType(EntityTypeProtoEnum.INFRASTRUCTURE)
        .build();
  }
}
