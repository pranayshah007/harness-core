/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.processingcontrollers;

import static io.harness.rule.OwnerRule.MEHUL;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.LicenseInfo;
import software.wings.beans.account.AccountStatus;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NotificationProcessingControllerTest extends WingsBaseTest {
  @Inject private NotificationProcessingController notificationProcessingController;
  @Inject private HPersistence persistence;

  private static final long ONE_DAY_TIME_DIFF = 86400000L;
  private static final long ONE_DAY_EXPIRY = System.currentTimeMillis() + ONE_DAY_TIME_DIFF;
  private static final long FOUR_DAYS_BEFORE_CURRENT_TIME = System.currentTimeMillis() - 4 * ONE_DAY_TIME_DIFF;

  private LicenseInfo getLicenseInfo(String accountStatus, long expiryTime) {
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountStatus(accountStatus);
    licenseInfo.setAccountType(AccountType.PAID);
    licenseInfo.setLicenseUnits(100);
    licenseInfo.setExpiryTime(expiryTime);
    return licenseInfo;
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void shouldProcessActiveAccount() {
    Account account =
        anAccount().withUuid(ACCOUNT_ID).withLicenseInfo(getLicenseInfo(AccountStatus.ACTIVE, ONE_DAY_EXPIRY)).build();
    persistence.save(account);
    assertThat(notificationProcessingController.canProcessAccount(ACCOUNT_ID)).isTrue();
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void shouldNotProcessInactiveAccount() {
    Account account = anAccount()
                          .withUuid(ACCOUNT_ID)
                          .withLicenseInfo(getLicenseInfo(AccountStatus.INACTIVE, ONE_DAY_EXPIRY))
                          .build();
    persistence.save(account);
    assertThat(notificationProcessingController.canProcessAccount(ACCOUNT_ID)).isFalse();
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void shouldNotProcessDeletedAccount() {
    Account account =
        anAccount().withUuid(ACCOUNT_ID).withLicenseInfo(getLicenseInfo(AccountStatus.DELETED, ONE_DAY_EXPIRY)).build();
    persistence.save(account);
    assertThat(notificationProcessingController.canProcessAccount(ACCOUNT_ID)).isFalse();
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void shouldNotProcessExpiredAccount() {
    Account account = anAccount()
                          .withUuid(ACCOUNT_ID)
                          .withLicenseInfo(getLicenseInfo(AccountStatus.INACTIVE, ONE_DAY_EXPIRY))
                          .build();
    persistence.save(account);
    assertThat(notificationProcessingController.canProcessAccount(ACCOUNT_ID)).isFalse();
  }
}
