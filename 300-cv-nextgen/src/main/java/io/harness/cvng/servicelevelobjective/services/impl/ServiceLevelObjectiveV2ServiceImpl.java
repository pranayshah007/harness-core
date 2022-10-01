/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.notification.beans.NotificationRuleRef;
import io.harness.cvng.notification.beans.NotificationRuleRefDTO;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2Response;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective.AbstractServiceLevelObjectiveUpdatableEntity;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective.SimpleServiceLevelObjectiveKeys;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.SLOTargetTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelobjectivev2.SLOV2Transformer;
import io.harness.exception.DuplicateFieldException;
import io.harness.persistence.HPersistence;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class ServiceLevelObjectiveV2ServiceImpl implements ServiceLevelObjectiveV2Service {
  @Inject private HPersistence hPersistence;

  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private Clock clock;
  @Inject private Map<SLOTargetType, SLOTargetTransformer> sloTargetTypeSLOTargetTransformerMap;
  @Inject private Map<ServiceLevelObjectiveType, SLOV2Transformer> serviceLevelObjectiveTypeSLOV2TransformerMap;
  @Inject private NotificationRuleService notificationRuleService;
  @Inject
  private Map<ServiceLevelObjectiveType, AbstractServiceLevelObjectiveUpdatableEntity>
      serviceLevelObjectiveTypeUpdatableEntityTransformerMap;

  @Override
  public ServiceLevelObjectiveV2Response create(
      ProjectParams projectParams, ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO) {
    validateCreate(serviceLevelObjectiveDTO, projectParams);
    MonitoredService monitoredService = monitoredServiceService.getMonitoredService(
        MonitoredServiceParams.builderWithProjectParams(projectParams)
            .monitoredServiceIdentifier(serviceLevelObjectiveDTO.getMonitoredServiceRef())
            .build());
    AbstractServiceLevelObjective serviceLevelObjective =
        saveServiceLevelObjectiveV2Entity(projectParams, serviceLevelObjectiveDTO, monitoredService.isEnabled());
    return getSLOResponse(serviceLevelObjective.getIdentifier(), projectParams);
  }

  @Override
  public ServiceLevelObjectiveV2Response update(ProjectParams projectParams, String identifier,
      ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO, List<String> serviceLevelIndicators) {
    Preconditions.checkArgument(identifier.equals(serviceLevelObjectiveDTO.getIdentifier()),
        String.format("Identifier %s does not match with path identifier %s", serviceLevelObjectiveDTO.getIdentifier(),
            identifier));
    AbstractServiceLevelObjective serviceLevelObjective =
        getEntity(projectParams, serviceLevelObjectiveDTO.getIdentifier());
    if (serviceLevelObjective == null) {
      log.error(String.format(
          "[SLOV2 Not Found] SLO  with identifier %s, accountId %s, orgIdentifier %s and projectIdentifier %s  is not present",
          serviceLevelObjectiveDTO.getIdentifier(), projectParams.getAccountIdentifier(),
          projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier()));
      return ServiceLevelObjectiveV2Response.builder().build();
    }
    validate(serviceLevelObjectiveDTO, projectParams);
    updateSLOV2Entity(projectParams, serviceLevelObjective, serviceLevelObjectiveDTO, serviceLevelIndicators);
    return getSLOResponse(serviceLevelObjectiveDTO.getIdentifier(), projectParams);
  }

  @Override
  public AbstractServiceLevelObjective getEntity(ProjectParams projectParams, String identifier) {
    return hPersistence.createQuery(AbstractServiceLevelObjective.class)
        .filter(ServiceLevelObjectiveV2Keys.accountId, projectParams.getAccountIdentifier())
        .filter(ServiceLevelObjectiveV2Keys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(ServiceLevelObjectiveV2Keys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(ServiceLevelObjectiveV2Keys.identifier, identifier)
        .get();
  }

  @Override
  public void deleteByProjectIdentifier(
      Class<AbstractServiceLevelObjective> clazz, String accountId, String orgIdentifier, String projectIdentifier) {
    List<AbstractServiceLevelObjective> serviceLevelObjectives =
        hPersistence.createQuery(AbstractServiceLevelObjective.class)
            .filter(ServiceLevelObjectiveV2Keys.accountId, accountId)
            .filter(ServiceLevelObjectiveV2Keys.orgIdentifier, orgIdentifier)
            .filter(ServiceLevelObjectiveV2Keys.projectIdentifier, projectIdentifier)
            .asList();

    serviceLevelObjectives.forEach(serviceLevelObjective -> {
      delete(ProjectParams.builder()
                 .accountIdentifier(serviceLevelObjective.getAccountId())
                 .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
                 .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                 .build(),
          serviceLevelObjective.getIdentifier());
    });
  }

  @Override
  public void deleteByOrgIdentifier(
      Class<AbstractServiceLevelObjective> clazz, String accountId, String orgIdentifier) {
    List<AbstractServiceLevelObjective> serviceLevelObjectives =
        hPersistence.createQuery(AbstractServiceLevelObjective.class)
            .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.accountId, accountId)
            .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.orgIdentifier, orgIdentifier)
            .asList();

    serviceLevelObjectives.forEach(serviceLevelObjective -> {
      delete(ProjectParams.builder()
                 .accountIdentifier(serviceLevelObjective.getAccountId())
                 .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
                 .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                 .build(),
          serviceLevelObjective.getIdentifier());
    });
  }

  @Override
  public void deleteByAccountIdentifier(Class<AbstractServiceLevelObjective> clazz, String accountId) {
    List<AbstractServiceLevelObjective> serviceLevelObjectives =
        hPersistence.createQuery(AbstractServiceLevelObjective.class)
            .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.accountId, accountId)
            .asList();

    serviceLevelObjectives.forEach(serviceLevelObjective -> {
      delete(ProjectParams.builder()
                 .accountIdentifier(serviceLevelObjective.getAccountId())
                 .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
                 .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                 .build(),
          serviceLevelObjective.getIdentifier());
    });
  }

  @Override
  public boolean delete(ProjectParams projectParams, String identifier) {
    AbstractServiceLevelObjective serviceLevelObjectiveV2 = getEntity(projectParams, identifier);
    if (serviceLevelObjectiveV2 == null) {
      log.error(String.format(
          "[SLOV2 Not Found] SLO  with identifier %s, accountId %s, orgIdentifier %s and projectIdentifier %s  is not present",
          identifier, projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
          projectParams.getProjectIdentifier()));
      return false;
    }
    return hPersistence.delete(serviceLevelObjectiveV2);
  }

  @Override
  public void setMonitoredServiceSLOsEnableFlag(
      ProjectParams projectParams, String monitoredServiceIdentifier, boolean isEnabled) {
    hPersistence.update(
        hPersistence.createQuery(SimpleServiceLevelObjective.class)
            .filter(ServiceLevelObjectiveV2Keys.accountId, projectParams.getAccountIdentifier())
            .filter(ServiceLevelObjectiveV2Keys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(ServiceLevelObjectiveV2Keys.projectIdentifier, projectParams.getProjectIdentifier())
            .filter(SimpleServiceLevelObjectiveKeys.monitoredServiceIdentifier, monitoredServiceIdentifier),
        hPersistence.createUpdateOperations(SimpleServiceLevelObjective.class)
            .set(ServiceLevelObjectiveV2Keys.enabled, isEnabled));
  }

  @Override
  public void updateNotificationRuleRefInSLO(ProjectParams projectParams,
      AbstractServiceLevelObjective serviceLevelObjective, List<String> notificationRuleRefs) {
    List<NotificationRuleRef> allNotificationRuleRefs = new ArrayList<>();
    List<NotificationRuleRef> notificationRuleRefsWithoutChange =
        serviceLevelObjective.getNotificationRuleRefs()
            .stream()
            .filter(notificationRuleRef -> !notificationRuleRefs.contains(notificationRuleRef.getNotificationRuleRef()))
            .collect(Collectors.toList());
    List<NotificationRuleRefDTO> notificationRuleRefDTOs =
        notificationRuleRefs.stream()
            .map(notificationRuleRef
                -> NotificationRuleRefDTO.builder().notificationRuleRef(notificationRuleRef).enabled(true).build())
            .collect(Collectors.toList());
    List<NotificationRuleRef> notificationRuleRefsWithChange = notificationRuleService.getNotificationRuleRefs(
        projectParams, notificationRuleRefDTOs, NotificationRuleType.SLO, clock.instant());
    allNotificationRuleRefs.addAll(notificationRuleRefsWithChange);
    allNotificationRuleRefs.addAll(notificationRuleRefsWithoutChange);
    UpdateOperations<AbstractServiceLevelObjective> updateOperations =
        hPersistence.createUpdateOperations(AbstractServiceLevelObjective.class);
    updateOperations.set(
        AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.notificationRuleRefs, allNotificationRuleRefs);

    hPersistence.update(serviceLevelObjective, updateOperations);
  }

  private AbstractServiceLevelObjective updateSLOV2Entity(ProjectParams projectParams,
      AbstractServiceLevelObjective abstractServiceLevelObjective,
      ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO, List<String> serviceLevelIndicators) {
    UpdateOperations<AbstractServiceLevelObjective> updateOperations =
        hPersistence.createUpdateOperations(AbstractServiceLevelObjective.class);
    serviceLevelObjectiveTypeUpdatableEntityTransformerMap.get(abstractServiceLevelObjective.getType())
        .setUpdateOperations(updateOperations, serviceLevelObjectiveV2DTO);
    updateOperations.set(ServiceLevelObjectiveV2Keys.sloTarget,
        sloTargetTypeSLOTargetTransformerMap.get(serviceLevelObjectiveV2DTO.getSloTarget().getType())
            .getSLOTarget(serviceLevelObjectiveV2DTO.getSloTarget().getSpec()));
    updateOperations.set(ServiceLevelObjectiveV2Keys.notificationRuleRefs,
        getNotificationRuleRefs(projectParams, abstractServiceLevelObjective, serviceLevelObjectiveV2DTO));
    hPersistence.update(abstractServiceLevelObjective, updateOperations);
    abstractServiceLevelObjective = getEntity(projectParams, abstractServiceLevelObjective.getIdentifier());
    return abstractServiceLevelObjective;
  }

  private List<NotificationRuleRef> getNotificationRuleRefs(ProjectParams projectParams,
      AbstractServiceLevelObjective serviceLevelObjective, ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO) {
    List<NotificationRuleRef> notificationRuleRefs = notificationRuleService.getNotificationRuleRefs(projectParams,
        serviceLevelObjectiveV2DTO.getNotificationRuleRefs(), NotificationRuleType.SLO, Instant.ofEpochSecond(0));
    deleteNotificationRuleRefs(projectParams, serviceLevelObjective, notificationRuleRefs);
    return notificationRuleRefs;
  }

  private void deleteNotificationRuleRefs(ProjectParams projectParams,
      AbstractServiceLevelObjective serviceLevelObjective, List<NotificationRuleRef> notificationRuleRefs) {
    List<String> existingNotificationRuleRefs = serviceLevelObjective.getNotificationRuleRefs()
                                                    .stream()
                                                    .map(NotificationRuleRef::getNotificationRuleRef)
                                                    .collect(Collectors.toList());
    List<String> updatedNotificationRuleRefs =
        notificationRuleRefs.stream().map(NotificationRuleRef::getNotificationRuleRef).collect(Collectors.toList());
    notificationRuleService.deleteNotificationRuleRefs(
        projectParams, existingNotificationRuleRefs, updatedNotificationRuleRefs);
  }

  private ServiceLevelObjectiveV2Response getSLOResponse(String identifier, ProjectParams projectParams) {
    AbstractServiceLevelObjective serviceLevelObjective = getEntity(projectParams, identifier);

    return sloEntityToSLOResponse(serviceLevelObjective);
  }

  private ServiceLevelObjectiveV2Response sloEntityToSLOResponse(AbstractServiceLevelObjective serviceLevelObjective) {
    ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO =
        serviceLevelObjectiveTypeSLOV2TransformerMap.get(serviceLevelObjective.getType())
            .getSLOV2DTO(serviceLevelObjective);
    return ServiceLevelObjectiveV2Response.builder()
        .serviceLevelObjectiveV2DTO(serviceLevelObjectiveDTO)
        .createdAt(serviceLevelObjective.getCreatedAt())
        .lastModifiedAt(serviceLevelObjective.getLastUpdatedAt())
        .build();
  }

  private AbstractServiceLevelObjective saveServiceLevelObjectiveV2Entity(
      ProjectParams projectParams, ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO, boolean isEnabled) {
    AbstractServiceLevelObjective serviceLevelObjectiveV2 =
        serviceLevelObjectiveTypeSLOV2TransformerMap.get(serviceLevelObjectiveV2DTO.getType())
            .getSLOV2(projectParams, serviceLevelObjectiveV2DTO, isEnabled);
    hPersistence.save(serviceLevelObjectiveV2);
    return serviceLevelObjectiveV2;
  }

  public void validateCreate(ServiceLevelObjectiveV2DTO sloCreateDTO, ProjectParams projectParams) {
    AbstractServiceLevelObjective serviceLevelObjective = getEntity(projectParams, sloCreateDTO.getIdentifier());
    if (serviceLevelObjective != null) {
      throw new DuplicateFieldException(String.format(
          "serviceLevelObjectiveV2 with identifier %s and orgIdentifier %s and projectIdentifier %s is already present",
          sloCreateDTO.getIdentifier(), projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier()));
    }
    validate(sloCreateDTO, projectParams);
  }

  private void validate(ServiceLevelObjectiveV2DTO sloCreateDTO, ProjectParams projectParams) {
    monitoredServiceService.get(projectParams, sloCreateDTO.getMonitoredServiceRef());
  }
}
