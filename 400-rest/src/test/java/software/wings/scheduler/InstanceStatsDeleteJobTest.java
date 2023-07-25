/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.harness.account.services.AccountService;
import io.harness.dataretention.LongerDataRetentionService;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.Account;
import software.wings.beans.AccountStatus;
import software.wings.beans.LicenseInfo;

import java.sql.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;

class InstanceStatsDeleteJobTest {
  @Mock private TimeScaleDBService timeScaleDBService;
  @Mock private PersistentLocker persistentLocker;
  @Mock private AccountService accountService;
  @Mock private LongerDataRetentionService longerDataRetentionService;

  @Mock private FeatureFlagService featureFlagService;

  @InjectMocks private InstanceStatsDeleteJob instanceStatsDeleteJob;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void testExecuteWithActiveAccount() throws SQLException {
    // Mocking account data
    String accountId = "testAccountId";
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setExpiryTime(System.currentTimeMillis() + 1000L); // Expiry time set to the future
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    Account account = new Account();
    account.setLicenseInfo(licenseInfo);

    // Mocking behavior of external services
    doReturn(account).when(accountService.getAccount(eq(accountId)));

    // Executing the method under test
    instanceStatsDeleteJob.execute(getJobExecutionContextWithAccountId(accountId));

    // Verifying that the instance stats deletion should not be triggered for an active account
    verify(featureFlagService, never()).isEnabled(any(), anyString());
    verify(longerDataRetentionService, never()).isLongerDataRetentionCompleted(any(), anyString());
    verify(timeScaleDBService, never()).getDBConnection();
    // Add more verifications as needed based on your test case.
  }

  @Test
  void testExecuteWithExpiredAccount() throws SQLException {
    // Mocking account data
    String accountId = "testExpiredAccountId";
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setExpiryTime(System.currentTimeMillis() - 1000L); // Expiry time set to the past (expired)
    licenseInfo.setAccountStatus(AccountStatus.EXPIRED);
    Account account = new Account();
    account.setLicenseInfo(licenseInfo);

    // Mocking behavior of external services
    doReturn(account).when(accountService.getAccount(eq(accountId)));
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
             .executeQuery())
        .thenReturn(mock(ResultSet.class));

    // Executing the method under test
    instanceStatsDeleteJob.execute(getJobExecutionContextWithAccountId(accountId));

    // Verifying that the instance stats deletion should be triggered for an expired account
    verify(featureFlagService, times(1)).isEnabled(any(), anyString());
    verify(longerDataRetentionService, times(1)).isLongerDataRetentionCompleted(any(), anyString());
    verify(timeScaleDBService, times(1)).getDBConnection();
    // Add more verifications as needed based on your test case.
  }

  @Test
  void testExecuteWithExpiredAccountRetryException() throws SQLException {
    // Mocking account data
    String accountId = "testExpiredAccountId";
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setExpiryTime(System.currentTimeMillis() - 1000L); // Expiry time set to the past (expired)
    licenseInfo.setAccountStatus(AccountStatus.EXPIRED);
    Account account = new Account();
    account.setLicenseInfo(licenseInfo);

    // Mocking behavior of external services
    doReturn(account).when(accountService.getAccount(eq(accountId)));
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(true);
    when(longerDataRetentionService.isLongerDataRetentionCompleted(any(), anyString())).thenReturn(false);
    when(persistentLocker.tryToAcquireLock(any(), anyString(), any())).thenReturn(mock(AcquiredLock.class));
    when(timeScaleDBService.getDBConnection()).thenThrow(new SQLException("Simulated DB connection exception"));
    // The job should retry MAX_RETRY times before failing, so we mock the maximum retries here
    when(timeScaleDBService.getDBConnection().prepareStatement(anyString()))
        .thenThrow(new SQLException("Simulated prepareStatement exception", null, 2000));
    when(timeScaleDBService.getDBConnection().prepareStatement(InstanceStatsDeleteJob.DELETE_INSTANCE_DATA_POINTS))
        .thenThrow(new SQLException("Simulated deleteStatement exception", null, 2000));
    when(timeScaleDBService.getDBConnection().prepareStatement(
             InstanceStatsDeleteJob.GET_FIRST_REPORTEDAT_INSTANCE_STAT_DATE))
        .thenThrow(new SQLException("Simulated fetchOldestInstanceStatsRecordStatement exception", null, 2000));
    when(timeScaleDBService.getDBConnection()
             .prepareStatement(InstanceStatsDeleteJob.GET_FIRST_REPORTEDAT_INSTANCE_STAT_DATE)
             .executeQuery())
        .thenReturn(mock(ResultSet.class));

    // Executing the method under test
    instanceStatsDeleteJob.execute(getJobExecutionContextWithAccountId(accountId));

    // Verifying that the instance stats deletion should be triggered for an expired account
    verify(featureFlagService, times(1)).isEnabled(any(), anyString());
    verify(longerDataRetentionService, times(1)).isLongerDataRetentionCompleted(any(), anyString());
    verify(timeScaleDBService, times(InstanceStatsDeleteJob.MAX_RETRY)).getDBConnection();
    // Add more verifications as needed based on your test case.
  }

  // Add more test cases for other scenarios as needed

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
