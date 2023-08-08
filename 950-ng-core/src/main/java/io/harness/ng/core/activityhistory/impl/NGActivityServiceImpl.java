/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.activityhistory.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.activityhistory.NGActivityStatus.FAILED;
import static io.harness.ng.core.activityhistory.NGActivityStatus.SUCCESS;
import static io.harness.ng.core.activityhistory.NGActivityType.CONNECTIVITY_CHECK;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;

import io.harness.EntityType;
import io.harness.exception.UnexpectedException;
import io.harness.ng.core.activityhistory.NGActivityQueryCriteriaHelper;
import io.harness.ng.core.activityhistory.NGActivityStatus;
import io.harness.ng.core.activityhistory.NGActivityType;
import io.harness.ng.core.activityhistory.dto.ConnectivityCheckSummaryDTO;
import io.harness.ng.core.activityhistory.dto.ConnectivityCheckSummaryDTO.ConnectivityCheckSummaryKeys;
import io.harness.ng.core.activityhistory.dto.NGActivityDTO;
import io.harness.ng.core.activityhistory.dto.NGEntityListDTO;
import io.harness.ng.core.activityhistory.entity.NGActivity;
import io.harness.ng.core.activityhistory.entity.NGActivity.ActivityHistoryEntityKeys;
import io.harness.ng.core.activityhistory.mapper.NGActivityDTOToEntityMapper;
import io.harness.ng.core.activityhistory.mapper.NGActivityEntityToDTOMapper;
import io.harness.ng.core.activityhistory.service.NGActivityService;
import io.harness.repositories.activityhistory.NGActivityRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class NGActivityServiceImpl implements NGActivityService {
  private NGActivityRepository activityRepository;
  private NGActivityQueryCriteriaHelper ngActivityQueryCriteriaHelper;
  NGActivityEntityToDTOMapper activityEntityToDTOMapper;
  NGActivityDTOToEntityMapper activityDTOToEntityMapper;
  private static final String IS_SUCCESSFUL_CONNECTIVITY_CHECK_ACTIVITY = "isSuccessfulConnectivityCheckField";
  private static final String IS_FAILED_CONNECTIVITY_CHECK_ACTIVITY = "isFailedConnectivityCheckField";

  @Override
  public Page<NGActivityDTO> list(int page, int size, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String referredEntityIdentifier, long start, long end, NGActivityStatus status,
      EntityType referredEntityType, EntityType referredByEntityType, Set<NGActivityType> ngActivityTypes) {
    Criteria criteria = createCriteriaForEntityUsageActivity(accountIdentifier, orgIdentifier, projectIdentifier,
        referredEntityIdentifier, status, start, end, referredEntityType, referredByEntityType, ngActivityTypes);
    Pageable pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, ActivityHistoryEntityKeys.activityTime));
    List<NGActivity> activities = activityRepository.findAll(criteria, pageable).getContent();
    return new PageImpl<>(activities.stream().map(activityEntityToDTOMapper::writeDTO).collect(Collectors.toList()),
        pageable, activities.size());
  }

  @Override
  public ConnectivityCheckSummaryDTO getConnectivityCheckSummary(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String referredEntityIdentifier, long start, long end) {
    Criteria connectivityCheckCriteria = createConnectivityCheckCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, referredEntityIdentifier, start, end);
    ProjectionOperation projectionOperation = getProjectionOperationForProjectingSuccessfulAndFailedChecks();
    MatchOperation matchStage = Aggregation.match(connectivityCheckCriteria);
    GroupOperation groupByID =
        group().sum(IS_FAILED_CONNECTIVITY_CHECK_ACTIVITY).as(ConnectivityCheckSummaryKeys.failureCount);
    Aggregation aggregation = Aggregation.newAggregation(matchStage, projectionOperation, groupByID);
    ConnectivityCheckSummaryDTO connectivityCheckSummaryDTO =
        activityRepository.aggregate(aggregation, ConnectivityCheckSummaryDTO.class).getUniqueMappedResult();
    if (connectivityCheckSummaryDTO != null) {
      connectivityCheckSummaryDTO.setStartTime(start);
      connectivityCheckSummaryDTO.setEndTime(end);
    }
    return connectivityCheckSummaryDTO;
  }

  @Override
  public NGEntityListDTO listReferredByEntityTypes(EntityType entityType, Set<NGActivityType> ngActivityTypes) {
    Criteria criteria = Criteria.where(ActivityHistoryEntityKeys.referredEntityType).is(entityType.toString());
    if (isNotEmpty(ngActivityTypes)) {
      criteria.and(ActivityHistoryEntityKeys.type).in(ngActivityTypes);
    }
    return NGEntityListDTO.builder()
        .entityTypeList(activityRepository.findDistinctEntityTypes(criteria)
                            .stream()
                            .map(entity -> EntityType.fromString(entity))
                            .collect(Collectors.toList()))
        .build();
  }

  private ProjectionOperation getProjectionOperationForProjectingSuccessfulAndFailedChecks() {
    Criteria successFulActivityCriteria =
        Criteria.where(ActivityHistoryEntityKeys.activityStatus).is(SUCCESS.toString());
    Criteria failedActivityCriteria = Criteria.where(ActivityHistoryEntityKeys.activityStatus).is(FAILED.toString());

    return Aggregation.project()
        .and(ConditionalOperators.when(successFulActivityCriteria).then(1).otherwise(0))
        .as(IS_SUCCESSFUL_CONNECTIVITY_CHECK_ACTIVITY)
        .and(ConditionalOperators.Cond.when(failedActivityCriteria).then(1).otherwise(0))
        .as(IS_FAILED_CONNECTIVITY_CHECK_ACTIVITY)
        .andInclude(ActivityHistoryEntityKeys.activityTime);
  }

  private Criteria createConnectivityCheckCriteria(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String referredEntityIdentifier, long start, long end) {
    Criteria criteria = new Criteria();
    criteria.and(ActivityHistoryEntityKeys.type).is(String.valueOf(CONNECTIVITY_CHECK));
    ngActivityQueryCriteriaHelper.populateEntityFQNFilterInCriteria(
        criteria, accountIdentifier, orgIdentifier, projectIdentifier, referredEntityIdentifier);
    ngActivityQueryCriteriaHelper.addTimeFilterInTheCriteria(criteria, start, end);
    return criteria;
  }

  private void populateActivityStatusCriteria(Criteria criteria, NGActivityStatus status) {
    if (status != null) {
      criteria.and(ActivityHistoryEntityKeys.activityStatus).is(status);
    }
  }

  private Criteria createCriteriaForEntityUsageActivity(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String referredEntityIdentifier, NGActivityStatus status, long startTime, long endTime,
      EntityType referredEntityType, EntityType referredByEntityType, Set<NGActivityType> ngActivityTypes) {
    Criteria criteria = new Criteria();
    ngActivityQueryCriteriaHelper.populateEntityFQNFilterInCriteria(
        criteria, accountIdentifier, orgIdentifier, projectIdentifier, referredEntityIdentifier);
    ngActivityQueryCriteriaHelper.addReferredEntityTypeCriteria(criteria, referredEntityType);
    ngActivityQueryCriteriaHelper.addReferredByEntityTypeCriteria(criteria, referredByEntityType);
    populateActivityStatusCriteria(criteria, status);
    ngActivityQueryCriteriaHelper.addTimeFilterInTheCriteria(criteria, startTime, endTime);
    ngActivityQueryCriteriaHelper.addActivityTypeCriteria(criteria, ngActivityTypes);
    return criteria;
  }

  @Override
  public NGActivityDTO save(NGActivityDTO activityInput) {
    NGActivity activityEntity = activityDTOToEntityMapper.toActivityHistoryEntity(activityInput);
    NGActivity savedActivityEntity = null;
    try {
      savedActivityEntity = activityRepository.save(activityEntity);
    } catch (DuplicateKeyException ex) {
      log.info(String.format("Error while saving the activity history [%s] for [%s]", ex.getMessage(),
          activityEntity.getReferredEntityFQN()));
      throw new UnexpectedException(
          String.format("Error while creating the activity history for [%s]", activityEntity.getReferredEntityFQN()));
    }
    return activityEntityToDTOMapper.writeDTO(savedActivityEntity);
  }

  @Override
  public boolean deleteAllActivitiesOfAnEntity(
      String accountIdentifier, String entityFQN, EntityType referredEntityType) {
    long numberOfRecordsDeleted = activityRepository.deleteByReferredEntityFQNAndReferredEntityType(
        entityFQN, String.valueOf(referredEntityType));
    return numberOfRecordsDeleted > 0;
  }
}
