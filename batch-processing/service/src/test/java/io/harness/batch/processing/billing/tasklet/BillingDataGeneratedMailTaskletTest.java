/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.tasklet;

import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.batch.processing.billing.tasklet.dao.intfc.DataGeneratedNotificationDao;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.mail.CEMailNotificationService;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.utils.TimeUtils;
import io.harness.rule.Owner;
import io.harness.testsupport.BaseTaskletTest;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.User;
import software.wings.beans.security.AccountPermissions;
import software.wings.beans.security.UserGroup;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@RunWith(MockitoJUnitRunner.class)
public class BillingDataGeneratedMailTaskletTest extends BaseTaskletTest {
  @Mock private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Mock private DataGeneratedNotificationDao notificationDao;
  @Mock private TimeScaleDBService timeScaleDBService;
  @Mock private TimeUtils utils;
  @Mock private CEMailNotificationService emailNotificationService;
  @Mock private JobParameters parameters;
  @Mock private CEMetadataRecordDao metadataRecordDao;
  @InjectMocks private BillingDataGeneratedMailTasklet tasklet;

  @Mock Statement statement;
  @Mock ResultSet resultSet;

  private static final long TIME = System.currentTimeMillis();
  final int[] count = {0};

  @Before
  public void setup() throws SQLException {
    Connection connection = mock(Connection.class);
    statement = mock(Statement.class);
    resultSet = mock(ResultSet.class);
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenReturn(resultSet);
    when(resultSet.getString("CLUSTERID")).thenAnswer((Answer<String>) invocation -> "CLUSTERID");
    when(resultSet.getString("CLUSTERNAME")).thenAnswer((Answer<String>) invocation -> "CLUSTERNAME");
    when(resultSet.getTimestamp("STARTTIME", utils.getDefaultCalendar()))
        .thenAnswer((Answer<Timestamp>) invocation -> new Timestamp(TIME));
    returnResultSet(1);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testExecute() throws Exception {
    when(parameters.getString(CCMJobConstants.ACCOUNT_ID)).thenReturn(ACCOUNT_ID);
    when(notificationDao.isMailSent(ACCOUNT_ID)).thenReturn(true);
    when(cloudToHarnessMappingService.listUserGroupsForAccount(ACCOUNT_ID))
        .thenReturn(Collections.singletonList(
            UserGroup.builder()
                .accountId(ACCOUNT_ID)
                .accountPermissions(
                    AccountPermissions.builder().permissions(Collections.singleton(PermissionType.CE_ADMIN)).build())
                .memberIds(Collections.singletonList("USERID"))
                .build()));
    when(cloudToHarnessMappingService.getUser("USERID"))
        .thenReturn(User.Builder.anUser().name("name").email("email").uuid("USERID").build());
    RepeatStatus repeatStatus = tasklet.execute(null, chunkContext);
    assertThat(repeatStatus).isNull();

    when(notificationDao.isMailSent(ACCOUNT_ID)).thenReturn(false);
    repeatStatus = tasklet.execute(null, chunkContext);
    assertThat(repeatStatus).isNull();
  }

  private void returnResultSet(int limit) throws SQLException {
    when(resultSet.next()).then((Answer<Boolean>) invocation -> {
      if (count[0] < limit) {
        count[0]++;
        return true;
      }
      count[0] = 0;
      return false;
    });
  }
}
