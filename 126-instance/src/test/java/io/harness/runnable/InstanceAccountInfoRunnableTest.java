/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.runnable;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.entities.DeploymentAccounts;
import io.harness.entities.DeploymentAccounts.DeploymentAccountsKeys;
import io.harness.entities.DeploymentSummary;
import io.harness.entities.DeploymentSummary.DeploymentSummaryKeys;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

public class InstanceAccountInfoRunnableTest extends InstancesTestBase {
  @Mock MongoTemplate mongoTemplate;
  @InjectMocks InstanceAccountInfoRunnable instanceAccountInfoRunnable;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTest() {
    Query query = new Query();
    List<String> accountIdsToBeInserted = new LinkedList<>(Arrays.asList("account1", "account2", "account3"));
    when(mongoTemplate.findDistinct(
             query, DeploymentSummaryKeys.accountIdentifier, DeploymentSummary.class, String.class))
        .thenReturn(accountIdsToBeInserted);
    List<String> accountIdsAlreadyExist = new LinkedList<>(Arrays.asList("account1"));
    when(mongoTemplate.findDistinct(
             query, DeploymentAccountsKeys.accountIdentifier, DeploymentAccounts.class, String.class))
        .thenReturn(accountIdsAlreadyExist);
    List<String> finalList = Arrays.asList("account2", "account3");
    final List<DeploymentAccounts> deploymentAccountsDocuments =
        finalList.stream()
            .map(item
                -> DeploymentAccounts.builder()
                       .accountIdentifier(item)
                       .instanceStatsMetricsPublisherIteration(0L)
                       .build())
            .collect(Collectors.toList());
    instanceAccountInfoRunnable.execute();
    verify(mongoTemplate, times(1)).insertAll(deploymentAccountsDocuments);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void runTest() {
    Query query = new Query();
    List<String> accountIdsToBeInserted = new LinkedList<>(Arrays.asList("account1", "account2", "account3"));
    when(mongoTemplate.findDistinct(
             query, DeploymentSummaryKeys.accountIdentifier, DeploymentSummary.class, String.class))
        .thenReturn(accountIdsToBeInserted);
    List<String> accountIdsAlreadyExist = new LinkedList<>(Arrays.asList("account1"));
    when(mongoTemplate.findDistinct(
             query, DeploymentAccountsKeys.accountIdentifier, DeploymentAccounts.class, String.class))
        .thenReturn(accountIdsAlreadyExist);
    List<String> finalList = Arrays.asList("account2", "account3");
    final List<DeploymentAccounts> deploymentAccountsDocuments =
        finalList.stream()
            .map(item
                -> DeploymentAccounts.builder()
                       .accountIdentifier(item)
                       .instanceStatsMetricsPublisherIteration(0L)
                       .build())
            .collect(Collectors.toList());
    instanceAccountInfoRunnable.run();
    verify(mongoTemplate, times(1)).insertAll(deploymentAccountsDocuments);
  }
}
