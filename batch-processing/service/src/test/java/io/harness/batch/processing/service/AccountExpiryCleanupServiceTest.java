/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service;

import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.dao.intfc.BillingDataPipelineRecordDao;
import io.harness.batch.processing.pricing.gcp.bigquery.BigQueryHelperService;
import io.harness.batch.processing.service.intfc.AccountExpiryService;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.billing.BillingDataPipelineRecord;
import io.harness.ccm.license.CeLicenseInfo;
import io.harness.rule.Owner;

import software.wings.beans.Account;
import software.wings.beans.SettingAttribute;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.beans.ce.CEGcpConfig;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AccountExpiryCleanupServiceTest extends CategoryTest {
  public static final String ACTIVE_ACCOUNT = "activeAccount";
  public static final String EXPIRED_ACCOUNT = "expiredAccount";
  public static final String GRACE_PERIOD = "gracePeriod";
  private static final String ACCOUNT_ID_1 = "accountId1";
  private static final String ACCOUNT_ID_2 = "accountId2";
  private static final String ACCOUNT_ID_3 = "accountId3";
  private static final String SETTING_ID_1 = "settingId1";
  private static final String SETTING_ID_2 = "settingId2";
  private static final String SETTING_ID_3 = "settingId3";
  public static final String ORG_SETTING_ID = "orgSettingId";
  @Inject @InjectMocks private AccountExpiryCleanupService accountExpiryCleanupService;
  @Mock private BillingDataPipelineRecordDao billingDataPipelineRecordDao;
  @Mock private AccountExpiryService accountExpiryService;
  @Mock private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Mock private BigQueryHelperService bigQueryHelperService;

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void write() {
    when(accountExpiryService.dataPipelineCleanup(any())).thenReturn(true);

    List<Account> accountList = new ArrayList<>();
    Instant instant = Instant.now();
    long activeLicense = instant.plus(10, ChronoUnit.DAYS).toEpochMilli();
    long gracePeriodLicense = instant.minus(10, ChronoUnit.DAYS).toEpochMilli();
    long expiredLicense = instant.minus(20, ChronoUnit.DAYS).toEpochMilli();

    Account activeAccount = new Account();
    activeAccount.setUuid(ACCOUNT_ID_1);
    activeAccount.setAccountName(ACTIVE_ACCOUNT);
    activeAccount.setCeLicenseInfo(CeLicenseInfo.builder().expiryTime(activeLicense).build());

    Account gracePeriodAccount = new Account();
    gracePeriodAccount.setUuid(ACCOUNT_ID_2);
    gracePeriodAccount.setAccountName(GRACE_PERIOD);
    gracePeriodAccount.setCeLicenseInfo(CeLicenseInfo.builder().expiryTime(gracePeriodLicense).build());

    Account expiredAccount = new Account();
    expiredAccount.setUuid(ACCOUNT_ID_3);
    expiredAccount.setAccountName(EXPIRED_ACCOUNT);
    expiredAccount.setCeLicenseInfo(CeLicenseInfo.builder().expiryTime(expiredLicense).build());

    accountList.add(activeAccount);
    accountList.add(gracePeriodAccount);
    accountList.add(expiredAccount);

    when(cloudToHarnessMappingService.getCeAccountsWithLicense()).thenReturn(accountList);
    SettingAttribute settingAttribute1 = new SettingAttribute();
    settingAttribute1.setUuid(SETTING_ID_1);
    CEGcpConfig ceGcpConfig = CEGcpConfig.builder().organizationSettingId(ORG_SETTING_ID).build();
    ceGcpConfig.setType(SettingVariableTypes.CE_GCP.toString());
    settingAttribute1.setValue(ceGcpConfig);
    when(cloudToHarnessMappingService.getCEConnectors(ACCOUNT_ID_1))
        .thenReturn(Collections.singletonList(settingAttribute1));

    SettingAttribute settingAttribute2 = new SettingAttribute();
    CEAwsConfig ceAwsConfig = CEAwsConfig.builder().build();
    ceAwsConfig.setType(SettingVariableTypes.CE_AWS.toString());
    settingAttribute2.setValue(ceAwsConfig);
    settingAttribute2.setUuid(SETTING_ID_2);
    when(cloudToHarnessMappingService.getCEConnectors(ACCOUNT_ID_2))
        .thenReturn(Collections.singletonList(settingAttribute2));

    SettingAttribute settingAttribute3 = new SettingAttribute();
    settingAttribute3.setUuid(SETTING_ID_3);
    settingAttribute3.setValue(ceAwsConfig);
    when(cloudToHarnessMappingService.getCEConnectors(ACCOUNT_ID_3))
        .thenReturn(Collections.singletonList(settingAttribute3));

    when(billingDataPipelineRecordDao.getAllRecordsByAccountId(ACCOUNT_ID_1))
        .thenReturn(Collections.singletonList(BillingDataPipelineRecord.builder().settingId(ORG_SETTING_ID).build()));
    when(billingDataPipelineRecordDao.getAllRecordsByAccountId(ACCOUNT_ID_2))
        .thenReturn(Collections.singletonList(BillingDataPipelineRecord.builder().settingId(SETTING_ID_2).build()));
    when(billingDataPipelineRecordDao.getAllRecordsByAccountId(ACCOUNT_ID_3))
        .thenReturn(Collections.singletonList(BillingDataPipelineRecord.builder().settingId(SETTING_ID_3).build()));

    accountExpiryCleanupService.execute();

    ArgumentCaptor<Account> accountArgumentCaptor = ArgumentCaptor.forClass(Account.class);
    verify(accountExpiryService).dataPipelineCleanup(accountArgumentCaptor.capture());
    Account account = accountArgumentCaptor.getValue();
    assertThat(account.getAccountName()).isEqualTo(EXPIRED_ACCOUNT);
  }
}
