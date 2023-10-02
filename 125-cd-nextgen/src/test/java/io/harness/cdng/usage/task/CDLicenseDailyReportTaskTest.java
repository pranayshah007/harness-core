/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage.task;

import static io.harness.rule.OwnerRule.IVAN;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.usage.task.CDLicenseReportAccounts.CDLicenseReportAccountsKeys;
import io.harness.entities.DeploymentSummary;
import io.harness.entities.DeploymentSummary.DeploymentSummaryKeys;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

public class CDLicenseDailyReportTaskTest extends CategoryTest {
  @Mock MongoTemplate mongoTemplate;
  @InjectMocks CDLicenseDailyReportTask cdLicenseDailyReportTask;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void runTest() {
    Query query = new Query();
    List<String> accountIdsToBeInserted = new LinkedList<>(Arrays.asList("account1", "account2", "account3"));
    when(mongoTemplate.findDistinct(
             query, DeploymentSummaryKeys.accountIdentifier, DeploymentSummary.class, String.class))
        .thenReturn(accountIdsToBeInserted);
    List<String> accountIdsAlreadyExist = new LinkedList<>(Arrays.asList("account1"));
    when(mongoTemplate.findDistinct(
             query, CDLicenseReportAccountsKeys.accountIdentifier, CDLicenseReportAccounts.class, String.class))
        .thenReturn(accountIdsAlreadyExist);
    List<String> finalList = Arrays.asList("account2", "account3");
    final List<CDLicenseReportAccounts> cdLicenseReportAccounts =
        finalList.stream()
            .map(item
                -> CDLicenseReportAccounts.builder().accountIdentifier(item).cdLicenseDailyReportIteration(0L).build())
            .collect(Collectors.toList());
    cdLicenseDailyReportTask.run();

    verify(mongoTemplate, times(1)).insertAll(cdLicenseReportAccounts);
  }
}
