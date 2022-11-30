/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.perpetualtask.PerpetualTaskScheduleConfig;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class PerpetualTaskScheduleServiceImplTest extends WingsBaseTest {
  @InjectMocks @Inject PerpetualTaskScheduleServiceImpl perpetualTaskScheduleService;

  private final String ACCOUNT_ID = "test-account-id";
  private final String PERPETUAL_TASK_TYPE = "test-perpetual-task-type";
  private final long TIME_INTERVAL_IN_MILLIS_1 = 300000L;
  private final long TIME_INTERVAL_IN_MILLIS_2 = 600000L;

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testSave() {
    PerpetualTaskScheduleConfig perpetualTaskScheduleConfig1 =
        perpetualTaskScheduleService.save(ACCOUNT_ID, PERPETUAL_TASK_TYPE, TIME_INTERVAL_IN_MILLIS_1);
    assertThat(perpetualTaskScheduleConfig1.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(perpetualTaskScheduleConfig1.getPerpetualTaskType()).isEqualTo(PERPETUAL_TASK_TYPE);
    assertThat(perpetualTaskScheduleConfig1.getTimeIntervalInMillis()).isEqualTo(TIME_INTERVAL_IN_MILLIS_1);

    PerpetualTaskScheduleConfig perpetualTaskScheduleConfig2 =
        perpetualTaskScheduleService.save(ACCOUNT_ID, PERPETUAL_TASK_TYPE, TIME_INTERVAL_IN_MILLIS_2);
    assertThat(perpetualTaskScheduleConfig2.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(perpetualTaskScheduleConfig2.getPerpetualTaskType()).isEqualTo(PERPETUAL_TASK_TYPE);
    assertThat(perpetualTaskScheduleConfig2.getTimeIntervalInMillis()).isEqualTo(TIME_INTERVAL_IN_MILLIS_2);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testDeleteByAccountId() {
    PerpetualTaskScheduleConfig perpetualTaskScheduleConfig1 =
        perpetualTaskScheduleService.save(ACCOUNT_ID, PERPETUAL_TASK_TYPE, TIME_INTERVAL_IN_MILLIS_1);
    assertThat(perpetualTaskScheduleConfig1.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(perpetualTaskScheduleConfig1.getPerpetualTaskType()).isEqualTo(PERPETUAL_TASK_TYPE);
    perpetualTaskScheduleService.deleteByAccountId(ACCOUNT_ID);
    PerpetualTaskScheduleConfig perpetualTaskScheduleConfig2 =
        perpetualTaskScheduleService.getByAccountIdAndPerpetualTaskType(ACCOUNT_ID, PERPETUAL_TASK_TYPE);
    assertThat(perpetualTaskScheduleConfig2).isNull();
  }
  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetByAccountIdAndPerpetualTaskType() {
    perpetualTaskScheduleService.save(ACCOUNT_ID, PERPETUAL_TASK_TYPE, TIME_INTERVAL_IN_MILLIS_1);

    PerpetualTaskScheduleConfig perpetualTaskScheduleConfig1 =
        perpetualTaskScheduleService.getByAccountIdAndPerpetualTaskType(ACCOUNT_ID, PERPETUAL_TASK_TYPE);
    assertThat(perpetualTaskScheduleConfig1.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(perpetualTaskScheduleConfig1.getPerpetualTaskType()).isEqualTo(PERPETUAL_TASK_TYPE);
    assertThat(perpetualTaskScheduleConfig1.getTimeIntervalInMillis()).isEqualTo(TIME_INTERVAL_IN_MILLIS_1);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testResetByAccountIdAndPerpetualTaskType() {
    perpetualTaskScheduleService.save(ACCOUNT_ID, PERPETUAL_TASK_TYPE, TIME_INTERVAL_IN_MILLIS_1);

    Boolean isDeleted = perpetualTaskScheduleService.resetByAccountIdAndPerpetualTaskType(
        ACCOUNT_ID, PERPETUAL_TASK_TYPE, TIME_INTERVAL_IN_MILLIS_1);
    assertThat(isDeleted).isEqualTo(true);
  }
}
