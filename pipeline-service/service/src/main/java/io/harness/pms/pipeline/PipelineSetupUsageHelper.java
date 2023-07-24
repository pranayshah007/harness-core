/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.CONNECTORS;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.ENVIRONMENT;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.ENVIRONMENT_GROUP;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.FILES;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.INFRASTRUCTURE;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.PIPELINES;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.SECRETS;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.SERVICE;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.TEMPLATE;

import io.harness.EntityType;
import io.harness.PipelineSetupUsageUtils;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityGitMetadata;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntityDetailWithSetupUsageDetailProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntityDetailWithSetupUsageDetailProtoDTO.EntityReferredByPipelineDetailProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntityDetailWithSetupUsageDetailProtoDTO.PipelineDetailType;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.dto.SetupUsageDetailType;
import io.harness.pms.events.PipelineDeleteEvent;
import io.harness.pms.pipeline.observer.PipelineActionObserver;
import io.harness.pms.pipeline.references.FilterCreationGitMetadata;
import io.harness.pms.pipeline.references.FilterCreationParams;
import io.harness.pms.rbac.InternalReferredEntityExtractor;
import io.harness.pms.yaml.YamlUtils;
import io.harness.preflight.PreFlightCheckMetadata;
import io.harness.remote.client.NGRestUtils;
import io.harness.utils.FullyQualifiedIdentifierHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class PipelineSetupUsageHelper implements PipelineActionObserver {
  @Inject @Named(EventsFrameworkConstants.SETUP_USAGE) private Producer eventProducer;
  @Inject private EntitySetupUsageClient entitySetupUsageClient;
  @Inject private InternalReferredEntityExtractor internalReferredEntityExtractor;
  @Inject private GitSyncSdkService gitSyncSdkService;
  private static final int PAGE = 0;
  private static final int SIZE = 100;

  final Set<EntityTypeProtoEnum> entityTypesSupportedByNGCore = Sets.newHashSet(
      SECRETS, CONNECTORS, SERVICE, ENVIRONMENT, ENVIRONMENT_GROUP, TEMPLATE, FILES, PIPELINES, INFRASTRUCTURE);

  /**
   * Performs the following:
   * - queries the entitySetupUsage framework to get all entities referenced in the given pipeline yaml. (static, inputs
   * and runtimeExpression)
   * - extracts the value of a runtime input using fqn from the given pipeline yaml
   * - does not resolve runtimeExpressions as they can only be resolved during execution.
   * - can filter out resources of given entityType too. If entityType is null, it gives all resources.
   *
   * @param accountIdentifier - accountIdentifier of the pipeline
   * @param orgIdentifier -  orgIdentifier of the pipeline
   * @param projectIdentifier - projectIdentifier of the pipeline
   * @param pipelineId - pipelineIdentifier
   * @param pipelineYamlWithUnresolvedTemplates - merged pipeline yaml with no templates resolved, because the
   *     references are saved based on unresolved yaml.
   * @param entityType - returns response of given entity type referred in the pipeline.
   */
  public List<EntityDetail> getReferencesOfPipeline(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineId, String pipelineYamlWithUnresolvedTemplates, EntityType entityType) {
    List<EntitySetupUsageDTO> allReferredUsages =
        NGRestUtils.getResponse(entitySetupUsageClient.listAllReferredUsages(PAGE, SIZE, accountIdentifier,
                                    FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
                                        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId),
                                    entityType, null),
            "Could not extract setup usage of pipeline with id " + pipelineId + " after {} attempts.");
    List<EntityDetail> entityDetails = PipelineSetupUsageUtils.extractInputReferredEntityFromYaml(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineYamlWithUnresolvedTemplates, allReferredUsages);
    entityDetails.addAll(internalReferredEntityExtractor.extractInternalEntities(accountIdentifier, entityDetails));
    return entityDetails;
  }

  public List<EntityDetail> getReferencesOfPipeline(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineId, JsonNode pipelineJsonNodeWithUnresolvedTemplates,
      EntityType entityType) {
    List<EntitySetupUsageDTO> allReferredUsages =
        NGRestUtils.getResponse(entitySetupUsageClient.listAllReferredUsages(PAGE, SIZE, accountIdentifier,
                                    FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
                                        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId),
                                    entityType, null),
            "Could not extract setup usage of pipeline with id " + pipelineId + " after {} attempts.");
    List<EntityDetail> entityDetails = PipelineSetupUsageUtils.extractInputReferredEntityFromYaml(accountIdentifier,
        orgIdentifier, projectIdentifier, pipelineJsonNodeWithUnresolvedTemplates, allReferredUsages);
    entityDetails.addAll(internalReferredEntityExtractor.extractInternalEntities(accountIdentifier, entityDetails));
    return entityDetails;
  }

  public void deleteExistingSetupUsages(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    IdentifierRefProtoDTO pipelineReference = IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    EntityDetailProtoDTO pipelineDetails = EntityDetailProtoDTO.newBuilder()
                                               .setIdentifierRef(pipelineReference)
                                               .setType(EntityTypeProtoEnum.PIPELINES)
                                               .build();
    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(accountIdentifier)
                                                         .setReferredByEntity(pipelineDetails)
                                                         .setDeleteOldReferredByRecords(true)
                                                         .build();
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier, EventsFrameworkMetadataConstants.ACTION,
                  EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    } catch (Exception ex) {
      log.error("Error deleting the setup usages for the connector with the identifier {} in project {} in org {}",
          identifier, projectIdentifier, orgIdentifier);
    }
  }

  public void publishSetupUsageEvent(
      FilterCreationParams filterCreationParams, List<EntityDetailProtoDTO> referredEntities) {
    PipelineEntity pipelineEntity = filterCreationParams.getPipelineEntity();
    FilterCreationGitMetadata gitMetadata = filterCreationParams.getFilterCreationGitMetadata();
    if (!shouldPublishSetupUsage(pipelineEntity, gitMetadata)) {
      return;
    }
    log.info(String.format("Publishing setup usages for pipeline [%s] in repo [%s] in default branch",
        pipelineEntity.getIdentifier(), pipelineEntity.getRepo()));
    if (EmptyPredicate.isEmpty(referredEntities)) {
      deleteSetupUsagesForGivenPipeline(pipelineEntity);
      return;
    }
    EntityDetailProtoDTO pipelineDetails = populateEntityDetailProtoDTO(pipelineEntity, gitMetadata);
    Map<String, List<EntityDetailProtoDTO>> referredEntityTypeToReferredEntities = new HashMap<>();
    for (EntityDetailProtoDTO entityDetailProtoDTO : referredEntities) {
      List<EntityDetailProtoDTO> entityDetailProtoDTOS =
          referredEntityTypeToReferredEntities.getOrDefault(entityDetailProtoDTO.getType().name(), new ArrayList<>());
      entityDetailProtoDTOS.add(entityDetailProtoDTO);
      referredEntityTypeToReferredEntities.put(entityDetailProtoDTO.getType().name(), entityDetailProtoDTOS);
    }

    for (Map.Entry<String, List<EntityDetailProtoDTO>> entry : referredEntityTypeToReferredEntities.entrySet()) {
      List<EntityDetailProtoDTO> entityDetailProtoDTOs = entry.getValue();
      List<EntityDetailWithSetupUsageDetailProtoDTO> entityDetailWithSetupUsageDetailProtoDTOS =
          convertToReferredEntityWithSetupUsageDetail(entityDetailProtoDTOs,
              Objects.requireNonNull(SetupUsageDetailType.getTypeFromEntityTypeProtoEnumName(entry.getKey())).name(),
              pipelineEntity.getIdentifier());
      EntitySetupUsageCreateV2DTO entityReferenceDTO =
          EntitySetupUsageCreateV2DTO.newBuilder()
              .setAccountIdentifier(pipelineEntity.getAccountId())
              .setReferredByEntity(pipelineDetails)
              .addAllReferredEntityWithSetupUsageDetail(entityDetailWithSetupUsageDetailProtoDTOS)
              .setDeleteOldReferredByRecords(true)
              .build();
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", pipelineEntity.getAccountId(),
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, entry.getKey(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    }

    // This is being added to handle the case for entities which were earlier present but have been removed in updated
    // pipeline.Example: envGroup was initially used in pipeline but later environment is being used
    for (EntityTypeProtoEnum key : entityTypesSupportedByNGCore) {
      if (!referredEntityTypeToReferredEntities.containsKey(key.name())) {
        EntitySetupUsageCreateV2DTO entityReferenceDTO =
            EntitySetupUsageCreateV2DTO.newBuilder()
                .setAccountIdentifier(pipelineEntity.getAccountIdentifier())
                .setReferredByEntity(pipelineDetails)
                .setDeleteOldReferredByRecords(true)
                .build();
        try {
          eventProducer.send(
              Message.newBuilder()
                  .putAllMetadata(ImmutableMap.of("accountId", pipelineEntity.getAccountIdentifier(),
                      EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, key.name(),
                      EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
                  .setData(entityReferenceDTO.toByteString())
                  .build());
        } catch (Exception ex) {
          log.error("Error deleting the setup usages for the connector with the identifier {} in project {} in org {}",
              pipelineEntity.getIdentifier(), pipelineEntity.getAccountIdentifier(), pipelineEntity.getOrgIdentifier());
        }
      }
    }
  }

  @VisibleForTesting
  boolean shouldPublishSetupUsage(PipelineEntity pipelineEntity, FilterCreationGitMetadata gitMetadata) {
    if (!StoreType.REMOTE.equals(pipelineEntity.getStoreType())
        && !StoreType.REMOTE.equals(GitAwareContextHelper.getStoreTypeFromGitContext())) {
      return true;
    } else {
      return gitMetadata != null && gitMetadata.isGitDefaultBranch();
    }
  }

  private EntityDetailProtoDTO populateEntityDetailProtoDTO(
      PipelineEntity pipelineEntity, FilterCreationGitMetadata gitMetadata) {
    EntityDetailProtoDTO pipelineDetails =
        EntityDetailProtoDTO.newBuilder()
            .setIdentifierRef(IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(pipelineEntity.getAccountId(),
                pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(),
                pipelineEntity.getIdentifier()))
            .setType(PIPELINES)
            .setName(pipelineEntity.getName())
            .build();
    if (gitMetadata != null) {
      pipelineDetails = EntityDetailProtoDTO.newBuilder(pipelineDetails)
                            .setEntityGitMetadata(EntityGitMetadata.newBuilder()
                                                      .setRepo(gitMetadata.getRepo())
                                                      .setBranch(gitMetadata.getBranch())
                                                      .build())
                            .build();
    }
    return pipelineDetails;
  }

  private List<EntityDetailWithSetupUsageDetailProtoDTO> convertToReferredEntityWithSetupUsageDetail(
      List<EntityDetailProtoDTO> entityDetailProtoDTOs, String setupUsageDetailType, String pipelineIdentifier) {
    List<EntityDetailWithSetupUsageDetailProtoDTO> res = new ArrayList<>();
    for (EntityDetailProtoDTO entityDetailProtoDTO : entityDetailProtoDTOs) {
      String fqn;
      if (EntityTypeProtoEnum.TEMPLATE.equals(entityDetailProtoDTO.getType())) {
        fqn = entityDetailProtoDTO.getTemplateRef().getMetadataMap().get(PreFlightCheckMetadata.FQN);
      } else {
        fqn = entityDetailProtoDTO.getIdentifierRef().getMetadataMap().get(PreFlightCheckMetadata.FQN);
      }

      EntityReferredByPipelineDetailProtoDTO entityReferredByPipelineDetailProtoDTO =
          getSetupDetailProtoDTO(fqn, pipelineIdentifier);
      res.add(EntityDetailWithSetupUsageDetailProtoDTO.newBuilder()
                  .setReferredEntity(entityDetailProtoDTO)
                  .setType(setupUsageDetailType)
                  .setEntityInPipelineDetail(entityReferredByPipelineDetailProtoDTO)
                  .build());
    }
    return res;
  }

  private EntityReferredByPipelineDetailProtoDTO getSetupDetailProtoDTO(String fqn, String pipelineIdentifier) {
    if (isEmpty(fqn)) {
      return EntityReferredByPipelineDetailProtoDTO.newBuilder()
          .setIdentifier(pipelineIdentifier)
          .setType(PipelineDetailType.PIPELINE_IDENTIFIER)
          .build();
    }

    String stageIdentifier = YamlUtils.getStageIdentifierFromFqn(fqn);
    if (stageIdentifier != null) {
      return EntityReferredByPipelineDetailProtoDTO.newBuilder()
          .setIdentifier(stageIdentifier)
          .setType(PipelineDetailType.STAGE_IDENTIFIER)
          .build();
    } else {
      String variableName = YamlUtils.getPipelineVariableNameFromFqn(fqn);
      if (isNotEmpty(variableName)) {
        return EntityReferredByPipelineDetailProtoDTO.newBuilder()
            .setIdentifier(variableName)
            .setType(PipelineDetailType.VARIABLE_NAME)
            .build();
      } else {
        return EntityReferredByPipelineDetailProtoDTO.newBuilder()
            .setIdentifier(pipelineIdentifier)
            .setType(PipelineDetailType.PIPELINE_IDENTIFIER)
            .build();
      }
    }
  }

  private void deleteSetupUsagesForGivenPipeline(PipelineEntity pipelineEntity) {
    EntityDetailProtoDTO pipelineDetails =
        EntityDetailProtoDTO.newBuilder()
            .setIdentifierRef(IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(pipelineEntity.getAccountId(),
                pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(),
                pipelineEntity.getIdentifier()))
            .setType(EntityTypeProtoEnum.PIPELINES)
            .setName(pipelineEntity.getName())
            .build();

    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(pipelineEntity.getAccountId())
                                                         .setReferredByEntity(pipelineDetails)
                                                         .addAllReferredEntities(new ArrayList<>())
                                                         .setDeleteOldReferredByRecords(true)
                                                         .build();
    // Send Events for all refferredEntitiesType so as to delete them
    for (EntityTypeProtoEnum protoEnum : EntityTypeProtoEnum.values()) {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", pipelineEntity.getAccountId(),
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, protoEnum.name(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    }
  }

  @Override
  public void onDelete(PipelineDeleteEvent pipelineDeleteEvent) {
    try {
      deleteSetupUsagesForGivenPipeline(pipelineDeleteEvent.getPipeline());
    } catch (EventsFrameworkDownException ex) {
      log.error("Redis Producer shutdown", ex);
    }
  }
}
