/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.PL_UPDATE_CONNECTOR_HEARTBEAT_PPT;
import static io.harness.perpetualtask.PerpetualTaskType.CONNECTOR_TEST_CONNECTION;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.mongo.MongoPersistence;
import io.harness.perpetualtask.PerpetualTaskState;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PL)
public class UpdateHeartBeatPerpetualTaskJobTest extends WingsBaseTest {
  @Inject
  @InjectMocks
  private UpdateHeartBeatIntervalAndResetPerpetualTaskJob updateHeartBeatIntervalAndResetPerpetualTaskJob;
  @Inject private MongoPersistence mongoPersistence;
  @Mock private AccountService accountService;
  private final String ACCOUNT_ID1 = "ACCOUNT_ID1";
  private final String UUID1 = "UUID1";
  private final String DELEGATE_ID1 = "DELEGATE_ID1";
  private final String ACCOUNT_ID2 = "ACCOUNT_ID2";
  private final String UUID2 = "UUID2";
  private final String DELEGATE_ID2 = "DELEGATE_ID2";

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testUpdateAccount() {
    PerpetualTaskRecord taskRecord = new PerpetualTaskRecord();
    taskRecord.setUuid(UUID1);
    taskRecord.setAccountId(ACCOUNT_ID1);
    taskRecord.setState(PerpetualTaskState.TASK_ASSIGNED);
    taskRecord.setPerpetualTaskType(CONNECTOR_TEST_CONNECTION);
    taskRecord.setIntervalSeconds(600);
    taskRecord.setDelegateId(DELEGATE_ID1);
    mongoPersistence.save(taskRecord);
    updateHeartBeatIntervalAndResetPerpetualTaskJob.updateForAccount(ACCOUNT_ID1);
    PerpetualTaskRecord finalRecord = mongoPersistence.get(PerpetualTaskRecord.class, UUID1);
    assertNotNull(finalRecord);
    assertEquals(1800, finalRecord.getIntervalSeconds());
    assertEquals(PerpetualTaskState.TASK_UNASSIGNED, finalRecord.getState());
    assertEquals("", finalRecord.getDelegateId());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testMigration() {
    PerpetualTaskRecord taskRecord1 = new PerpetualTaskRecord();
    taskRecord1.setUuid(UUID1);
    taskRecord1.setAccountId(ACCOUNT_ID1);
    taskRecord1.setState(PerpetualTaskState.TASK_ASSIGNED);
    taskRecord1.setPerpetualTaskType(CONNECTOR_TEST_CONNECTION);
    taskRecord1.setIntervalSeconds(600);
    taskRecord1.setDelegateId(DELEGATE_ID1);
    mongoPersistence.save(taskRecord1);
    PerpetualTaskRecord taskRecord2 = new PerpetualTaskRecord();
    taskRecord2.setUuid(UUID2);
    taskRecord2.setAccountId(ACCOUNT_ID2);
    taskRecord2.setState(PerpetualTaskState.TASK_ASSIGNED);
    taskRecord2.setPerpetualTaskType(CONNECTOR_TEST_CONNECTION);
    taskRecord2.setIntervalSeconds(600);
    taskRecord2.setDelegateId(DELEGATE_ID2);
    mongoPersistence.save(taskRecord2);
    Set<String> accountIds = new HashSet<>(Collections.singleton(ACCOUNT_ID1));
    accountIds.addAll(new ArrayList<>());
    when(accountService.getFeatureFlagEnabledAccountIds(PL_UPDATE_CONNECTOR_HEARTBEAT_PPT.name()))
        .thenReturn(accountIds);
    updateHeartBeatIntervalAndResetPerpetualTaskJob.execute();
    PerpetualTaskRecord finalRecord1 = mongoPersistence.get(PerpetualTaskRecord.class, UUID1);
    PerpetualTaskRecord finalRecord2 = mongoPersistence.get(PerpetualTaskRecord.class, UUID2);
    assertNotNull(finalRecord1);
    assertNotNull(finalRecord2);
    assertEquals(1800, finalRecord1.getIntervalSeconds());
    assertEquals(600, finalRecord2.getIntervalSeconds());
  }
}
