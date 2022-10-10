/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.service.impl;

import io.harness.freeze.beans.CurrentOrUpcomingActiveWindow;
import io.harness.freeze.beans.EntityConfig;
import io.harness.freeze.beans.FilterType;
import io.harness.freeze.beans.FreezeEntityRule;
import io.harness.freeze.beans.FreezeEntityType;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.entity.FreezeConfigEntity.FreezeConfigEntityKeys;
import io.harness.freeze.helpers.FreezeFilterHelper;
import io.harness.freeze.helpers.FreezeTimeUtils;
import io.harness.freeze.service.FreezeEvaluateService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@Slf4j
public class FreezeEvaluateServiceImpl implements FreezeEvaluateService {
  FreezeCRUDServiceImpl freezeCRUDService;

  @Inject
  public FreezeEvaluateServiceImpl(FreezeCRUDServiceImpl freezeCRUDService) {
    this.freezeCRUDService = freezeCRUDService;
  }

  public List<FreezeSummaryResponseDTO> getActiveFreezeEntities(
      String accountId, String orgIdentifier, String projectIdentifier, Map<FreezeEntityType, List<String>> entityMap) {
    List<FreezeSummaryResponseDTO> freezeSummaryResponseDTOList = new LinkedList<>();
    Criteria criteria = FreezeFilterHelper.createCriteriaForGetList(
        accountId, orgIdentifier, projectIdentifier, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    PageRequest pageRequest = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, FreezeConfigEntityKeys.createdAt));
    Page<FreezeSummaryResponseDTO> result = freezeCRUDService.list(criteria, pageRequest);
    List<FreezeSummaryResponseDTO> configs = result.getContent();
    for (FreezeSummaryResponseDTO freezeSummaryResponseDTO : configs) {
      CurrentOrUpcomingActiveWindow currentOrUpcomingActiveWindow =
          freezeSummaryResponseDTO.getCurrentOrUpcomingActiveWindow();
      if (FreezeTimeUtils.currentWindowIsActive(currentOrUpcomingActiveWindow)
          && matchesEntities(entityMap, freezeSummaryResponseDTO.getRules())) {
        freezeSummaryResponseDTOList.add(freezeSummaryResponseDTO);
      }
    }
    return freezeSummaryResponseDTOList;
  }

  public boolean globalFreezeActive(String accountId, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = FreezeFilterHelper.createCriteriaForGetList(
        accountId, orgIdentifier, projectIdentifier, null, FreezeType.GLOBAL, FreezeStatus.ENABLED, null, null);
    PageRequest pageRequest = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, FreezeConfigEntityKeys.createdAt));
    Page<FreezeSummaryResponseDTO> result = freezeCRUDService.list(criteria, pageRequest);
    List<FreezeSummaryResponseDTO> configs = result.getContent();
    for (FreezeSummaryResponseDTO freezeSummaryResponseDTO : configs) {
      CurrentOrUpcomingActiveWindow currentOrUpcomingActiveWindow =
          freezeSummaryResponseDTO.getCurrentOrUpcomingActiveWindow();
      if (FreezeTimeUtils.currentWindowIsActive(currentOrUpcomingActiveWindow)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean shouldDisableDeployment(String accountId, String orgIdentifier, String projectIdentifier) {
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
    entityMap.put(FreezeEntityType.ORG, Lists.newArrayList(orgIdentifier));
    entityMap.put(FreezeEntityType.PROJECT, Lists.newArrayList(projectIdentifier));
    return scopeFrozen(accountId, null, null, entityMap) || scopeFrozen(accountId, orgIdentifier, null, entityMap)
        || scopeFrozen(accountId, orgIdentifier, projectIdentifier, entityMap);
  }

  private boolean scopeFrozen(
      String accountId, String orgId, String projectId, Map<FreezeEntityType, List<String>> entityMap) {
    if (globalFreezeActive(accountId, orgId, projectId)) {
      return true;
    }

    Criteria criteria = FreezeFilterHelper.createCriteriaForGetList(
        accountId, orgId, projectId, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    PageRequest pageRequest = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, FreezeConfigEntityKeys.createdAt));
    Page<FreezeSummaryResponseDTO> result = freezeCRUDService.list(criteria, pageRequest);
    List<FreezeSummaryResponseDTO> configs = result.getContent();

    for (FreezeSummaryResponseDTO freezeSummaryResponseDTO : configs) {
      CurrentOrUpcomingActiveWindow currentOrUpcomingActiveWindow =
          freezeSummaryResponseDTO.getCurrentOrUpcomingActiveWindow();
      if (FreezeTimeUtils.currentWindowIsActive(currentOrUpcomingActiveWindow)
          && matchesEntities(entityMap, freezeSummaryResponseDTO.getRules())) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesEntities(Map<FreezeEntityType, List<String>> entityMap, List<FreezeEntityRule> rules) {
    for (FreezeEntityRule rule : rules) {
      if (matchesEntities(entityMap, rule)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesEntities(Map<FreezeEntityType, List<String>> entityMap, FreezeEntityRule rules) {
    List<EntityConfig> entities = rules.getEntityConfigList()
                                      .stream()
                                      .filter(entityConfig
                                          -> !FilterType.ALL.equals(entityConfig.getFilterType())
                                              && entityMap.containsKey(entityConfig.getFreezeEntityType()))
                                      .collect(Collectors.toList());
    for (EntityConfig entity : entities) {
      if (!matchesEntities(entityMap, entity)) {
        return false;
      }
    }
    return true;
  }

  private boolean matchesEntities(Map<FreezeEntityType, List<String>> entityMap, EntityConfig entityConfig) {
    if (FilterType.NOT_EQUALS.equals(entityConfig.getFilterType())) {
      return !entityConfig.getEntityReference().contains(entityMap.get(entityConfig.getEntityReference()));
    } else {
      return entityConfig.getEntityReference().contains(entityMap.get(entityConfig.getEntityReference()));
    }
  }
}
