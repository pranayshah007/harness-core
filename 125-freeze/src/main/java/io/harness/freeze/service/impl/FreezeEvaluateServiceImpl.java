/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.service.impl;

import static java.util.Objects.isNull;

import io.harness.encryption.Scope;
import io.harness.freeze.beans.CurrentOrUpcomingWindow;
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
import io.harness.freeze.mappers.NGFreezeDtoMapper;
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

  @Override
  public List<FreezeSummaryResponseDTO> getActiveFreezeEntities(
      String accountId, String orgIdentifier, String projectIdentifier, Map<FreezeEntityType, List<String>> entityMap) {
    List<FreezeSummaryResponseDTO> freezeSummaryResponseDTOList = new LinkedList<>();
    Criteria criteria = FreezeFilterHelper.createCriteriaForGetList(
        accountId, orgIdentifier, projectIdentifier, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    PageRequest pageRequest = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, FreezeConfigEntityKeys.createdAt));
    Page<FreezeSummaryResponseDTO> result = freezeCRUDService.list(criteria, pageRequest);
    List<FreezeSummaryResponseDTO> configs = result.getContent();
    for (FreezeSummaryResponseDTO freezeSummaryResponseDTO : configs) {
      CurrentOrUpcomingWindow currentOrUpcomingWindow = freezeSummaryResponseDTO.getCurrentOrUpcomingWindow();
      if (FreezeTimeUtils.currentWindowIsActive(currentOrUpcomingWindow)
          && matchesEntities(entityMap, freezeSummaryResponseDTO.getRules())) {
        freezeSummaryResponseDTOList.add(freezeSummaryResponseDTO);
      }
    }
    return freezeSummaryResponseDTOList;
  }

  @Override
  public List<FreezeSummaryResponseDTO> anyGlobalFreezeActive(
      String accountId, String orgIdentifier, String projectIdentifier) {
    Scope scope = NGFreezeDtoMapper.getScopeFromFreezeDto(orgIdentifier, projectIdentifier);
    List<FreezeSummaryResponseDTO> globalFreeze = new LinkedList<>();
    FreezeSummaryResponseDTO freezeSummaryResponseDTO;
    switch (scope) {
      case PROJECT:
        freezeSummaryResponseDTO = getGlobalFreezeIfActive(accountId, orgIdentifier, projectIdentifier);
        if (freezeSummaryResponseDTO != null) {
          globalFreeze.add(freezeSummaryResponseDTO);
        }
        // fallthrough to ignore
      case ORG:
        freezeSummaryResponseDTO = getGlobalFreezeIfActive(accountId, orgIdentifier, null);
        if (freezeSummaryResponseDTO != null) {
          globalFreeze.add(freezeSummaryResponseDTO);
        }
        // fallthrough to ignore
      case ACCOUNT:
        freezeSummaryResponseDTO = getGlobalFreezeIfActive(accountId, null, null);
        if (freezeSummaryResponseDTO != null) {
          globalFreeze.add(freezeSummaryResponseDTO);
        }
        return globalFreeze;
      default:
        return globalFreeze;
    }
  }

  private FreezeSummaryResponseDTO getGlobalFreezeIfActive(
      String accountId, String orgIdentifier, String projectIdentifier) {
    FreezeSummaryResponseDTO freezeSummaryResponseDTO =
        freezeCRUDService.getGlobalFreezeSummary(accountId, orgIdentifier, projectIdentifier);
    CurrentOrUpcomingWindow currentOrUpcomingWindow = freezeSummaryResponseDTO.getCurrentOrUpcomingWindow();
    if (FreezeStatus.ENABLED.equals(freezeSummaryResponseDTO.getStatus())
        && FreezeTimeUtils.currentWindowIsActive(currentOrUpcomingWindow)) {
      return freezeSummaryResponseDTO;
    }
    return null;
  }

  @Override
  public List<FreezeSummaryResponseDTO> getActiveFreezeEntities(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    List<FreezeSummaryResponseDTO> activeFreezeList = new LinkedList<>();
    activeFreezeList.addAll(anyGlobalFreezeActive(accountId, orgIdentifier, projectIdentifier));
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
    entityMap.put(FreezeEntityType.ORG, Lists.newArrayList(orgIdentifier));
    entityMap.put(FreezeEntityType.PROJECT, Lists.newArrayList(projectIdentifier));
    if (!isNull(pipelineIdentifier)) {
      entityMap.put(FreezeEntityType.PIPELINE, Lists.newArrayList(pipelineIdentifier));
    }
    Scope scope = NGFreezeDtoMapper.getScopeFromFreezeDto(orgIdentifier, projectIdentifier);
    switch (scope) {
      case PROJECT:
        activeFreezeList.addAll(getActiveFreezeEntities(accountId, orgIdentifier, projectIdentifier, entityMap));
        // fallthrough to ignore
      case ORG:
        activeFreezeList.addAll(getActiveFreezeEntities(accountId, orgIdentifier, null, entityMap));
        // fallthrough to ignore
      case ACCOUNT:
        activeFreezeList.addAll(getActiveFreezeEntities(accountId, null, null, entityMap));
        break;
      default:
        break;
    }
    return activeFreezeList;
  }

  @Override
  public List<FreezeSummaryResponseDTO> getActiveManualFreezeEntities(
      String accountId, String orgIdentifier, String projectIdentifier, Map<FreezeEntityType, List<String>> entityMap) {
    List<FreezeSummaryResponseDTO> activeFreezeList = new LinkedList<>();
    Scope scope = NGFreezeDtoMapper.getScopeFromFreezeDto(orgIdentifier, projectIdentifier);
    switch (scope) {
      case PROJECT:
        activeFreezeList.addAll(getActiveFreezeEntities(accountId, orgIdentifier, projectIdentifier, entityMap));
        // fallthrough to ignore
      case ORG:
        activeFreezeList.addAll(getActiveFreezeEntities(accountId, orgIdentifier, null, entityMap));
        // fallthrough to ignore
      case ACCOUNT:
        activeFreezeList.addAll(getActiveFreezeEntities(accountId, null, null, entityMap));
        break;
      default:
        break;
    }
    return activeFreezeList;
  }

  protected boolean matchesEntities(Map<FreezeEntityType, List<String>> entityMap, List<FreezeEntityRule> rules) {
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
                                      .filter(entityConfig -> !FilterType.ALL.equals(entityConfig.getFilterType()))
                                      .collect(Collectors.toList());
    for (EntityConfig entity : entities) {
      if (!matchesEntities(entityMap, entity)) {
        return false;
      }
    }
    return true;
  }

  protected boolean matchesEntities(Map<FreezeEntityType, List<String>> entityMap, EntityConfig entityConfig) {
    if (!entityMap.containsKey(entityConfig.getFreezeEntityType())) {
      return false;
    }
    boolean match = entityConfig.getEntityReference().stream().anyMatch(
        entityRef -> entityMap.get(entityConfig.getFreezeEntityType()).contains(entityRef));
    if (FilterType.NOT_EQUALS.equals(entityConfig.getFilterType())) {
      return !match;
    }
    return match;
  }
}
