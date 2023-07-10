/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.licensing;

import static io.harness.annotations.dev.HarnessTeam.GTM;
import static io.harness.data.encoding.EncodingUtils.decodeBase64;
import static io.harness.rule.OwnerRule.MEHUL;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.VOJIN;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.common.Constants.HARNESS_NAME;
import static software.wings.service.intfc.instance.licensing.InstanceLimitProvider.DEFAULT_SI_USAGE_LIMITS;
import static software.wings.service.intfc.instance.licensing.InstanceLimitProvider.defaults;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.configuration.DeployMode;
import io.harness.exception.WingsException;
import io.harness.licensing.beans.response.CheckExpiryResultDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.AccountStatus;
import software.wings.beans.AccountType;
import software.wings.beans.LicenseInfo;
import software.wings.service.impl.LicenseUtils;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Created by peeyushaggarwal on 10/11/16.
 */
@OwnedBy(GTM)
public class LicenseServiceTest extends WingsBaseTest {
  @InjectMocks @Inject private AccountService accountService;
  @InjectMocks @Inject private LicenseServiceImpl licenseService;

  @Mock private MainConfiguration mainConfiguration;
  @Mock private NgLicenseHttpClient ngLicenseHttpClient;

  @Rule public ExpectedException thrown = ExpectedException.none();

  private static final long oneDayTimeDiff = 86400000L;
  private static final String ACCOUNT_KEY = "ACCOUNT_KEY";
  private static final String TRIAL_EXPIRATION_DAY_0_TEMPLATE = "trial_expiration_day0";
  private static final String TRIAL_EXPIRATION_DAY_29_TEMPLATE = "trial_expiration_day29";
  private static final String TRIAL_EXPIRATION_DAY_30_TEMPLATE = "trial_expiration_day30";
  private static final String TRIAL_EXPIRATION_DAY_60_TEMPLATE = "trial_expiration_day60";
  private static final String TRIAL_EXPIRATION_DAY_89_TEMPLATE = "trial_expiration_day89";
  private static final String TRIAL_EXPIRATION_BEFORE_DELETION_TEMPLATE = "trial_expiration_before_deletion";

