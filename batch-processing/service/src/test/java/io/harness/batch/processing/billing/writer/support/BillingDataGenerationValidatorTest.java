/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.writer.support;

import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.batch.LastReceivedPublishedMessage;
import io.harness.ccm.health.LastReceivedPublishedMessageDao;
import io.harness.rule.Owner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class BillingDataGenerationValidatorTest extends CategoryTest {
  private static final String ACCOUNT_ID = "fcf53242-4a9d-4b8c-8497-5ba7360569d9";
  private static final String CLUSTER_ID = "fe847242-1de4-7ab5-8497-5ba7360569d9";
  private static final Instant START_TIME = Instant.now();
  private BillingDataGenerationValidator billingDataGenerationValidator;
  private LastReceivedPublishedMessageDao lastReceivedPublishedMessageDao;
  private ClusterDataGenerationValidator clusterDataGenerationValidator;

  @Before
  public void setUp() {
    lastReceivedPublishedMessageDao = mock(LastReceivedPublishedMessageDao.class);
    clusterDataGenerationValidator = mock(ClusterDataGenerationValidator.class);
    billingDataGenerationValidator =
        new BillingDataGenerationValidator(lastReceivedPublishedMessageDao, clusterDataGenerationValidator);
    when(clusterDataGenerationValidator.shouldGenerateClusterData(any(), any())).thenReturn(true);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnFalseWhenClusterEventIsNotAvailable() {
    when(lastReceivedPublishedMessageDao.get(ACCOUNT_ID, CLUSTER_ID)).thenReturn(null);
    boolean generateBillingData =
        billingDataGenerationValidator.shouldGenerateBillingData(ACCOUNT_ID, CLUSTER_ID, START_TIME);
    assertThat(generateBillingData).isFalse();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnFalseWhenClusterEventIsOld() {
    when(lastReceivedPublishedMessageDao.get(ACCOUNT_ID, CLUSTER_ID))
        .thenReturn(lastReceivedPublishedMessage(START_TIME.minus(3, ChronoUnit.DAYS).toEpochMilli()));
    boolean generateBillingData =
        billingDataGenerationValidator.shouldGenerateBillingData(ACCOUNT_ID, CLUSTER_ID, START_TIME);
    assertThat(generateBillingData).isFalse();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnTrueWhenClusterEventIsRecent() {
    when(lastReceivedPublishedMessageDao.get(ACCOUNT_ID, CLUSTER_ID))
        .thenReturn(lastReceivedPublishedMessage(START_TIME.plus(1, ChronoUnit.DAYS).toEpochMilli()));
    boolean generateBillingData =
        billingDataGenerationValidator.shouldGenerateBillingData(ACCOUNT_ID, CLUSTER_ID, START_TIME);
    assertThat(generateBillingData).isTrue();

    boolean generateBillingDataCached =
        billingDataGenerationValidator.shouldGenerateBillingData(ACCOUNT_ID, CLUSTER_ID, START_TIME);
    assertThat(generateBillingDataCached).isTrue();
  }

  private LastReceivedPublishedMessage lastReceivedPublishedMessage(long lastReceivedAt) {
    return LastReceivedPublishedMessage.builder()
        .accountId(ACCOUNT_ID)
        .identifier(CLUSTER_ID)
        .lastReceivedAt(lastReceivedAt)
        .build();
  }
}
