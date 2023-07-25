/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.dataretention.LongerDataRetentionService;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.Account;
import software.wings.beans.AccountStatus;
import software.wings.beans.LicenseInfo;
import software.wings.service.intfc.AccountService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;

public class InstanceStatsDeleteJobTest extends CategoryTest {
  @Mock private TimeScaleDBService timeScaleDBService;
  @Mock private PersistentLocker persistentLocker;
  @Mock private AccountService accountService;
  @Mock private LongerDataRetentionService longerDataRetentionService;

  @Mock private FeatureFlagService featureFlagService;
  public static final long TWO_MONTH_IN_MILLIS = 5184000000L;

  @InjectMocks private InstanceStatsDeleteJob instanceStatsDeleteJob;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testExecuteWithActiveAccount() throws SQLException {
    // Mocking account data
    String accountId = "testAccountId";
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setExpiryTime(System.currentTimeMillis() + 1000L); // Expiry time set to the future
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    Account account = new Account();
    account.setLicenseInfo(licenseInfo);

    // Mocking behavior of external services
    doReturn(account).when(accountService).get(eq(accountId));

    // Executing the method under test
    instanceStatsDeleteJob.execute(getJobExecutionContextWithAccountId(accountId));

    // Verifying that the instance stats deletion should not be triggered for an active account
    verify(featureFlagService, never()).isEnabled(any(), anyString());
    verify(longerDataRetentionService, never()).isLongerDataRetentionCompleted(any(), anyString());
    verify(timeScaleDBService, never()).getDBConnection();
    // Add more verifications as needed based on your test case.
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testExecuteWithExpiredAccount() throws SQLException {
    // Mocking account data
    String accountId = "testExpiredAccountId";
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setExpiryTime(
        System.currentTimeMillis() - TWO_MONTH_IN_MILLIS); // Expiry time set to the past (expired)
    licenseInfo.setAccountStatus(AccountStatus.EXPIRED);
    Account account = new Account();
    account.setLicenseInfo(licenseInfo);

    // Mocking behavior of external services
    doReturn(account).when(accountService).get(eq(accountId));
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(true);
    when(longerDataRetentionService.isLongerDataRetentionCompleted(any(), anyString())).thenReturn(false);
    when(persistentLocker.tryToAcquireLock(any(), anyString(), any())).thenReturn(mock(AcquiredLock.class));
    when(timeScaleDBService.getDBConnection()).thenReturn(mock(Connection.class));
    when(timeScaleDBService.getDBConnection().prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));
    when(timeScaleDBService.getDBConnection().prepareStatement(InstanceStatsDeleteJob.DELETE_INSTANCE_DATA_POINTS))
        .thenReturn(mock(PreparedStatement.class));
    when(timeScaleDBService.getDBConnection().prepareStatement(
             InstanceStatsDeleteJob.GET_FIRST_REPORTEDAT_INSTANCE_STAT_DATE))
        .thenReturn(mock(PreparedStatement.class));
    when(timeScaleDBService.getDBConnection()
             .prepareStatement(InstanceStatsDeleteJob.GET_FIRST_REPORTEDAT_INSTANCE_STAT_DATE)
             .execute())
        .thenReturn(true);

    // Executing the method under test
    instanceStatsDeleteJob.execute(getJobExecutionContextWithAccountId(accountId));

    // Verifying that the instance stats deletion should be triggered for an expired account
    verify(featureFlagService, times(1)).isEnabled(any(), anyString());
    verify(longerDataRetentionService, times(1)).isLongerDataRetentionCompleted(any(), anyString());
    verify(timeScaleDBService, times(5)).getDBConnection();
    // Add more verifications as needed based on your test case.
  }

  private JobExecutionContext getJobExecutionContextWithAccountId(String accountId) {
    JobExecutionContext jobExecutionContext = mock(JobExecutionContext.class);
    JobDetail jobDetail = mock(JobDetail.class);
    JobDataMap jobDataMap = mock(JobDataMap.class);

    when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);
    when(jobDataMap.get(InstanceStatsDeleteJob.ACCOUNT_ID_KEY)).thenReturn(accountId);
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail);

    return jobExecutionContext;
  }
}
