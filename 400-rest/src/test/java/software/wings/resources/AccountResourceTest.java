/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.rule.OwnerRule.HANTANG;

import static java.lang.String.format;
import static javax.ws.rs.client.Entity.entity;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.licensing.remote.admin.AdminLicenseHttpClient;
import io.harness.marketplace.gcp.GcpMarketPlaceApiHandler;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.scheduler.PersistentScheduler;
import io.harness.seeddata.SampleDataProviderService;
import io.harness.service.intfc.DelegateAuthService;

import software.wings.beans.Account;
import software.wings.features.api.FeatureService;
import software.wings.licensing.LicenseService;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.utils.AccountPermissionUtils;
import software.wings.utils.ResourceTestRule;

import com.google.inject.Provider;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AccountResourceTest extends CategoryTest {
  private String accountId = "ACCOUNT_ID";
  private Account account = Account.Builder.anAccount().build();

  private static AccountService accountService = mock(AccountService.class);
  private static UserService userService = mock(UserService.class);
  private static LicenseService licenseService = mock(LicenseService.class);
  private static Provider<LicenseService> licenseServiceProvider = (Provider<LicenseService>) mock(Provider.class);
  private static AccountPermissionUtils accountPermissionUtils = mock(AccountPermissionUtils.class);
  private static FeatureService featureService = mock(FeatureService.class);
  private static PersistentScheduler jobScheduler = mock(PersistentScheduler.class);
  private static GcpMarketPlaceApiHandler gcpMarketPlaceApiHandler = mock(GcpMarketPlaceApiHandler.class);
  private static SampleDataProviderService sampleDataProviderService = mock(SampleDataProviderService.class);
  private static HarnessUserGroupService harnessUserGroupService = mock(HarnessUserGroupService.class);
  private static AdminLicenseHttpClient adminLicenseHttpClient = mock(AdminLicenseHttpClient.class);
  private static Provider<SampleDataProviderService> sampleDataProviderServiceProvider =
      (Provider<SampleDataProviderService>) mock(Provider.class);

  @ClassRule
  public static ResourceTestRule RESOURCES =
      ResourceTestRule.builder()
          .instance(new AccountResource(accountService, userService, licenseServiceProvider, accountPermissionUtils,
              featureService, jobScheduler, gcpMarketPlaceApiHandler, sampleDataProviderServiceProvider,
              mock(AuthService.class), harnessUserGroupService, adminLicenseHttpClient,
              mock(DelegateAuthService.class)))
          .build();
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void setUp() {
    when(accountService.get(eq(accountId))).thenReturn(account);
    when(licenseServiceProvider.get()).thenReturn(licenseService);
    when(sampleDataProviderServiceProvider.get()).thenReturn(sampleDataProviderService);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldStartCeTrial() {
    RESOURCES.client()
        .target(format("/account/continuous-efficiency/%s/startTrial", accountId))
        .request()
        .post(entity(Entity.json(""), MediaType.APPLICATION_JSON), new GenericType<RestResponse<Boolean>>() {});
    verify(licenseService).startCeLimitedTrial(eq(accountId));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldCreateSampleApplication() {
    RESOURCES.client()
        .target(format("/account/createSampleApplication?accountId=%s", accountId))
        .request()
        .post(entity(Entity.json(""), MediaType.APPLICATION_JSON), new GenericType<RestResponse<Boolean>>() {});
    verify(sampleDataProviderService).createK8sV2SampleApp(eq(account));
  }
}
