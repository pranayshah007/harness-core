/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution;

import static io.harness.rule.OwnerRule.HEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.account.AccountClient;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.validation.CIAccountValidationServiceImpl;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.summary.CILicenseSummaryDTO;
import io.harness.ng.core.account.AccountTrustLevel;
import io.harness.ng.core.user.UserInfo;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
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
  @Mock CILicenseService ciLicenseService;
  static final String accountId = "ACCOUNT_ID";
  @Before
  public void setup() {
    initMocks(this);
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
}
