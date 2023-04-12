/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.UpdatableEntity;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.notification.beans.NotificationRuleDTO;
import io.harness.cvng.notification.beans.NotificationRuleRef;
import io.harness.cvng.notification.beans.NotificationRuleRefDTO;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.cvng.notification.entities.NotificationRule.NotificationRuleKeys;
import io.harness.cvng.notification.entities.NotificationRule.NotificationRuleUpdatableEntity;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.cvng.notification.transformer.NotificationRuleConditionTransformer;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.persistence.HPersistence;
import io.harness.utils.PageUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class NotificationRuleServiceImpl implements NotificationRuleService {
  @Inject private HPersistence hPersistence;
  @Inject
  private Map<NotificationRuleType, NotificationRuleConditionTransformer>
      notificationRuleTypeNotificationRuleConditionTransformerMap;
  @Inject private Map<NotificationRuleType, NotificationRuleUpdatableEntity> notificationRuleMapBinder;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject private MonitoredServiceService monitoredServiceService;

  @Override
  public NotificationRuleResponse create(ProjectParams projectParams, NotificationRuleDTO notificationRuleDTO) {
    validateCreate(projectParams, notificationRuleDTO);
    NotificationRule notificationRule =
        notificationRuleTypeNotificationRuleConditionTransformerMap.get(notificationRuleDTO.getType())
            .getEntity(projectParams, notificationRuleDTO);
    hPersistence.save(notificationRule);
    return getNotificationRuleResponse(projectParams, notificationRuleDTO.getIdentifier());
  }

  @Override
  public List<NotificationRule> getEntities(ProjectParams projectParams, List<String> identifiers) {
    return hPersistence.createQuery(NotificationRule.class)
        .filter(NotificationRuleKeys.accountId, projectParams.getAccountIdentifier())
        .filter(NotificationRuleKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(NotificationRuleKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .field(NotificationRuleKeys.identifier)
        .in(identifiers)
        .asList();
  }

  @Override
  public NotificationRule getEntity(ProjectParams projectParams, String identifier) {
    return hPersistence.createQuery(NotificationRule.class)
        .filter(NotificationRuleKeys.accountId, projectParams.getAccountIdentifier())
        .filter(NotificationRuleKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(NotificationRuleKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(NotificationRuleKeys.identifier, identifier)
        .get();
  }

  @Override
  public NotificationRuleResponse update(
      ProjectParams projectParams, String identifier, NotificationRuleDTO notificationRuleDTO) {
    Preconditions.checkArgument(identifier.equals(notificationRuleDTO.getIdentifier()),
        String.format(
            "Identifier %s does not match with path identifier %s", notificationRuleDTO.getIdentifier(), identifier));
    if (getEntity(projectParams, notificationRuleDTO.getIdentifier()) == null) {
      throw new InvalidRequestException(String.format(
          "NotificationRule  with identifier %s, accountId %s, orgIdentifier %s and projectIdentifier %s  is not present",
          notificationRuleDTO.getIdentifier(), projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
          projectParams.getProjectIdentifier()));
    }
    NotificationRule notificationRule = getEntity(projectParams, identifier);
    UpdateOperations<NotificationRule> updateOperations = hPersistence.createUpdateOperations(NotificationRule.class);
    NotificationRule newNotificationRule =
        notificationRuleTypeNotificationRuleConditionTransformerMap.get(notificationRuleDTO.getType())
            .getEntity(projectParams, notificationRuleDTO);
    UpdatableEntity<NotificationRule, NotificationRule> updatableEntity =
        notificationRuleMapBinder.get(notificationRuleDTO.getType());
    updatableEntity.setUpdateOperations(updateOperations, newNotificationRule);
    hPersistence.update(notificationRule, updateOperations);
    return getNotificationRuleResponse(projectParams, notificationRuleDTO.getIdentifier());
  }

  @Override
  public Boolean delete(ProjectParams projectParams, String identifier) {
    List<NotificationRule> notificationRules = getEntities(projectParams, Arrays.asList(identifier));
    if (isEmpty(notificationRules)) {
      throw new InvalidRequestException(String.format(
          "NotificationRule  with identifier %s, accountId %s, orgIdentifier %s and projectIdentifier %s  is not present",
          identifier, projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
          projectParams.getProjectIdentifier()));
    }
    serviceLevelObjectiveV2Service.beforeNotificationRuleDelete(projectParams, identifier);
    monitoredServiceService.beforeNotificationRuleDelete(projectParams, identifier);
    return hPersistence.delete(notificationRules.get(0));
  }

  @Override
  public void delete(ProjectParams projectParams, List<String> identifiers) {
    if (isNotEmpty(identifiers)) {
      Query<NotificationRule> notificationRulesQuery =
          hPersistence.createQuery(NotificationRule.class, excludeAuthority)
              .filter(NotificationRuleKeys.accountId, projectParams.getAccountIdentifier())
              .filter(NotificationRuleKeys.orgIdentifier, projectParams.getOrgIdentifier())
              .filter(NotificationRuleKeys.projectIdentifier, projectParams.getProjectIdentifier())
              .field(NotificationRuleKeys.identifier)
              .in(identifiers);
      hPersistence.delete(notificationRulesQuery);
    }
  }

  @Override
  public PageResponse<NotificationRuleResponse> get(
      ProjectParams projectParams, List<String> notificationRuleIdentifiers, Integer pageNumber, Integer pageSize) {
    Query<NotificationRule> notificationRuleQuery =
        hPersistence.createQuery(NotificationRule.class)
            .disableValidation()
            .filter(NotificationRuleKeys.accountId, projectParams.getAccountIdentifier())
            .filter(NotificationRuleKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(NotificationRuleKeys.projectIdentifier, projectParams.getProjectIdentifier());
    if (isNotEmpty(notificationRuleIdentifiers)) {
      notificationRuleQuery =
          notificationRuleQuery.field(NotificationRuleKeys.identifier).in(notificationRuleIdentifiers);
    }
    notificationRuleQuery = notificationRuleQuery.order(Sort.descending(NotificationRuleKeys.lastUpdatedAt));
    List<NotificationRule> notificationRuleList = notificationRuleQuery.asList();

    PageResponse<NotificationRule> notificationRuleEntitiesPageResponse =
        PageUtils.offsetAndLimit(notificationRuleList, pageNumber, pageSize);
    List<NotificationRuleResponse> notificationRulePageResponse = notificationRuleEntitiesPageResponse.getContent()
                                                                      .stream()
                                                                      .map(this::notificationRuleEntityToResponse)
                                                                      .collect(Collectors.toList());
    return PageResponse.<NotificationRuleResponse>builder()
        .pageSize(pageSize)
        .pageIndex(pageNumber)
        .totalPages(notificationRuleEntitiesPageResponse.getTotalPages())
        .totalItems(notificationRuleEntitiesPageResponse.getTotalItems())
        .pageItemCount(notificationRuleEntitiesPageResponse.getPageItemCount())
        .content(notificationRulePageResponse)
        .build();
  }

  @Override
  public List<NotificationRuleRef> getNotificationRuleRefs(ProjectParams projectParams,
      List<NotificationRuleRefDTO> notificationRuleRefDTOS, NotificationRuleType type,
      Instant lastSuccessfullNotificationTime) {
    List<NotificationRule> notificationRules = getEntities(projectParams,
        notificationRuleRefDTOS.stream().map(ref -> ref.getNotificationRuleRef()).collect(Collectors.toList()));
    for (NotificationRule rule : notificationRules) {
      if (rule.getType() != type) {
        throw new InvalidArgumentsException(
            String.format("NotificationRule with identifier %s is of type %s and cannot be added into %s",
                rule.getIdentifier(), rule.getType(), type));
      }
    }
    return notificationRuleRefDTOS.stream()
        .map(notificationRuleRefDTO
            -> NotificationRuleRef.builder()
                   .notificationRuleRef(notificationRuleRefDTO.getNotificationRuleRef())
                   .enabled(notificationRuleRefDTO.isEnabled())
                   .lastSuccessFullNotificationSent(lastSuccessfullNotificationTime)
                   .build())
        .collect(Collectors.toList());
  }

  @Override
  public void deleteNotificationRuleRefs(ProjectParams projectParams, List<String> existingNotificationRuleRefs,
      List<String> updatedNotificationRuleRefs) {
    List<String> toBeDeletedNotificationRuleRefs = new ArrayList<>();
    for (String notificationRuleRef : existingNotificationRuleRefs) {
      if (!updatedNotificationRuleRefs.contains(notificationRuleRef)) {
        toBeDeletedNotificationRuleRefs.add(notificationRuleRef);
      }
    }
    delete(projectParams, toBeDeletedNotificationRuleRefs);
  }
  @Override
  public List<NotificationRuleRefDTO> getNotificationRuleRefDTOs(List<NotificationRuleRef> notificationRuleRefs) {
    return notificationRuleRefs.stream()
        .map(notificationRuleRef
            -> NotificationRuleRefDTO.builder()
                   .notificationRuleRef(notificationRuleRef.getNotificationRuleRef())
                   .enabled(notificationRuleRef.isEnabled())
                   .build())
        .collect(Collectors.toList());
  }

  @Override
  public List<NotificationRuleResponse> getNotificationRuleResponse(
      ProjectParams projectParams, List<NotificationRuleRef> notificationRuleRefList) {
    if (!isNotEmpty(notificationRuleRefList)) {
      return Collections.emptyList();
    }
    Map<String, Boolean> NOTIFICATION_RULE_REF_TO_ENABLED_MAP = new HashMap<>();
    notificationRuleRefList.forEach(
        ref -> NOTIFICATION_RULE_REF_TO_ENABLED_MAP.put(ref.getNotificationRuleRef(), ref.isEnabled()));

    List<NotificationRule> notificationRuleList =
        getEntities(projectParams, new ArrayList<>(NOTIFICATION_RULE_REF_TO_ENABLED_MAP.keySet()));
    return notificationRuleList.stream()
        .map(notificationRule
            -> notificationRuleEntityToResponse(
                notificationRule, NOTIFICATION_RULE_REF_TO_ENABLED_MAP.get(notificationRule.getIdentifier())))
        .collect(Collectors.toList());
  }

  @Override
  public void validateNotification(
      List<NotificationRuleRefDTO> notificationRuleRefDTOList, ProjectParams projectParams) {
    List<String> inputNotificationIdentifierList =
        notificationRuleRefDTOList.stream()
            .map(notificationRuleRefDTO -> notificationRuleRefDTO.getNotificationRuleRef())
            .collect(Collectors.toList());
    Set<String> dbNotificationIdentifierSet = getEntities(projectParams, inputNotificationIdentifierList)
                                                  .stream()
                                                  .map(notificationRule -> notificationRule.getIdentifier())
                                                  .collect(Collectors.toSet());

    inputNotificationIdentifierList.forEach(notificationIdentifier -> {
      if (!dbNotificationIdentifierSet.contains(notificationIdentifier)) {
      }
    });
    inputNotificationIdentifierList =
        inputNotificationIdentifierList.stream()
            .filter(notificationIdentifier -> !dbNotificationIdentifierSet.contains(notificationIdentifier))
            .collect(Collectors.toList());
    if (!inputNotificationIdentifierList.isEmpty()) {
      throw new InvalidArgumentsException(String.format(
          "NotificationRule does not exist for identifier: %s", String.join(",", inputNotificationIdentifierList)));
    }
  }

  private void validateCreate(ProjectParams projectParams, NotificationRuleDTO notificationRuleDTO) {
    NotificationRule notificationRule = getEntity(projectParams, notificationRuleDTO.getIdentifier());
    if (notificationRule != null) {
      throw new DuplicateFieldException(String.format(
          "NotificationRule with identifier %s and orgIdentifier %s and projectIdentifier %s is already present",
          notificationRuleDTO.getIdentifier(), projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier()));
    }
  }

  private NotificationRuleResponse getNotificationRuleResponse(ProjectParams projectParams, String identifier) {
    NotificationRule notificationRule = getEntity(projectParams, identifier);
    return notificationRuleEntityToResponse(notificationRule);
  }

  private NotificationRuleResponse notificationRuleEntityToResponse(NotificationRule notificationRule) {
    NotificationRuleDTO notificationRuleDTO =
        notificationRuleTypeNotificationRuleConditionTransformerMap.get(notificationRule.getType())
            .getDto(notificationRule);
    return NotificationRuleResponse.builder()
        .notificationRule(notificationRuleDTO)
        .createdAt(notificationRule.getCreatedAt())
        .lastModifiedAt(notificationRule.getLastUpdatedAt())
        .build();
  }

  private NotificationRuleResponse notificationRuleEntityToResponse(
      NotificationRule notificationRule, boolean enabled) {
    NotificationRuleDTO notificationRuleDTO =
        notificationRuleTypeNotificationRuleConditionTransformerMap.get(notificationRule.getType())
            .getDto(notificationRule);
    return NotificationRuleResponse.builder()
        .notificationRule(notificationRuleDTO)
        .enabled(enabled)
        .createdAt(notificationRule.getCreatedAt())
        .lastModifiedAt(notificationRule.getLastUpdatedAt())
        .build();
  }
}
