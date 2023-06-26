/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.HANTANG;
import static io.harness.rule.OwnerRule.UTSAV;
import static io.harness.rule.OwnerRule.VIKAS;
import static io.harness.rule.OwnerRule.ZHUO;

import static software.wings.beans.Account.Builder.anAccount;

import static java.lang.String.format;
import static javax.ws.rs.client.Entity.entity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.license.CeLicenseInfo;
import io.harness.datahandler.services.AdminAccountService;
import io.harness.datahandler.services.AdminUserService;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.CDModuleLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.remote.admin.AdminLicenseHttpClient;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.beans.Account;
import software.wings.beans.CeLicenseUpdateInfo;
import software.wings.service.intfc.DelegateService;
import software.wings.utils.ResourceTestRule;

import java.io.IOException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
@TargetModule(HarnessModule._955_ACCOUNT_MGMT)
public class AdminAccountResourceTest extends CategoryTest {
  private String accountId = "ACCOUNT_ID";
  private Account account;
  private ModuleLicenseDTO moduleLicenseDTO;

  private static AdminAccountService adminAccountService = mock(AdminAccountService.class);
  private static AdminUserService adminUserService = mock(AdminUserService.class);
  private static AccessControlAdminClient accessControlAdminClient = mock(AccessControlAdminClient.class);
  private static DelegateService delegateService = mock(DelegateService.class);
  private static AdminLicenseHttpClient adminLicenseHttpClient = mock(AdminLicenseHttpClient.class);

  @ClassRule
  public static ResourceTestRule RESOURCES =
      ResourceTestRule.builder()
          .instance(new AdminAccountResource(
              adminAccountService, adminUserService, accessControlAdminClient, delegateService, adminLicenseHttpClient))
          .build();

  @Before
  public void setUp() throws IOException {
    account = anAccount()
                  .withUuid(accountId)
                  .withAccountKey("ACCOUNT_KEY")
                  .withCloudCostEnabled(true)
                  .withCeK8sEventCollectionEnabled(true)
                  .build();

    moduleLicenseDTO = CDModuleLicenseDTO.builder().moduleType(ModuleType.CD).build();

    Call<ResponseDTO<ModuleLicenseDTO>> adminLicenseCreateAndUpdateCall = mock(Call.class);
    when(adminLicenseCreateAndUpdateCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(moduleLicenseDTO)));

    Call<ResponseDTO<Void>> adminLicenseDeleteCall = mock(Call.class);
    when(adminLicenseDeleteCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse()));

    Call<ResponseDTO<AccountLicenseDTO>> adminLicenseQueryCall = mock(Call.class);
    AccountLicenseDTO accountLicenseDTO = AccountLicenseDTO.builder().build();
    when(adminLicenseQueryCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(accountLicenseDTO)));

    when(adminLicenseHttpClient.createAccountLicense(any(), any())).thenReturn(adminLicenseCreateAndUpdateCall);
    when(adminLicenseHttpClient.updateModuleLicense(any(), any(), any())).thenReturn(adminLicenseCreateAndUpdateCall);
    when(adminLicenseHttpClient.deleteModuleLicense(any(), any())).thenReturn(adminLicenseDeleteCall);
    when(adminLicenseHttpClient.getAccountLicense(any())).thenReturn(adminLicenseQueryCall);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldUpdateCeLicense() {
    CeLicenseInfo ceLicenseInfo = CeLicenseInfo.builder().build();
    CeLicenseUpdateInfo ceLicenseUpdateInfo = CeLicenseUpdateInfo.builder().ceLicenseInfo(ceLicenseInfo).build();
    RESOURCES.client()
        .target(format("/admin/accounts/%s/license/continuous-efficiency", accountId))
        .request()
        .put(
            entity(ceLicenseUpdateInfo, MediaType.APPLICATION_JSON), new GenericType<RestResponse<CeLicenseInfo>>() {});
    verify(adminAccountService).updateCeLicense(eq(accountId), eq(ceLicenseInfo));
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testUpdatePovFlag() {
    RESOURCES.client()
        .target(format("/admin/accounts/%s/pov?isPov=%b", accountId, true))
        .request()
        .put(Entity.json(""), new GenericType<RestResponse<Boolean>>() {});
    verify(adminAccountService).updatePovFlag(eq(accountId), eq(true));
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  @Ignore("Platform Team to fix it")
  public void testCreateAccount() {
    RESOURCES.client()
        .target("/admin/accounts/")
        .request()
        .post(entity(account, MediaType.APPLICATION_JSON), new GenericType<RestResponse<Boolean>>() {});
    verify(adminAccountService).createAccount(eq(account), any());
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testCreateNgLicense() {
    RESOURCES.client()
        .target("/admin/accounts/ACCOUNT_ID/ng/license")
        .request()
        .post(
            entity(moduleLicenseDTO, MediaType.APPLICATION_JSON), new GenericType<RestResponse<ModuleLicenseDTO>>() {});
    verify(adminLicenseHttpClient).createAccountLicense(accountId, moduleLicenseDTO);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testUpdateNgLicense() {
    RESOURCES.client()
        .target("/admin/accounts/ACCOUNT_ID/ng/license")
        .request()
        .put(
            entity(moduleLicenseDTO, MediaType.APPLICATION_JSON), new GenericType<RestResponse<ModuleLicenseDTO>>() {});
    verify(adminLicenseHttpClient).updateModuleLicense(any(), eq(accountId), eq(moduleLicenseDTO));
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetNgAccountLicenses() {
    RESOURCES.client().target("/admin/accounts/ACCOUNT_ID/ng/license").request().get();
    verify(adminLicenseHttpClient).getAccountLicense(anyString());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testDeleteNgLicense() {
    RESOURCES.client()
        .target("/admin/accounts/identifier/ng/license")
        .queryParam("accountIdentifier", accountId)
        .request()
        .delete(new GenericType<RestResponse<Void>>() {});
    verify(adminLicenseHttpClient).deleteModuleLicense(any(), any());
  }
}
