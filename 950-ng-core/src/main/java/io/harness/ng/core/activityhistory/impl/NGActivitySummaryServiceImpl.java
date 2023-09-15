/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.activityhistory.impl;

import static io.harness.ng.core.activityhistory.NGActivityStatus.FAILED;
import static io.harness.ng.core.activityhistory.NGActivityStatus.SUCCESS;
import static io.harness.ng.core.activityhistory.NGActivityType.CONNECTIVITY_CHECK;
import static io.harness.ng.core.activityhistory.dto.TimeGroupType.DAY;
import static io.harness.ng.core.activityhistory.dto.TimeGroupType.HOUR;
import static io.harness.ng.core.activityhistory.dto.TimeGroupType.WEEK;

import static java.util.Objects.isNull;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.bucket;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.BooleanOperators.And.and;

import io.harness.EntityType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.ng.core.activityhistory.NGActivityQueryCriteriaHelper;
import io.harness.ng.core.activityhistory.dto.NGActivitySummaryDTO;
import io.harness.ng.core.activityhistory.dto.NGActivitySummaryDTO.NGActivitySummaryKeys;
import io.harness.ng.core.activityhistory.dto.TimeGroupType;
import io.harness.ng.core.activityhistory.entity.NGActivity.ActivityHistoryEntityKeys;
import io.harness.ng.core.activityhistory.service.NGActivitySummaryService;
import io.harness.repositories.activityhistory.NGActivityRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ArithmeticOperators.Add;
import org.springframework.data.mongodb.core.aggregation.BooleanOperators.And;
import org.springframework.data.mongodb.core.aggregation.BucketOperation;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class NGActivitySummaryServiceImpl implements NGActivitySummaryService {
  private NGActivityRepository activityRepository;
  private NGActivityQueryCriteriaHelper ngActivityQueryCriteriaHelper;
  private static final String intervalField = "intervalField";
  private static final String isSuccessfulActivityField = "isSuccessfulActivity";
  private static final String isFailedActivityField = "isFailedActivity";
  private static final String isConnectivityFailureField = "isConnectivityFailure";
  private static final long HOUR_IN_MS = 60 * 60 * 1000;
  private static final long DAY_IN_MS = 24 * HOUR_IN_MS;
  private static final long WEEK_IN_MS = 7 * DAY_IN_MS;

  @Override
  public Page<NGActivitySummaryDTO> listActivitySummary(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String referredEntityIdentifier, TimeGroupType timeGroupType, long start, long end,
      EntityType referredEntityType, EntityType referredByEntityType) {
    validateTheTimeRangeGivenIsCorrect(timeGroupType, start, end);
    Criteria activitySummaryCriteria = createActivitySummaryCriteria(accountIdentifier, orgIdentifier,
        projectIdentifier, referredEntityIdentifier, start, end, referredEntityType, referredByEntityType);
    MatchOperation matchStage = Aggregation.match(activitySummaryCriteria);
    ProjectionOperation projectionWhichAddsMinuteInterval = getProjectionWhichAddMinuteInterval(timeGroupType);
    BucketOperation bucketOperation = getBucketOperation(timeGroupType, start, end);
    Aggregation aggregation =
        Aggregation.newAggregation(matchStage, projectionWhichAddsMinuteInterval, bucketOperation);
    List<NGActivitySummaryDTO> activitySummaries =
        activityRepository.aggregate(aggregation, NGActivitySummaryDTO.class).getMappedResults();
    populateTheStartAndEndTimeInActivities(activitySummaries, timeGroupType);
    return new PageImpl<>(activitySummaries);
  }

  private void populateTheStartAndEndTimeInActivities(
      List<NGActivitySummaryDTO> activitySummaries, TimeGroupType timeGroupType) {
    activitySummaries.forEach(activity -> {
      setActivityStartTime(activity);
      setActivityEndTime(activity, timeGroupType);
    });
  }

  private void setActivityStartTime(NGActivitySummaryDTO activitySummary) {
    activitySummary.setStartTime(activitySummary.get_id());
  }

  private void setActivityEndTime(NGActivitySummaryDTO activitySummary, TimeGroupType timeGroupType) {
    activitySummary.setEndTime(activitySummary.get_id() + getTimeUnitToGroupBy(timeGroupType));
  }

  private void validateTheTimeRangeGivenIsCorrect(TimeGroupType timeGroupType, long startTime, long endTime) {
    if (timeGroupType == DAY) {
      float numberOfDays = (endTime - startTime) / (float) DAY_IN_MS;
      boolean isNumberOfDaysInt = checkTheNumberIsAInt(numberOfDays);
      if (!isNumberOfDaysInt) {
        throw new InvalidRequestException(
            "The start time and end time should constitute n days, the number of days in given range is not a int");
      }
    } else if (timeGroupType == HOUR) {
      float numberOfHours = (endTime - startTime) / (float) HOUR_IN_MS;
      boolean isNumberOfHourInt = checkTheNumberIsAInt(numberOfHours);
      if (!isNumberOfHourInt || Math.round(numberOfHours) != 24) {
        throw new InvalidRequestException(
            "The start time and end time should constitute a day, the number of hours in given range is not 24");
      }
    } else {
      throw new UnknownEnumTypeException("TimeGroupType", String.valueOf(timeGroupType));
    }
  }

  private boolean checkTheNumberIsAInt(float numberOHours) {
    if (numberOHours == Math.round(numberOHours)) {
      return true;
    }
    return false;
  }

  private BucketOperation getBucketOperation(TimeGroupType timeGroupType, long startTime, long endTime) {
    List<Long> timeIntervalList = getBoundariesForAggregating(timeGroupType, startTime, endTime);
    return bucket(ActivityHistoryEntityKeys.activityTime)
        .withBoundaries(timeIntervalList.toArray())
        .andOutput(isSuccessfulActivityField)
        .sum()
        .as(NGActivitySummaryKeys.successfulActivitiesCount)
        .andOutput(isFailedActivityField)
        .sum()
        .as(NGActivitySummaryKeys.failedActivitiesCount)
        .andOutput(isConnectivityFailureField)
        .sum()
        .as(NGActivitySummaryKeys.heartBeatFailuresCount);
  }

  private List<Long> getBoundariesForAggregating(TimeGroupType timeGroupType, long startTime, long endTime) {
    long timeUnit = getTimeUnitToGroupBy(timeGroupType);
    List<Long> timeIntervalList = new ArrayList<>();
    for (long time = startTime; time <= endTime; time += timeUnit) {
      timeIntervalList.add(time);
    }
    return timeIntervalList;
  }

  private long getTimeUnitToGroupBy(TimeGroupType timeGroupType) {
    if (timeGroupType == DAY) {
      return DAY_IN_MS;
    } else if (timeGroupType == HOUR) {
      return HOUR_IN_MS;
    } else if (timeGroupType == WEEK) {
      return WEEK_IN_MS;
    } else {
      throw new UnknownEnumTypeException("Time Group Type", String.valueOf(timeGroupType));
    }
  }

  private ProjectionOperation getProjectionWhichAddMinuteInterval(TimeGroupType timeGroupType) {
    And successFulActivityCriteria = getCriteriaForSuccessFulNonHearbeatActivity();
    And failedActivityCriteria = getCriteriaForFailedNonHearbeatActivity();
    And failedConnectivityCheck = getCriteriaForFailedConnectivityCheck();

    return Aggregation.project()
        .and(ConditionalOperators.when(successFulActivityCriteria).then(1).otherwise(0))
        .as(isSuccessfulActivityField)
        .and(ConditionalOperators.Cond.when(failedActivityCriteria).then(1).otherwise(0))
        .as(isFailedActivityField)
        .and(ConditionalOperators.when(failedConnectivityCheck).then(1).otherwise(0))
        .as(isConnectivityFailureField)
        .andInclude(ActivityHistoryEntityKeys.activityTime);
  }

  private ProjectionOperation getProjectionForStartAndEndTime(long timeUnit) {
    return Aggregation.project()
        .and(Fields.UNDERSCORE_ID)
        .as(NGActivitySummaryKeys.startTime)
        .and(Add.valueOf(Fields.UNDERSCORE_ID).add(timeUnit))
        .as(NGActivitySummaryKeys.endTime)
        .andInclude(NGActivitySummaryKeys.successfulActivitiesCount, NGActivitySummaryKeys.failedActivitiesCount,
            NGActivitySummaryKeys.heartBeatFailuresCount);
  }

  private And getCriteriaForSuccessFulNonHearbeatActivity() {
    return and(
        ComparisonOperators.valueOf(ActivityHistoryEntityKeys.type).notEqualToValue(CONNECTIVITY_CHECK.toString()),
        ComparisonOperators.valueOf(ActivityHistoryEntityKeys.activityStatus).equalToValue(SUCCESS.toString()));
  }

  private And getCriteriaForFailedNonHearbeatActivity() {
    return and(
        ComparisonOperators.valueOf(ActivityHistoryEntityKeys.type).notEqualToValue(CONNECTIVITY_CHECK.toString()),
        ComparisonOperators.valueOf(ActivityHistoryEntityKeys.activityStatus).equalToValue(FAILED.toString()));
  }

  private And getCriteriaForFailedConnectivityCheck() {
    return and(ComparisonOperators.valueOf(ActivityHistoryEntityKeys.type).equalToValue(CONNECTIVITY_CHECK.toString()),
        ComparisonOperators.valueOf(ActivityHistoryEntityKeys.activityStatus).equalToValue(FAILED.toString()));
  }

  private GroupOperation getGroupQueryToGroupResultsByIntervals(TimeGroupType timeGroupType) {
    return group(intervalField)
        .sum(isSuccessfulActivityField)
        .as(NGActivitySummaryKeys.successfulActivitiesCount)
        .sum(isFailedActivityField)
        .as(NGActivitySummaryKeys.failedActivitiesCount)
        .sum(isConnectivityFailureField)
        .as(NGActivitySummaryKeys.heartBeatFailuresCount);
  }

  private Criteria createActivitySummaryCriteria(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String referredEntityIdentifier, long startTime, long endTime,
      EntityType referredEntityType, EntityType referredByEntityType) {
    Criteria criteria = new Criteria();
    ngActivityQueryCriteriaHelper.populateEntityFQNFilterInCriteria(
        criteria, accountIdentifier, orgIdentifier, projectIdentifier, referredEntityIdentifier);
    ngActivityQueryCriteriaHelper.addTimeFilterInTheCriteria(criteria, startTime, endTime);
    ngActivityQueryCriteriaHelper.addReferredEntityTypeCriteria(criteria, referredEntityType);
    if (!isNull(referredByEntityType)) {
      ngActivityQueryCriteriaHelper.addReferredByEntityTypeCriteria(criteria, Set.of(referredByEntityType));
    }
    return criteria;
  }
}
