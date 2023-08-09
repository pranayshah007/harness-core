/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.activityhistory.impl;

import static io.harness.ng.core.activityhistory.ActivityHistoryTestHelper.createActivityHistoryDTO;
import static io.harness.rule.OwnerRule.DEEPAK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.EntityType;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.NGCoreTestBase;
import io.harness.ng.core.activityhistory.NGActivityStatus;
import io.harness.ng.core.activityhistory.NGActivityType;
import io.harness.ng.core.activityhistory.dto.NGActivityDTO;
import io.harness.ng.core.activityhistory.dto.NGActivitySummaryDTO;
import io.harness.ng.core.activityhistory.dto.TimeGroupType;
import io.harness.ng.core.activityhistory.service.NGActivityService;
import io.harness.rule.Owner;
import io.harness.utils.FullyQualifiedIdentifierHelper;

import com.google.inject.Inject;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@Slf4j
public class NGActivitySummaryServiceImplTest extends NGCoreTestBase {
  @Inject @InjectMocks NGActivitySummaryServiceImpl entityActivitySummaryService;
  @Inject NGActivityService ngActivityService;

  private static final long DAY_IN_MS = 1000 * 60 * 60 * 24;
  private static final long HOUR_IN_MS = 60 * 60 * 1000;
  private static final String accountIdentifier = "accountIdentifier";
  private static final String orgIdentifier = "orgIdentifier";
  private static final String projectIdentifier = "projectIdentifier";

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void listActivitySummaryForDaysInISTTimeZone() {
    String startDateString = "Oct 27 2020 00:00:00";
    String endDateString = "Nov 01 2020 00:00:00";
    long startingTimeEpoch = getEpochTime(startDateString, "IST");
    long endTimeEpoch = getEpochTime(endDateString, "IST");
    listActivitySummaryForDays(startingTimeEpoch, endTimeEpoch, "identifier1" + System.currentTimeMillis());
  }

