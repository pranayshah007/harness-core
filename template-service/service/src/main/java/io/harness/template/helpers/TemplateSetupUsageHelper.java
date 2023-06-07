/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers;

import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.NGTemplateReference;
import io.harness.data.structure.EmptyPredicate;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityGitMetadata;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.template.TemplateReferenceProtoUtils;
import io.harness.template.async.beans.SetupUsageParams;
import io.harness.template.async.beans.SetupUsagesGitMetadata;
import io.harness.template.entity.TemplateEntity;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class TemplateSetupUsageHelper {
  private static final int PAGE = 0;
  private static final int SIZE = 100;
  @Inject @Named(EventsFrameworkConstants.SETUP_USAGE) private Producer eventProducer;
  @Inject EntitySetupUsageClient entitySetupUsageClient;

  public List<EntitySetupUsageDTO> getReferencesOfTemplate(
      String accountId, String orgId, String projectId, String templateIdentifier, String versionLabel) {
    NGTemplateReference ngTemplateReference = NGTemplateReference.builder()
                                                  .accountIdentifier(accountId)
                                                  .orgIdentifier(orgId)
                                                  .projectIdentifier(projectId)
                                                  .identifier(templateIdentifier)
                                                  .versionLabel(versionLabel)
                                                  .build();

    String templateReferenceFqn = ngTemplateReference.getFullyQualifiedName();
    return getResponse(
        entitySetupUsageClient.listAllReferredUsages(PAGE, SIZE, accountId, templateReferenceFqn, null, null));
  }

  public void publishSetupUsageEvent(
      SetupUsageParams setupUsageParams, List<EntityDetailProtoDTO> referredEntities, Map<String, String> metadata) {
    // Deleting all references so that any deleted entity is not still referred.
    TemplateEntity templateEntity = setupUsageParams.getTemplateEntity();
    deleteExistingSetupUsages(templateEntity);
    if (EmptyPredicate.isEmpty(referredEntities)) {
      return;
    }

    String accountId = templateEntity.getAccountId();
    EntityDetailProtoDTO referredEntity = populateEntityDetailsProtoDTO(metadata, setupUsageParams, accountId);

    Map<String, List<EntityDetailProtoDTO>> referredEntityTypeToReferredEntities = new HashMap<>();
    for (EntityDetailProtoDTO entityDetailProtoDTO : referredEntities) {
      List<EntityDetailProtoDTO> entityDetailProtoDTOS =
          referredEntityTypeToReferredEntities.getOrDefault(entityDetailProtoDTO.getType().name(), new ArrayList<>());
      entityDetailProtoDTOS.add(entityDetailProtoDTO);
      referredEntityTypeToReferredEntities.put(entityDetailProtoDTO.getType().name(), entityDetailProtoDTOS);
    }

    for (Map.Entry<String, List<EntityDetailProtoDTO>> entry : referredEntityTypeToReferredEntities.entrySet()) {
      List<EntityDetailProtoDTO> entityDetailProtoDTOs = entry.getValue();
      EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                           .setAccountIdentifier(accountId)
                                                           .setReferredByEntity(referredEntity)
                                                           .addAllReferredEntities(entityDetailProtoDTOs)
                                                           .setDeleteOldReferredByRecords(true)
                                                           .build();
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountId,
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, entry.getKey(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    }
  }

  private EntityDetailProtoDTO populateEntityDetailsProtoDTO(
      Map<String, String> metadata, SetupUsageParams setupUsageParams, String accountId) {
    TemplateEntity templateEntity = setupUsageParams.getTemplateEntity();
    EntityDetailProtoDTO templateDetails =
        EntityDetailProtoDTO.newBuilder()
            .setTemplateRef(
                TemplateReferenceProtoUtils.createTemplateReferenceProto(accountId, templateEntity.getOrgIdentifier(),
                    templateEntity.getProjectIdentifier(), templateEntity.getIdentifier(),
                    templateEntity.getTemplateScope(), templateEntity.getVersionLabel(), metadata))
            .setType(EntityTypeProtoEnum.TEMPLATE)
            .setName(templateEntity.getName())
            .build();
    SetupUsagesGitMetadata setupUsagesGitMetadata = setupUsageParams.getSetupUsagesGitMetadata();
    if (setupUsagesGitMetadata != null) {
      templateDetails = EntityDetailProtoDTO.newBuilder(templateDetails)
                            .setEntityGitMetadata(EntityGitMetadata.newBuilder()
                                                      .setBranch(setupUsagesGitMetadata.getBranch())
                                                      .setRepo(setupUsagesGitMetadata.getRepo())
                                                      .build())
                            .build();
    }
    return templateDetails;
  }

  public void deleteExistingSetupUsages(TemplateEntity templateEntity) {
    String accountId = templateEntity.getAccountId();
    EntityDetailProtoDTO templateDetails =
        EntityDetailProtoDTO.newBuilder()
            .setTemplateRef(
                TemplateReferenceProtoUtils.createTemplateReferenceProto(accountId, templateEntity.getOrgIdentifier(),
                    templateEntity.getProjectIdentifier(), templateEntity.getIdentifier(),
                    templateEntity.getTemplateScope(), templateEntity.getVersionLabel(), null))
            .setType(EntityTypeProtoEnum.TEMPLATE)
            .build();
    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(accountId)
                                                         .setReferredByEntity(templateDetails)
                                                         .setDeleteOldReferredByRecords(true)
                                                         .build();
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountId, EventsFrameworkMetadataConstants.ACTION,
                  EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    } catch (Exception ex) {
      log.error(
          "Error deleting the setup usages for the template with the identifier {}, versionlabel {} in project {} in org {}",
          templateEntity.getAccountId(), templateEntity.getVersionLabel(), templateEntity.getProjectIdentifier(),
          templateEntity.getOrgIdentifier());
    }
  }
}
