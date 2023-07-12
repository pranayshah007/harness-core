/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.activityhistory.impl;

import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.TEJAS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.EntityType;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.NGCoreTestBase;
import io.harness.ng.core.activityhistory.ActivityHistoryTestHelper;
import io.harness.ng.core.activityhistory.NGActivityStatus;
import io.harness.ng.core.activityhistory.NGActivityType;
import io.harness.ng.core.activityhistory.dto.ConnectivityCheckSummaryDTO;
import io.harness.ng.core.activityhistory.dto.NGActivityDTO;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class NGActivityServiceImplTest extends NGCoreTestBase {
  @Inject @InjectMocks NGActivityServiceImpl activityHistoryService;

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void listAllActivityOfAAccountLevelEntity() {
    String accountIdentifier = "accountIdentifier";
    String referredEntityIdentifier = "referredEntityIdentifier";
    String referredEntityIdentifierAccountLevel = "account.referredEntityIdentifier";
    long startTime = System.currentTimeMillis();
    for (int i = 0; i < 10; i++) {
      NGActivityDTO activityHistory = ActivityHistoryTestHelper.createActivityHistoryDTO(accountIdentifier, null, null,
          referredEntityIdentifierAccountLevel, NGActivityStatus.SUCCESS, startTime + i, NGActivityType.ENTITY_USAGE);
      activityHistoryService.save(activityHistory);
    }
    List<NGActivityDTO> activityHistories =
        activityHistoryService
            .list(0, 20, accountIdentifier, null, null, referredEntityIdentifier, startTime, startTime + 100L, null,
                EntityType.CONNECTORS, null, null)
            .toList();
    assertThat(activityHistories.size()).isEqualTo(10);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void listAllActivity() {
    String accountIdentifier = "accountIdentifier";
    String referredEntityIdentifier = "referredEntityIdentifier";
    String referredEntityIdentifierAccountLevel = "account.referredEntityIdentifier";
    long startTime = System.currentTimeMillis();
    for (int i = 0; i < 10; i++) {
      NGActivityDTO activityHistory = ActivityHistoryTestHelper.createActivityHistoryDTO(accountIdentifier, null, null,
          referredEntityIdentifierAccountLevel, NGActivityStatus.SUCCESS, startTime + i, NGActivityType.ENTITY_USAGE);
      activityHistoryService.save(activityHistory);
    }

    for (int i = 0; i < 6; i++) {
      NGActivityDTO activityHistory = ActivityHistoryTestHelper.createActivityHistoryDTO(accountIdentifier, null, null,
          referredEntityIdentifierAccountLevel, NGActivityStatus.FAILED, startTime + i,
          NGActivityType.CONNECTIVITY_CHECK);
      activityHistoryService.save(activityHistory);
    }

    for (int i = 0; i < 4; i++) {
      NGActivityDTO activityHistory = ActivityHistoryTestHelper.createActivityHistoryDTO(accountIdentifier, null, null,
          referredEntityIdentifierAccountLevel, NGActivityStatus.SUCCESS, startTime + i,
          NGActivityType.CONNECTIVITY_CHECK);
      activityHistoryService.save(activityHistory);
    }

    List<NGActivityDTO> activityHistories =
        activityHistoryService
            .list(0, 20, accountIdentifier, null, null, referredEntityIdentifier, startTime, startTime + 100L, null,
                EntityType.CONNECTORS, null, null)
            .toList();
    assertThat(activityHistories.size()).isEqualTo(20);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void getConnectivityCheckSummary() {
    String accountIdentifier = "accountIdentifier";
    String referredEntityIdentifier = "referredEntityIdentifier";
    String referredEntityIdentifierAccountLevel = "account.referredEntityIdentifier";
    long startTime = System.currentTimeMillis();

    for (int i = 0; i < 6; i++) {
      NGActivityDTO activityHistory = ActivityHistoryTestHelper.createActivityHistoryDTO(accountIdentifier, null, null,
          referredEntityIdentifierAccountLevel, NGActivityStatus.FAILED, startTime + i,
          NGActivityType.CONNECTIVITY_CHECK);
      activityHistoryService.save(activityHistory);
    }

    ConnectivityCheckSummaryDTO connectivityCheckSummaryDTO = activityHistoryService.getConnectivityCheckSummary(
        accountIdentifier, null, null, referredEntityIdentifier, startTime, startTime + 100L);
    assertThat(connectivityCheckSummaryDTO).isNotNull();
    assertThat(connectivityCheckSummaryDTO.getFailureCount()).isEqualTo(6);
    assertThat(connectivityCheckSummaryDTO.getStartTime()).isEqualTo(startTime);
    assertThat(connectivityCheckSummaryDTO.getEndTime()).isEqualTo(startTime + 100L);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void listAllActivityOfAOrgLevelEntity() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String referredEntityIdentifier = "referredEntityIdentifier";
    String referredEntityIdentifierOrgLevel = "org.referredEntityIdentifier";
    long startTime = System.currentTimeMillis();
    for (int i = 0; i < 10; i++) {
      NGActivityDTO activityHistory =
          ActivityHistoryTestHelper.createActivityHistoryDTO(accountIdentifier, orgIdentifier, null,
              referredEntityIdentifierOrgLevel, NGActivityStatus.SUCCESS, startTime + i, NGActivityType.ENTITY_USAGE);
      activityHistoryService.save(activityHistory);
    }
    List<NGActivityDTO> activityHistories =
        activityHistoryService
            .list(0, 20, accountIdentifier, orgIdentifier, null, referredEntityIdentifier, startTime, startTime + 100L,
                null, EntityType.CONNECTORS, null, null)
            .toList();
    assertThat(activityHistories.size()).isEqualTo(10);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void listAllActivityOfAProjectLevelEntity() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String referredEntityIdentifier = "referredEntityIdentifier";
    long startTime = System.currentTimeMillis();
    for (int i = 0; i < 10; i++) {
      NGActivityDTO activityHistory =
          ActivityHistoryTestHelper.createActivityHistoryDTO(accountIdentifier, orgIdentifier, projectIdentifier,
              referredEntityIdentifier, NGActivityStatus.SUCCESS, startTime + i, NGActivityType.ENTITY_USAGE);
      activityHistoryService.save(activityHistory);
    }
    List<NGActivityDTO> activityHistories =
        activityHistoryService
            .list(0, 20, accountIdentifier, orgIdentifier, projectIdentifier, referredEntityIdentifier, startTime,
                startTime + 100L, null, EntityType.CONNECTORS, null, null)
            .toList();
    assertThat(activityHistories.size()).isEqualTo(10);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void save() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String referredEntityIdentifier = "referredEntityIdentifier";
    NGActivityDTO savedActivityHistory = activityHistoryService.save(ActivityHistoryTestHelper.createActivityHistoryDTO(
        accountIdentifier, orgIdentifier, projectIdentifier, referredEntityIdentifier, NGActivityStatus.SUCCESS,
        System.currentTimeMillis(), NGActivityType.ENTITY_USAGE));
    assertThat(savedActivityHistory).isNotNull();
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void listAllActivityOfAAccountLevelEntity_WithActivityTypeFilter() {
    String accountIdentifier = "accountIdentifier";
    String referredEntityIdentifier = "referredEntityIdentifier";
    String referredEntityIdentifierAccountLevel = "account.referredEntityIdentifier";
    long startTime = System.currentTimeMillis();
    for (int i = 0; i < 10; i++) {
      NGActivityDTO activityHistory = ActivityHistoryTestHelper.createActivityHistoryDTO(accountIdentifier, null, null,
          referredEntityIdentifierAccountLevel, NGActivityStatus.SUCCESS, startTime + i, NGActivityType.ENTITY_USAGE);
      activityHistoryService.save(activityHistory);
    }
    for (int i = 0; i < 5; i++) {
      NGActivityDTO activityHistory = ActivityHistoryTestHelper.createActivityHistoryDTO(accountIdentifier, null, null,
          referredEntityIdentifierAccountLevel, NGActivityStatus.SUCCESS, startTime + i, NGActivityType.ENTITY_UPDATE);
      activityHistoryService.save(activityHistory);
    }
    List<NGActivityDTO> activityHistories =
        activityHistoryService
            .list(0, 20, accountIdentifier, null, null, referredEntityIdentifier, startTime, startTime + 100L, null,
                EntityType.CONNECTORS, null, Set.of(NGActivityType.ENTITY_USAGE))
            .toList();
    assertThat(activityHistories.size()).isEqualTo(10);

    activityHistories =
        activityHistoryService
            .list(0, 20, accountIdentifier, null, null, referredEntityIdentifier, startTime, startTime + 100L, null,
                EntityType.CONNECTORS, null, Set.of(NGActivityType.ENTITY_UPDATE))
            .toList();
    assertThat(activityHistories.size()).isEqualTo(5);

    activityHistories =
        activityHistoryService
            .list(0, 20, accountIdentifier, null, null, referredEntityIdentifier, startTime, startTime + 100L, null,
                EntityType.CONNECTORS, null, Set.of(NGActivityType.ENTITY_USAGE, NGActivityType.ENTITY_UPDATE))
            .toList();
    assertThat(activityHistories.size()).isEqualTo(15);

    activityHistories = activityHistoryService
                            .list(0, 20, accountIdentifier, null, null, referredEntityIdentifier, startTime,
                                startTime + 100L, null, EntityType.CONNECTORS, null, null)
                            .toList();
    assertThat(activityHistories.size()).isEqualTo(15);
  }
}
