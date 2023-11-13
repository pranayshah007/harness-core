/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.validation;

import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.HEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.account.AccountClient;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.ExecutionLimitSpec;
import io.harness.ci.config.ExecutionLimits;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.creditcard.remote.CreditCardClient;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.summary.CILicenseSummaryDTO;
import io.harness.ng.core.account.AccountTrustLevel;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.user.UserInfo;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.subscription.responses.AccountCreditCardValidationResponse;
import io.harness.user.remote.UserClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

public class CIAccountValidationServiceTest extends CIExecutionTestBase {
  @InjectMocks CIAccountValidationServiceImpl accountValidationService;
  @Mock UserClient userClient;
  @Mock AccountClient accountClient;
  @Mock CreditCardClient creditCardClient;
  @Mock CILicenseService ciLicenseService;
  @Mock CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock CIMiningPatternJob ciMiningPatternJob;
  @Mock ExecutionLimits executionLimits;
  static final String accountId = "ACCOUNT_ID";
  @Before
  public void setup() {
    initMocks(this);
    when(executionLimits.getFreeBasicUser())
        .thenReturn(ExecutionLimitSpec.builder().dailyMaxBuildsCount(25).monthlyMaxCreditsCount(2000).build());
    when(executionLimits.getFreeNewUser())
        .thenReturn(ExecutionLimitSpec.builder().dailyMaxBuildsCount(0).monthlyMaxCreditsCount(0).build());
  }

  @Test
  @Owner(developers = HEN)
  @Category(UnitTests.class)
  public void testAccountValidationForValidDomain() throws IOException {
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);