  public void cleanTheEntityActivityRecords(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    ngActivityService.deleteAllActivitiesOfAnEntity(accountIdentifier,
        FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier),
        EntityType.CONNECTORS);
  }

  public long getRandomNumberUsingNextInt(long minimum, long maximum) {
    Random random = new Random();
    return minimum + (long) (random.nextDouble() * (maximum - minimum));
  }

  public long getEpochTime(String dateString, String timezone) {
    SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd yyyy HH:mm:ss");
    dateFormat.setTimeZone(TimeZone.getTimeZone(timezone));
    Date date = Date.from(Instant.now());
    try {
      date = dateFormat.parse(dateString);
    } catch (Exception ex) {
      log.info("Error parsing the date");
    }
    return date.getTime();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void listActivitySummaryForDaysInUTCTimeZone() {
    String startDateString = "Oct 27 2020 00:00:00";
    String endDateString = "Nov 01 2020 00:00:00";
    long startingTimeEpoch = getEpochTime(startDateString, "UTC");
    long endTimeEpoch = getEpochTime(endDateString, "UTC");
    listActivitySummaryForDays(startingTimeEpoch, endTimeEpoch, "identifier2" + System.currentTimeMillis());
  }

  private void listActivitySummaryForDays(long startingTimeEpoch, long endTimeEpoch, String referredEntityIdentifier) {
    createActivityHistoryData(startingTimeEpoch, endTimeEpoch, referredEntityIdentifier, DAY_IN_MS);

    List<NGActivitySummaryDTO> activities =
        entityActivitySummaryService
            .listActivitySummary(accountIdentifier, orgIdentifier, projectIdentifier, referredEntityIdentifier,
                TimeGroupType.DAY, startingTimeEpoch, endTimeEpoch, EntityType.CONNECTORS, null)
            .toList();

    cleanTheEntityActivityRecords(accountIdentifier, orgIdentifier, projectIdentifier, referredEntityIdentifier);
    assertThat(activities).isNotNull();
    assertThat(activities.size()).isEqualTo(5);
    for (int i = 0; i < 5; i++) {
      NGActivitySummaryDTO ngActivitySummary = activities.get(i);
      assertThat(ngActivitySummary.getStartTime()).isEqualTo(startingTimeEpoch + i * DAY_IN_MS);
      assertThat(ngActivitySummary.getEndTime()).isEqualTo(startingTimeEpoch + (i + 1) * DAY_IN_MS);
      assertThat(ngActivitySummary.getHeartBeatFailuresCount()).isEqualTo(8 + i);
      assertThat(ngActivitySummary.getSuccessfulActivitiesCount()).isEqualTo(10 + i);
      assertThat(ngActivitySummary.getFailedActivitiesCount()).isEqualTo(4 + i);
    }
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void listActivitySummaryForHourInISTTimeZone() {
    String startDateString = "Oct 27 2020 00:00:00";
    String endDateString = "Oct 28 2020 00:00:00";
    long startingTimeEpoch = getEpochTime(startDateString, "IST");
    long endTimeEpoch = getEpochTime(endDateString, "IST");
    log.info("start Time {}, end time {}", startingTimeEpoch, endTimeEpoch);
    listActivitySummaryForHours(startingTimeEpoch, endTimeEpoch, "identifier3" + System.currentTimeMillis());
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void listActivitySummaryForHourInUTCTimeZone() {
    String startDateString = "Oct 27 2020 00:00:00";
    String endDateString = "Oct 28 2020 00:00:00";
    long startingTimeEpoch = getEpochTime(startDateString, "UTC");
    long endTimeEpoch = getEpochTime(endDateString, "UTC");
    listActivitySummaryForHours(startingTimeEpoch, endTimeEpoch, "identifier4" + System.currentTimeMillis());
  }

  private void listActivitySummaryForHours(long startingTimeEpoch, long endTimeEpoch, String referredEntityIdentifier) {
    createActivityHistoryData(startingTimeEpoch, endTimeEpoch, referredEntityIdentifier, HOUR_IN_MS);

    List<NGActivitySummaryDTO> activities =
        entityActivitySummaryService
            .listActivitySummary(accountIdentifier, orgIdentifier, projectIdentifier, referredEntityIdentifier,
                TimeGroupType.HOUR, startingTimeEpoch, endTimeEpoch, EntityType.CONNECTORS, null)
            .toList();

    cleanTheEntityActivityRecords(accountIdentifier, orgIdentifier, projectIdentifier, referredEntityIdentifier);
    assertThat(activities).isNotNull();
    assertThat(activities.size()).isEqualTo(24);
    for (int i = 0; i < 24; i++) {
      NGActivitySummaryDTO ngActivitySummary = activities.get(i);
      assertThat(ngActivitySummary.getStartTime()).isEqualTo(startingTimeEpoch + i * HOUR_IN_MS);
      assertThat(ngActivitySummary.getEndTime()).isEqualTo(startingTimeEpoch + (i + 1) * HOUR_IN_MS);
      assertThat(ngActivitySummary.getHeartBeatFailuresCount()).isEqualTo(8 + i);
      assertThat(ngActivitySummary.getSuccessfulActivitiesCount()).isEqualTo(10 + i);
      assertThat(ngActivitySummary.getFailedActivitiesCount()).isEqualTo(4 + i);
    }
  }

  private void createActivityHistoryData(long startTime, long endTime, String referredEntityIdentifier, long timeUnit) {
    for (long time = startTime, timeUnitsCovered = 0; time < endTime; time += timeUnit, timeUnitsCovered += 1) {
      long numberOfHeartBeatFailures = 8 + timeUnitsCovered;
      long numberOfSuccessfulEntityUsage = 10 + timeUnitsCovered;
      long numberOfFailedEntityUsage = 4 + timeUnitsCovered;

      for (long i = 0; i < numberOfSuccessfulEntityUsage; i++) {
        long activityTime = getRandomNumberUsingNextInt(time, time + timeUnit);
        NGActivityDTO activity =
            createActivityHistoryDTO(accountIdentifier, orgIdentifier, projectIdentifier, referredEntityIdentifier,
                NGActivityStatus.SUCCESS, activityTime, NGActivityType.ENTITY_USAGE, EntityType.PIPELINES);
        ngActivityService.save(activity);
      }

      for (long i = 0; i < numberOfFailedEntityUsage; i++) {
        long activityTime = getRandomNumberUsingNextInt(time, time + timeUnit);
        NGActivityDTO activity =
            createActivityHistoryDTO(accountIdentifier, orgIdentifier, projectIdentifier, referredEntityIdentifier,
                NGActivityStatus.FAILED, activityTime, NGActivityType.ENTITY_USAGE, EntityType.PIPELINES);
        ngActivityService.save(activity);
      }

      for (long i = 0; i < numberOfHeartBeatFailures; i++) {
        long activityTime = getRandomNumberUsingNextInt(time, time + timeUnit);
        NGActivityDTO activity =
            createActivityHistoryDTO(accountIdentifier, orgIdentifier, projectIdentifier, referredEntityIdentifier,
                NGActivityStatus.FAILED, activityTime, NGActivityType.CONNECTIVITY_CHECK, EntityType.PIPELINES);
        ngActivityService.save(activity);
      }
    }
  }
}
