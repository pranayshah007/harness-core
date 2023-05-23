/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.resource;

import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.EntityNotFoundException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerEventHistoryBaseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerEventHistoryDTO;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory.TriggerEventHistoryKeys;
import io.harness.ngtriggers.mapper.NGTriggerEventHistoryBaseMapper;
import io.harness.ngtriggers.mapper.NGTriggerEventHistoryMapper;
import io.harness.ngtriggers.service.NGTriggerEventsService;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Singleton
@Slf4j
public class NGTriggerEventHistoryResourceImpl implements NGTriggerEventHistoryResource {
  private final NGTriggerService ngTriggerService;
  private final NGTriggerEventsService ngTriggerEventsService;

  @Override
  public ResponseDTO<Page<NGTriggerEventHistoryDTO>> getTriggerEventHistory(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String targetIdentifier, String triggerIdentifier,
      String searchTerm, int page, int size, List<String> sort) {
    Optional<NGTriggerEntity> ngTriggerEntity = ngTriggerService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, targetIdentifier, triggerIdentifier, false);
    if (!ngTriggerEntity.isPresent()) {
      throw new EntityNotFoundException(String.format("Trigger %s does not exist", triggerIdentifier));
    }

    Criteria criteria = ngTriggerEventsService.formTriggerEventCriteria(accountIdentifier, orgIdentifier,
        projectIdentifier, targetIdentifier, triggerIdentifier, searchTerm, new ArrayList<>());
    Pageable pageRequest;
    if (EmptyPredicate.isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, TriggerEventHistoryKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }

    Page<TriggerEventHistory> eventHistoryList = ngTriggerEventsService.getEventHistory(criteria, pageRequest);

    Page<NGTriggerEventHistoryDTO> ngTriggerEventHistoryDTOS = eventHistoryList.map(
        eventHistory -> NGTriggerEventHistoryMapper.toTriggerEventHistoryDto(eventHistory, ngTriggerEntity.get()));

    return ResponseDTO.newResponse(ngTriggerEventHistoryDTOS);
  }

  @Override
  public ResponseDTO<Page<NGTriggerEventHistoryBaseDTO>> getTriggerHistoryEventCorrelation(
      String accountIdentifier, String eventCorrelationId, int page, int size, List<String> sort) {
    Criteria criteria =
        ngTriggerEventsService.formEventCriteria(accountIdentifier, eventCorrelationId, new ArrayList<>());
    Pageable pageRequest;
    if (EmptyPredicate.isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, TriggerEventHistoryKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }

    Page<TriggerEventHistory> eventHistoryList = ngTriggerEventsService.getEventHistory(criteria, pageRequest);

    Page<NGTriggerEventHistoryBaseDTO> ngTriggerEventHistoryDTOS =
        eventHistoryList.map(eventHistory -> NGTriggerEventHistoryBaseMapper.toEventHistory(eventHistory));

    return ResponseDTO.newResponse(ngTriggerEventHistoryDTOS);
  }
}