  @Before
  public void setup() throws IllegalAccessException, IOException {
    FieldUtils.writeField(licenseService, "accountService", accountService, true);
    FieldUtils.writeField(accountService, "licenseService", licenseService, true);
    FieldUtils.writeField(licenseService, "ngLicenseHttpClient", ngLicenseHttpClient, true);

    Call<ResponseDTO<Boolean>> booleanResult = mock(Call.class);
    when(booleanResult.execute()).thenReturn(Response.success(ResponseDTO.newResponse(true)));
    when(ngLicenseHttpClient.softDelete(any())).thenReturn(booleanResult);
    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldFailSavingAccountWithoutLicense() {
    thrown.expect(WingsException.class);
    thrown.expectMessage("Invalid / Null license info");
    accountService.save(
        anAccount().withCompanyName(HARNESS_NAME).withAccountName(HARNESS_NAME).withAccountKey(ACCOUNT_KEY).build(),
        false);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldSaveTrialAccountWithDefaultValues() {
    long expiryTime = LicenseUtils.getDefaultTrialExpiryTime();
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.TRIAL);
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    Account account = accountService.save(anAccount()
                                              .withCompanyName(HARNESS_NAME)
                                              .withAccountName(HARNESS_NAME)
                                              .withAccountKey(ACCOUNT_KEY)
                                              .withLicenseInfo(licenseInfo)
                                              .build(),
        false);
    Account accountFromDB = accountService.get(account.getUuid());
    assertThat(accountFromDB.getLicenseInfo()).isNotNull();
    assertThat(accountFromDB.getLicenseInfo().getAccountType()).isEqualTo(AccountType.TRIAL);
    assertThat(accountFromDB.getLicenseInfo().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(accountFromDB.getLicenseInfo().getExpiryTime()).isEqualTo(expiryTime);
    assertThat(accountFromDB.getLicenseInfo().getLicenseUnits()).isEqualTo(defaults(AccountType.TRIAL));
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldSaveAccountWithSpecificType() {
    long timestamp = System.currentTimeMillis() + 100000;
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.PAID);
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    licenseInfo.setLicenseUnits(100);
    licenseInfo.setExpiryTime(timestamp);
    Account account = accountService.save(anAccount()
                                              .withCompanyName(HARNESS_NAME)
                                              .withAccountName(HARNESS_NAME)
                                              .withAccountKey(ACCOUNT_KEY)
                                              .withLicenseInfo(licenseInfo)
                                              .build(),
        false);
    Account accountFromDB = accountService.get(account.getUuid());
    assertThat(accountFromDB.getLicenseInfo()).isNotNull();
    assertThat(accountFromDB.getLicenseInfo().getAccountType()).isEqualTo(AccountType.PAID);
    assertThat(accountFromDB.getLicenseInfo().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(accountFromDB.getLicenseInfo().getExpiryTime()).isEqualTo(timestamp);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldSaveAccountWithSpecificTypeAndExpiryTime() {
    long expiryTime = LicenseUtils.getDefaultTrialExpiryTime() + 100000;
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.TRIAL);
    licenseInfo.setExpiryTime(expiryTime);
    Account account = accountService.save(anAccount()
                                              .withCompanyName(HARNESS_NAME)
                                              .withAccountName(HARNESS_NAME)
                                              .withAccountKey(ACCOUNT_KEY)
                                              .withLicenseInfo(licenseInfo)
                                              .build(),
        false);
    Account accountFromDB = accountService.get(account.getUuid());
    assertThat(accountFromDB.getLicenseInfo()).isNotNull();
    assertThat(accountFromDB.getLicenseInfo().getAccountType()).isEqualTo(AccountType.TRIAL);
    assertThat(accountFromDB.getLicenseInfo().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(accountFromDB.getLicenseInfo().getExpiryTime()).isEqualTo(expiryTime);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldUpdatePaidAccountWithDefaultValues() {
    long expiryTime = LicenseUtils.getDefaultTrialExpiryTime() + 100000;
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.PAID);
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    licenseInfo.setExpiryTime(expiryTime);
    licenseInfo.setLicenseUnits(20);

    Account account = accountService.save(anAccount()
                                              .withCompanyName(HARNESS_NAME)
                                              .withAccountName(HARNESS_NAME)
                                              .withAccountKey(ACCOUNT_KEY)
                                              .withLicenseInfo(licenseInfo)
                                              .build(),
        false);
    Account accountFromDB = accountService.get(account.getUuid());
    licenseService.updateAccountLicense(accountFromDB.getUuid(), accountFromDB.getLicenseInfo());
    accountFromDB = accountService.get(account.getUuid());
    assertThat(accountFromDB.getLicenseInfo()).isNotNull();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldUpdateTrialAccountWithDefaultValues() {
    long expiryTime = LicenseUtils.getDefaultTrialExpiryTime() + 100000;
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.TRIAL);
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    licenseInfo.setExpiryTime(expiryTime);

    Account account = accountService.save(anAccount()
                                              .withCompanyName(HARNESS_NAME)
                                              .withAccountName(HARNESS_NAME)
                                              .withAccountKey(ACCOUNT_KEY)
                                              .withLicenseInfo(licenseInfo)
                                              .build(),
        false);
    Account accountFromDB = accountService.get(account.getUuid());

    long newExpiryTime = System.currentTimeMillis() + 400000;
    LicenseInfo updatedLicenseInfo = new LicenseInfo();
    updatedLicenseInfo.setExpiryTime(newExpiryTime);

    licenseService.updateAccountLicense(accountFromDB.getUuid(), updatedLicenseInfo);
    accountFromDB = accountService.get(account.getUuid());
    assertThat(accountFromDB.getLicenseInfo()).isNotNull();
    assertThat(accountFromDB.getLicenseInfo().getAccountType()).isEqualTo(AccountType.TRIAL);
    assertThat(accountFromDB.getLicenseInfo().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(accountFromDB.getLicenseInfo().getExpiryTime()).isEqualTo(newExpiryTime);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldFailToUpdateTrialAccountWithNullLicenseInfo() {
    long expiryTime = LicenseUtils.getDefaultTrialExpiryTime();
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.TRIAL);
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    licenseInfo.setExpiryTime(expiryTime);

    Account account = accountService.save(anAccount()
                                              .withCompanyName(HARNESS_NAME)
                                              .withAccountName(HARNESS_NAME)
                                              .withAccountKey(ACCOUNT_KEY)
                                              .withLicenseInfo(licenseInfo)
                                              .build(),
        false);
    Account accountFromDB = accountService.get(account.getUuid());
    thrown.expect(WingsException.class);
    thrown.expectMessage("Invalid / Null license info for update");
    licenseService.updateAccountLicense(accountFromDB.getUuid(), null);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldUpdateTrialAccount3WithDefaultValues() {
    long expiryTime = LicenseUtils.getDefaultTrialExpiryTime();
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.TRIAL);
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    licenseInfo.setExpireAfterDays(1);
    licenseInfo.setLicenseUnits(20);

    Account account = accountService.save(anAccount()
                                              .withCompanyName(HARNESS_NAME)
                                              .withAccountName(HARNESS_NAME)
                                              .withAccountKey(ACCOUNT_KEY)
                                              .withLicenseInfo(licenseInfo)
                                              .build(),
        false);
    Account accountFromDB = accountService.get(account.getUuid());

    LicenseInfo updatedLicenseInfo = new LicenseInfo();
    updatedLicenseInfo.setAccountStatus(AccountStatus.EXPIRED);
    updatedLicenseInfo.setExpiryTime(expiryTime);

    licenseService.updateAccountLicense(accountFromDB.getUuid(), updatedLicenseInfo);
    accountFromDB = accountService.get(account.getUuid());
    assertThat(accountFromDB.getLicenseInfo()).isNotNull();
    assertThat(accountFromDB.getLicenseInfo().getAccountType()).isEqualTo(AccountType.TRIAL);
    assertThat(accountFromDB.getLicenseInfo().getAccountStatus()).isEqualTo(AccountStatus.EXPIRED);
    assertThat(accountFromDB.getLicenseInfo().getExpiryTime()).isEqualTo(expiryTime);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldUpdateAccountWithSpecificType() {
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.TRIAL);
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    licenseInfo.setLicenseUnits(20);
    Account account = accountService.save(anAccount()
                                              .withCompanyName(HARNESS_NAME)
                                              .withAccountName(HARNESS_NAME)
                                              .withAccountKey(ACCOUNT_KEY)
                                              .withLicenseInfo(licenseInfo)
                                              .build(),
        false);
    Account accountFromDB = accountService.get(account.getUuid());
    LicenseInfo updatedLicenseInfo = new LicenseInfo();
    updatedLicenseInfo.setAccountType(AccountType.PAID);
    accountFromDB.setLicenseInfo(updatedLicenseInfo);
    licenseService.updateAccountLicense(accountFromDB.getUuid(), updatedLicenseInfo);
    accountFromDB = accountService.get(account.getUuid());
    assertThat(accountFromDB.getLicenseInfo()).isNotNull();
    assertThat(accountFromDB.getLicenseInfo().getAccountType()).isEqualTo(AccountType.PAID);
    assertThat(accountFromDB.getLicenseInfo().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(accountFromDB.getLicenseInfo().getExpiryTime()).isNotEqualTo(0);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldUpdateAccountWithSpecificTypeAndExpiryTime() {
    long expiryTime = LicenseUtils.getDefaultTrialExpiryTime() + 100000;
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.PAID);
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    licenseInfo.setLicenseUnits(10);
    licenseInfo.setExpiryTime(expiryTime);

    Account account = accountService.save(anAccount()
                                              .withCompanyName(HARNESS_NAME)
                                              .withAccountName(HARNESS_NAME)
                                              .withAccountKey(ACCOUNT_KEY)
                                              .withLicenseInfo(licenseInfo)
                                              .build(),
        false);
    Account accountFromDB = accountService.get(account.getUuid());

    licenseInfo.setAccountStatus(AccountStatus.EXPIRED);

    accountFromDB.setLicenseInfo(licenseInfo);
    licenseService.updateAccountLicense(accountFromDB.getUuid(), licenseInfo);
    accountFromDB = accountService.get(account.getUuid());
    assertThat(accountFromDB.getLicenseInfo()).isNotNull();
    assertThat(accountFromDB.getLicenseInfo().getAccountType()).isEqualTo(AccountType.PAID);
    assertThat(accountFromDB.getLicenseInfo().getAccountStatus()).isEqualTo(AccountStatus.EXPIRED);
    assertThat(accountFromDB.getLicenseInfo().getExpiryTime()).isEqualTo(expiryTime);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldUpdateOnPremTrialAccountWithDefaultValues() {
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.TRIAL);
    licenseInfo.setAccountStatus(AccountStatus.EXPIRED);

    long expiryTime = LicenseUtils.getDefaultTrialExpiryTime();
    Account accountFromDB = accountService.save(anAccount()
                                                    .withCompanyName(HARNESS_NAME)
                                                    .withAccountName(HARNESS_NAME)
                                                    .withAccountKey(ACCOUNT_KEY)
                                                    .withLicenseInfo(licenseInfo)
                                                    .build(),
        false);
    LicenseInfo updatedLicenseInfo = new LicenseInfo();
    updatedLicenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    updatedLicenseInfo.setAccountType(AccountType.TRIAL);
    String encryptedString = getEncryptedString(updatedLicenseInfo);
    licenseService.updateAccountLicenseForOnPrem(encryptedString);
    accountFromDB = accountService.get(accountFromDB.getUuid());
    assertThat(accountFromDB.getLicenseInfo()).isNotNull();
    assertThat(accountFromDB.getLicenseInfo().getAccountType()).isEqualTo(AccountType.TRIAL);
    assertThat(accountFromDB.getLicenseInfo().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(accountFromDB.getLicenseInfo().getExpiryTime()).isEqualTo(expiryTime);
    assertThat(accountFromDB.getLicenseInfo().getLicenseUnits())
        .isEqualTo(DEFAULT_SI_USAGE_LIMITS.get(AccountType.TRIAL));
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldUpdateOnPremTrialAccountWithSpecificValues() {
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.PAID);
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    licenseInfo.setLicenseUnits(10);

    long expiryTime = LicenseUtils.getDefaultPaidExpiryTime();
    Account accountFromDB = accountService.save(anAccount()
                                                    .withCompanyName(HARNESS_NAME)
                                                    .withAccountName(HARNESS_NAME)
                                                    .withAccountKey(ACCOUNT_KEY)
                                                    .withLicenseInfo(licenseInfo)
                                                    .build(),
        false);

    LicenseInfo updatedLicenseInfo = new LicenseInfo();
    updatedLicenseInfo.setAccountStatus(AccountStatus.EXPIRED);
    updatedLicenseInfo.setAccountType(AccountType.PAID);
    updatedLicenseInfo.setLicenseUnits(20);
    String encryptedString = getEncryptedString(updatedLicenseInfo);
    licenseService.updateAccountLicenseForOnPrem(encryptedString);
    accountFromDB = accountService.get(accountFromDB.getUuid());
    assertThat(accountFromDB.getLicenseInfo()).isNotNull();
    assertThat(accountFromDB.getLicenseInfo().getAccountType()).isEqualTo(AccountType.PAID);
    assertThat(accountFromDB.getLicenseInfo().getAccountStatus()).isEqualTo(AccountStatus.EXPIRED);
    assertThat(accountFromDB.getLicenseInfo().getExpiryTime()).isEqualTo(expiryTime);
    assertThat(accountFromDB.getLicenseInfo().getLicenseUnits()).isEqualTo(20);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldGetNewLicense() {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.DATE, 1);
    calendar.set(Calendar.HOUR, 11);
    calendar.set(Calendar.MINUTE, 59);
    calendar.set(Calendar.SECOND, 59);
    calendar.set(Calendar.MILLISECOND, 0);
    calendar.set(Calendar.AM_PM, Calendar.PM);
    long expiryTime = calendar.getTimeInMillis();

    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.TRIAL);
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    licenseInfo.setExpireAfterDays(1);

    String generatedLicense = LicenseUtils.generateLicense(licenseInfo);

    Account account = new Account();
    account.setEncryptedLicenseInfo(decodeBase64(generatedLicense));
    Account accountWithDecryptedInfo = LicenseUtils.decryptLicenseInfo(account, false);
    assertThat(accountWithDecryptedInfo).isNotNull();
    assertThat(accountWithDecryptedInfo.getLicenseInfo()).isNotNull();
    assertThat(accountWithDecryptedInfo.getLicenseInfo().getExpiryTime()).isEqualTo(expiryTime);
    assertThat(accountWithDecryptedInfo.getLicenseInfo().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(accountWithDecryptedInfo.getLicenseInfo().getAccountType()).isEqualTo(AccountType.TRIAL);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldCheckForLicenseExpiry() throws InterruptedException {
    long expiryTime = System.currentTimeMillis() + 1000;
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.TRIAL);
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    licenseInfo.setExpiryTime(expiryTime);

    Account account = accountService.save(anAccount()
                                              .withCompanyName(HARNESS_NAME)
                                              .withAccountName(HARNESS_NAME)
                                              .withAccountKey(ACCOUNT_KEY)
                                              .withLicenseInfo(licenseInfo)
                                              .build(),
        false);

    TimeUnit.SECONDS.sleep(2);
    licenseService.checkForLicenseExpiry(account);
    Account accountFromDB = accountService.get(account.getUuid());
    assertThat(accountFromDB.getLicenseInfo().getAccountStatus()).isEqualTo(AccountStatus.EXPIRED);
  }

  @Test
  @Owner(developers = VOJIN)
  @Category(UnitTests.class)
  public void getEmailTemplateNameForTrialAccountExpiration() {
    long currentTime = System.currentTimeMillis();
    long expiryTime = currentTime + 1000;
    long oneDayAfterExpiry = expiryTime + oneDayTimeDiff;
    long twoDaysAfterExpiry = expiryTime + (2 * oneDayTimeDiff);
    long thirtyDaysAfterExpiry = expiryTime + (30 * oneDayTimeDiff);
    long sixtyDaysAfterExpiry = expiryTime + (60 * oneDayTimeDiff);
    long eightyNineDaysAfterExpiry = expiryTime + (89 * oneDayTimeDiff);
    long ninetyDaysAfterExpiry = expiryTime + (90 * oneDayTimeDiff);
    long ninetyTwoDaysAfterExpiry = expiryTime + (92 * oneDayTimeDiff);

    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.TRIAL);
    licenseInfo.setAccountStatus(AccountStatus.EXPIRED);
    licenseInfo.setExpiryTime(expiryTime);

    Account account = accountService.save(anAccount()
                                              .withCompanyName(HARNESS_NAME)
                                              .withAccountName(HARNESS_NAME)
                                              .withAccountKey(ACCOUNT_KEY)
                                              .withLicenseInfo(licenseInfo)
                                              .withLastLicenseExpiryReminderSentAt(expiryTime - oneDayTimeDiff)
                                              .build(),
        false);
    assertThat(licenseService.getEmailTemplateName(account, oneDayAfterExpiry, expiryTime))
        .isEqualTo(TRIAL_EXPIRATION_DAY_0_TEMPLATE);
    licenseService.updateLastLicenseExpiryReminderSentAt(account.getUuid(), oneDayAfterExpiry);
    account = accountService.get(account.getUuid());
    assertThat(licenseService.getEmailTemplateName(account, oneDayAfterExpiry + 10000, expiryTime)).isNull();
    assertThat(licenseService.getEmailTemplateName(account, twoDaysAfterExpiry, expiryTime)).isNull();

    assertThat(licenseService.getEmailTemplateName(account, thirtyDaysAfterExpiry, expiryTime))
        .isEqualTo(TRIAL_EXPIRATION_DAY_30_TEMPLATE);
    licenseService.updateLastLicenseExpiryReminderSentAt(account.getUuid(), thirtyDaysAfterExpiry);
    account = accountService.get(account.getUuid());
    assertThat(licenseService.getEmailTemplateName(account, thirtyDaysAfterExpiry + 10000, expiryTime)).isNull();

    assertThat(licenseService.getEmailTemplateName(account, sixtyDaysAfterExpiry, expiryTime))
        .isEqualTo(TRIAL_EXPIRATION_DAY_60_TEMPLATE);
    licenseService.updateLastLicenseExpiryReminderSentAt(account.getUuid(), sixtyDaysAfterExpiry);
    account = accountService.get(account.getUuid());
    assertThat(licenseService.getEmailTemplateName(account, sixtyDaysAfterExpiry + 10000, expiryTime)).isNull();
    assertThat(licenseService.getEmailTemplateName(account, eightyNineDaysAfterExpiry, expiryTime))
        .isEqualTo(TRIAL_EXPIRATION_DAY_89_TEMPLATE);
    licenseService.updateLastLicenseExpiryReminderSentAt(account.getUuid(), eightyNineDaysAfterExpiry);
    account = accountService.get(account.getUuid());

    assertThat(licenseService.getEmailTemplateName(account, ninetyDaysAfterExpiry, expiryTime))
        .isEqualTo(TRIAL_EXPIRATION_BEFORE_DELETION_TEMPLATE);
    licenseService.updateLastLicenseExpiryReminderSentAt(account.getUuid(), ninetyDaysAfterExpiry);
    account = accountService.get(account.getUuid());
    assertThat(licenseService.getEmailTemplateName(account, ninetyDaysAfterExpiry + 10000, expiryTime)).isNull();

    assertThat(licenseService.getEmailTemplateName(account, ninetyTwoDaysAfterExpiry, expiryTime))
        .isEqualTo(TRIAL_EXPIRATION_BEFORE_DELETION_TEMPLATE);
    licenseService.updateLastLicenseExpiryReminderSentAt(account.getUuid(), ninetyTwoDaysAfterExpiry);
    account = accountService.get(account.getUuid());
    assertThat(licenseService.getEmailTemplateName(account, ninetyTwoDaysAfterExpiry + 10000, expiryTime)).isNull();
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void shouldHandleTrialAccountExpiration() throws InterruptedException, IOException {
    long currentTime = System.currentTimeMillis();
    long expiryTime = currentTime + 1000;
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.TRIAL);
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    licenseInfo.setExpiryTime(expiryTime);

    Call<ResponseDTO<CheckExpiryResultDTO>> result = mock(Call.class);
    when(result.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(CheckExpiryResultDTO.builder().shouldDelete(true).expiryTime(0).build())));
    when(ngLicenseHttpClient.checkExpiry(any())).thenReturn(result);

    Account account = accountService.save(anAccount()
                                              .withCompanyName(HARNESS_NAME)
                                              .withAccountName(HARNESS_NAME)
                                              .withAccountKey(ACCOUNT_KEY)
                                              .withLicenseInfo(licenseInfo)
                                              .build(),
        false);
    TimeUnit.SECONDS.sleep(2);
    LicenseServiceImpl licenseServiceSpy = spy(licenseService);
    doReturn(true).when(licenseServiceSpy).sendEmailToAccountAdmin(any(), anyString());
    licenseServiceSpy.checkForLicenseExpiry(account);
    account = accountService.get(account.getUuid());
    long lastLicenseExpiryReminderSentAtUpdatedValue = account.getLastLicenseExpiryReminderSentAt();
    assertThat(lastLicenseExpiryReminderSentAtUpdatedValue)
        .isBetween(System.currentTimeMillis() - 10000, System.currentTimeMillis() + 10000);
    assertThat(account.getLicenseInfo().getAccountStatus()).isEqualTo(AccountStatus.EXPIRED);
  }

  @Test
  @Owner(developers = VOJIN)
  @Category(UnitTests.class)
  public void shouldUpdateAccountStatusAfterNinetyDaysOfExpiry() {
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.TRIAL);
    licenseInfo.setAccountStatus(AccountStatus.EXPIRED);

    long currentTime = System.currentTimeMillis();
    List<Long> licenseExpiryRemindersSentAt = new ArrayList<>();
    licenseExpiryRemindersSentAt.add(currentTime - 30 * oneDayTimeDiff);
    licenseExpiryRemindersSentAt.add(currentTime - 60 * oneDayTimeDiff);
    licenseExpiryRemindersSentAt.add(currentTime - oneDayTimeDiff);

    Account account = accountService.save(anAccount()
                                              .withCompanyName(HARNESS_NAME)
                                              .withAccountName(HARNESS_NAME)
                                              .withAccountKey(ACCOUNT_KEY)
                                              .withLicenseInfo(licenseInfo)
                                              .withLicenseExpiryRemindersSentAt(licenseExpiryRemindersSentAt)
                                              .build(),
        false);
    LicenseServiceImpl licenseServiceSpy = spy(licenseService);
    doReturn(true).when(licenseServiceSpy).sendEmailToAccountAdmin(any(), anyString());
    licenseServiceSpy.handleTrialAccountExpiration(account, currentTime - 90 * oneDayTimeDiff);
    Account accountFromDB = accountService.get(account.getUuid());
    assertThat(accountFromDB.getLicenseInfo().getAccountStatus()).isEqualTo(AccountStatus.MARKED_FOR_DELETION);
  }

  @Test
  @Owner(developers = VOJIN)
  @Category(UnitTests.class)
  public void shouldNotUpdateAccountStatusAfterNinetyDaysOfExpiry() throws InterruptedException {
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.TRIAL);
    licenseInfo.setAccountStatus(AccountStatus.EXPIRED);

    long currentTime = System.currentTimeMillis();
    List<Long> licenseExpiryRemindersSentAt = new ArrayList<>();
    licenseExpiryRemindersSentAt.add(currentTime - 60 * oneDayTimeDiff);
    licenseExpiryRemindersSentAt.add(currentTime - oneDayTimeDiff);

    Account account = accountService.save(anAccount()
                                              .withCompanyName(HARNESS_NAME)
                                              .withAccountName(HARNESS_NAME)
                                              .withAccountKey(ACCOUNT_KEY)
                                              .withLicenseInfo(licenseInfo)
                                              .withLicenseExpiryRemindersSentAt(licenseExpiryRemindersSentAt)
                                              .build(),
        false);
    LicenseServiceImpl licenseServiceSpy = spy(licenseService);
    when(mainConfiguration.getNumberOfRemindersBeforeAccountDeletion()).thenReturn(3);
    doReturn(true).when(licenseServiceSpy).sendEmailToAccountAdmin(any(), anyString());
    licenseServiceSpy.handleTrialAccountExpiration(account, currentTime - 90 * oneDayTimeDiff);
    Account accountFromDB = accountService.get(account.getUuid());
    assertThat(accountFromDB.getLicenseInfo().getAccountStatus()).isNotEqualTo(AccountStatus.MARKED_FOR_DELETION);
    assertThat(accountFromDB.getLastLicenseExpiryReminderSentAt())
        .isBetween(System.currentTimeMillis() - 10000, System.currentTimeMillis() + 10000);
    assertThat(accountFromDB.getLicenseExpiryRemindersSentAt().size()).isEqualTo(3);
  }

  private String getEncryptedString(LicenseInfo licenseInfo) {
    return LicenseUtils.generateLicense(licenseInfo);
  }
}
