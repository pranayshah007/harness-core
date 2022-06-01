/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.services.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.UpdatableEntity;
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
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.persistence.HPersistence;
import io.harness.utils.PageUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;

public class NotificationRuleServiceImpl implements NotificationRuleService {
  @Inject private HPersistence hPersistence;
  @Inject
  private Map<NotificationRuleType, NotificationRuleConditionTransformer>
      notificationRuleTypeNotificationRuleConditionTransformerMap;
  @Inject private Map<NotificationRuleType, NotificationRuleUpdatableEntity> notificationRuleMapBinder;
  @Inject Clock clock;

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
    if (isNotEmpty(notificationRules)) {
      throw new InvalidRequestException(String.format(
          "NotificationRule  with identifier %s, accountId %s, orgIdentifier %s and projectIdentifier %s  is not present",
          identifier, projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
          projectParams.getProjectIdentifier()));
    }
    // TODO: all the references of this notificationRule should also be deleted e.g. inside SLO and MonitoredService
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
  public PageResponse<NotificationRuleResponse> get(ProjectParams projectParams, Integer pageNumber, Integer pageSize) {
    Query<NotificationRule> notificationRuleQuery =
        hPersistence.createQuery(NotificationRule.class)
            .disableValidation()
            .filter(NotificationRuleKeys.accountId, projectParams.getAccountIdentifier())
            .filter(NotificationRuleKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(NotificationRuleKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .order(Sort.descending(NotificationRuleKeys.lastUpdatedAt));
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
  public List<NotificationRuleRef> getNotificationRuleRefs(List<NotificationRuleRefDTO> notificationRuleRefDTOS) {
    return notificationRuleRefDTOS.stream()
        .map(notificationRuleRefDTO
            -> NotificationRuleRef.builder()
                   .notificationRuleRef(notificationRuleRefDTO.getNotificationRuleRef())
                   .enabled(notificationRuleRefDTO.isEnabled())
                   .lastSuccessFullNotificationSent(clock.instant())
                   .build())
        .collect(Collectors.toList());
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

  private void validateCreate(ProjectParams projectParams, NotificationRuleDTO notificationRuleDTO) {
    NotificationRule notificationRule = getEntity(projectParams, notificationRuleDTO.getIdentifier());
    if (notificationRule != null) {
      throw new DuplicateFieldException(String.format(
          "notificationRule with identifier %s and orgIdentifier %s and projectIdentifier %s is already present",
          notificationRuleDTO.getIdentifier(), projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier()));
    }
  }

  private NotificationRuleResponse getNotificationRuleResponse(ProjectParams projectParams, String identifier) {
    NotificationRule notificationRule = getEntity(projectParams, identifier);
    return notificationRuleEntityToResponse(notificationRule);
  }

  private NotificationRuleResponse notificationRuleEntityToResponse(NotificationRule notificationRule) {
    NotificationRuleDTO notificationRuleDTO =
        NotificationRuleDTO.builder()
            .orgIdentifier(notificationRule.getOrgIdentifier())
            .projectIdentifier(notificationRule.getProjectIdentifier())
            .identifier(notificationRule.getIdentifier())
            .name(notificationRule.getName())
            .type(notificationRule.getType())
            .notificationMethod(notificationRule.getNotificationMethod())
            .conditions(notificationRuleTypeNotificationRuleConditionTransformerMap.get(notificationRule.getType())
                            .getDto(notificationRule)
                            .getConditions())
            .build();
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
