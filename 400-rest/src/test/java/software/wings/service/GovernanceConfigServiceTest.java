/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.rule.OwnerRule.RAMA;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.utils.WingsTestConstants.USER_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.governance.TimeRangeBasedFreezeConfig;
import io.harness.governance.WeeklyFreezeConfig;
import io.harness.rule.Owner;

import software.wings.beans.Account;
import software.wings.beans.Event;
import software.wings.beans.LicenseInfo;
import software.wings.beans.User;
import software.wings.beans.governance.GovernanceConfig;
import software.wings.features.GovernanceFeature;
import software.wings.features.api.PremiumFeature;
import software.wings.integration.IntegrationTestBase;
import software.wings.licensing.LicenseService;
import software.wings.resources.stats.model.TimeRange;
import software.wings.resources.stats.model.WeeklyRange;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.compliance.GovernanceConfigService;
import software.wings.utils.WingsTestConstants;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 *
 * @author rktummala
 */
@TargetModule(HarnessModule._960_API_SERVICES)
@OwnedBy(HarnessTeam.CDC)
public class GovernanceConfigServiceTest extends IntegrationTestBase {
  @Inject private AccountService accountService;
  @Inject private LicenseService licenseService;
  @Inject @InjectMocks private GovernanceConfigService governanceConfigService;
  @Inject @Named(GovernanceFeature.FEATURE_NAME) private PremiumFeature governanceFeature;
  @Mock private AuditServiceHelper auditServiceHelper;

  private String accountId = "some-account-uuid-" + RandomStringUtils.randomAlphanumeric(5);

  /**
   * Sets mocks.
   */
  @Override
  @Before
  public void setUp() {
    Account account = anAccount()
                          .withUuid(accountId)
                          .withAccountName(WingsTestConstants.ACCOUNT_NAME)
                          .withCompanyName(WingsTestConstants.COMPANY_NAME)
                          .withLicenseInfo(getLicenseInfo())
                          .build();
    accountService.save(account, false);
    accountId = account.getUuid();
    setUserRequestContext();
  }

  private void setUserRequestContext() {
    User user = User.Builder.anUser().name(USER_NAME).uuid(USER_ID).build();
    user.setUserRequestContext(UserRequestContext.builder().accountId(accountId).build());
    UserThreadLocal.set(user);
  }

  /**
   * Should save and read.
   *
   */
  @Test
  @Owner(developers = RAMA)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testUpdateAndRead() {
    GovernanceConfig defaultConfig = GovernanceConfig.builder().accountId(accountId).deploymentFreeze(false).build();
    GovernanceConfig governanceConfig = governanceConfigService.get(accountId);
    compare(defaultConfig, governanceConfig);

    GovernanceConfig inputConfig = GovernanceConfig.builder().accountId(accountId).deploymentFreeze(true).build();
    GovernanceConfig savedGovernanceConfig = governanceConfigService.upsert(accountId, inputConfig);
    compare(inputConfig, savedGovernanceConfig);

    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(
            eq(accountId), eq(governanceConfig), eq(savedGovernanceConfig), eq(Event.Type.ENABLE));

    savedGovernanceConfig = governanceConfigService.get(accountId);
    compare(inputConfig, savedGovernanceConfig);

    TimeRange range = new TimeRange(100L, 200L, "Asia/Kolkata", false, null, null, null, false);
    WeeklyRange weeklyRange = new WeeklyRange(null, "Tuesday", "7:00 PM", "Monday", "5:00 AM", "Asia/Kolkata");
    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range, null, null, false, null, null, null, "uuid", null);
    WeeklyFreezeConfig weeklyFreezeConfig =
        new WeeklyFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            weeklyRange, null, null, false, null, null, null, "uuid", null);

    inputConfig = GovernanceConfig.builder()
                      .accountId(accountId)
                      .deploymentFreeze(true)
                      .timeRangeBasedFreezeConfigs(Collections.singletonList(timeRangeBasedFreezeConfig))
                      .weeklyFreezeConfigs(Collections.singletonList(weeklyFreezeConfig))
                      .build();

    GovernanceConfig oldGovernanceConfig = savedGovernanceConfig;
    savedGovernanceConfig = governanceConfigService.upsert(accountId, inputConfig);
    compare(inputConfig, savedGovernanceConfig);

    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(
            eq(accountId), eq(oldGovernanceConfig), eq(savedGovernanceConfig), eq(Event.Type.UPDATE));

    inputConfig.setDeploymentFreeze(false);

    oldGovernanceConfig = savedGovernanceConfig;
    savedGovernanceConfig = governanceConfigService.upsert(accountId, inputConfig);
    compare(inputConfig, savedGovernanceConfig);

    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(
            eq(accountId), eq(oldGovernanceConfig), eq(savedGovernanceConfig), eq(Event.Type.DISABLE));

    savedGovernanceConfig = governanceConfigService.get(accountId);
    compare(inputConfig, savedGovernanceConfig);

    inputConfig = GovernanceConfig.builder().accountId(accountId).deploymentFreeze(true).build();

    for (String restrictedAccountType : governanceFeature.getRestrictedAccountTypes()) {
      LicenseInfo newLicenseInfo = getLicenseInfo();
      newLicenseInfo.setAccountType(restrictedAccountType);
      licenseService.updateAccountLicense(accountId, newLicenseInfo);
      try {
        governanceConfigService.upsert(accountId, inputConfig);
        fail("Saved governance config");
      } catch (WingsException e) {
        assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
      }
    }
  }

  private void compare(GovernanceConfig lhs, GovernanceConfig rhs) {
    assertThat(rhs.getAccountId()).isEqualTo(lhs.getAccountId());
    assertThat(rhs.isDeploymentFreeze()).isEqualTo(lhs.isDeploymentFreeze());
  }
}