    UserInfo userInfo = UserInfo.builder().email("test@harness.com").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);

    when(userEmailsCall.execute()).thenReturn(Response.success(new RestResponse<>(userInfos)));
    when(accountTrustLevelCall.execute())
        .thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.BASIC_USER)));

    when(userClient.listUsersEmails(any(String.class))).thenReturn(userEmailsCall);
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);

    assertThat(accountValidationService.isAccountValidForExecution(accountId)).isTrue();
  }

  @Test
  @Owner(developers = HEN)
  @Category(UnitTests.class)
  public void testAccountValidationForInvalidDomain() throws IOException {
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);

    UserInfo userInfo = UserInfo.builder().email("test@xyz.com").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);

    when(accountTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.NEW_USER)));
    when(ciLicenseService.getLicenseSummary(any(String.class)))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.TRIAL).edition(Edition.FREE).build());
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);

    boolean isValid = false;
    try {
      isValid = accountValidationService.isAccountValidForExecution(accountId);
    } catch (Exception e) {
    }
    assertThat(isValid).isTrue();
  }

  @Test
  @Owner(developers = HEN)
  @Category(UnitTests.class)
  public void testAccountValidationForInvalidDomainWithPayingStatus() throws IOException {
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);

    UserInfo userInfo = UserInfo.builder().email("test@xyz.com").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);

    when(accountTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.NEW_USER)));
    when(ciLicenseService.getLicenseSummary(any(String.class)))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);

    boolean isValid = false;
    try {
      isValid = accountValidationService.isAccountValidForExecution(accountId);
    } catch (Exception e) {
    }
    assertThat(isValid).isTrue();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetTrustLevel() throws IOException {
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);

    UserInfo userInfo = UserInfo.builder().email("test@xyz.com").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);

    when(accountTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.NEW_USER)));
    when(userEmailsCall.execute()).thenReturn(Response.success(new RestResponse<>(userInfos)));
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);
    when(userClient.listUsersEmails(any(String.class))).thenReturn(userEmailsCall);

    Integer trustLevel = accountValidationService.getTrustLevel(accountId);

    assertThat(trustLevel).isEqualTo(0);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetMaxBuildPerDayOldAccount() throws IOException {
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);
    Call<RestResponse<AccountDTO>> accountDTOCall = mock(Call.class);

    UserInfo userInfo = UserInfo.builder().email("test@xyz.com").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);
    AccountDTO dto = AccountDTO.builder().createdAt(CIAccountValidationServiceImpl.APPLY_DAY - 1000).build();

    when(accountTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.NEW_USER)));
    when(userEmailsCall.execute()).thenReturn(Response.success(new RestResponse<>(userInfos)));
    when(accountDTOCall.execute()).thenReturn(Response.success(new RestResponse<>(dto)));

    when(ciLicenseService.getLicenseSummary(any(String.class)))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);
    when(accountClient.getAccountDTO(any(String.class))).thenReturn(accountDTOCall);
    when(userClient.listUsersEmails(any(String.class))).thenReturn(userEmailsCall);

    long buildsCount = accountValidationService.getMaxBuildPerDay(accountId);

    assertThat(buildsCount).isEqualTo(25);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetMaxBuildPerDayNewAccountValidCC() throws IOException {
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);
    Call<RestResponse<AccountDTO>> accountDTOCall = mock(Call.class);
    Call<ResponseDTO<AccountCreditCardValidationResponse>> creditCardCall = mock(Call.class);

    UserInfo userInfo = UserInfo.builder().email("test@xyz.com").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);
    AccountDTO dto = AccountDTO.builder().createdAt(CIAccountValidationServiceImpl.APPLY_DAY + 1000).build();
    AccountCreditCardValidationResponse creditCardValidationResponse =
        AccountCreditCardValidationResponse.builder().hasAtleastOneValidCreditCard(true).build();

    when(accountTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.NEW_USER)));
    when(userEmailsCall.execute()).thenReturn(Response.success(new RestResponse<>(userInfos)));
    when(accountDTOCall.execute()).thenReturn(Response.success(new RestResponse<>(dto)));
    when(creditCardCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(creditCardValidationResponse)));

    when(ciLicenseService.getLicenseSummary(any(String.class)))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);
    when(accountClient.getAccountDTO(any(String.class))).thenReturn(accountDTOCall);
    when(userClient.listUsersEmails(any(String.class))).thenReturn(userEmailsCall);
    when(creditCardClient.validateCreditCard(any(String.class))).thenReturn(creditCardCall);

    long buildsCount = accountValidationService.getMaxBuildPerDay(accountId);

    assertThat(buildsCount).isEqualTo(25);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetMaxCreditsPerMonthNewAccountValidCC() throws IOException {
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);
    Call<RestResponse<AccountDTO>> accountDTOCall = mock(Call.class);
    Call<ResponseDTO<AccountCreditCardValidationResponse>> creditCardCall = mock(Call.class);

    UserInfo userInfo = UserInfo.builder().email("test@xyz.com").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);
    AccountDTO dto = AccountDTO.builder().createdAt(CIAccountValidationServiceImpl.APPLY_DAY + 1000).build();
    AccountCreditCardValidationResponse creditCardValidationResponse =
        AccountCreditCardValidationResponse.builder().hasAtleastOneValidCreditCard(true).build();

    when(accountTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.NEW_USER)));
    when(userEmailsCall.execute()).thenReturn(Response.success(new RestResponse<>(userInfos)));
    when(accountDTOCall.execute()).thenReturn(Response.success(new RestResponse<>(dto)));
    when(creditCardCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(creditCardValidationResponse)));

    when(ciLicenseService.getLicenseSummary(any(String.class)))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);
    when(accountClient.getAccountDTO(any(String.class))).thenReturn(accountDTOCall);
    when(userClient.listUsersEmails(any(String.class))).thenReturn(userEmailsCall);
    when(creditCardClient.validateCreditCard(any(String.class))).thenReturn(creditCardCall);

    long creditsCount = accountValidationService.getMaxCreditsPerMonth(accountId);

    assertThat(creditsCount).isEqualTo(2000);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetMaxBuildPerDayNewAccountInvalidCC() throws IOException {
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);
    Call<RestResponse<AccountDTO>> accountDTOCall = mock(Call.class);
    Call<ResponseDTO<AccountCreditCardValidationResponse>> creditCardCall = mock(Call.class);

    UserInfo userInfo = UserInfo.builder().email("test@xyz.com").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);
    AccountDTO dto = AccountDTO.builder().createdAt(CIAccountValidationServiceImpl.APPLY_DAY + 1000).build();
    AccountCreditCardValidationResponse creditCardValidationResponse =
        AccountCreditCardValidationResponse.builder().hasAtleastOneValidCreditCard(false).build();

    when(accountTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.NEW_USER)));
    when(userEmailsCall.execute()).thenReturn(Response.success(new RestResponse<>(userInfos)));
    when(accountDTOCall.execute()).thenReturn(Response.success(new RestResponse<>(dto)));
    when(creditCardCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(creditCardValidationResponse)));

    when(ciLicenseService.getLicenseSummary(any(String.class)))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);
    when(accountClient.getAccountDTO(any(String.class))).thenReturn(accountDTOCall);
    when(userClient.listUsersEmails(any(String.class))).thenReturn(userEmailsCall);
    when(creditCardClient.validateCreditCard(any(String.class))).thenReturn(creditCardCall);

    long buildsCount = accountValidationService.getMaxBuildPerDay(accountId);

    assertThat(buildsCount).isEqualTo(0);
  }
}
