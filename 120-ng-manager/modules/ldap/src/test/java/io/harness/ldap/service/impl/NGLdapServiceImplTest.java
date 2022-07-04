/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.service.impl;

import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.SHASHANK;

import static software.wings.beans.sso.LdapTestResponse.Status.FAILURE;
import static software.wings.beans.sso.LdapTestResponse.Status.SUCCESS;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ldap.LdapSettingsWithEncryptedDataDetail;
import io.harness.delegate.beans.ldap.NGLdapDelegateTaskResponse;
import io.harness.delegate.beans.ldap.NGLdapGroupSearchTaskResponse;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.authenticationsettings.remote.AuthSettingsManagerClient;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.beans.dto.LdapSettings;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.service.impl.ldap.LdapDelegateException;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.PL)
@RunWith(MockitoJUnitRunner.class)
public class NGLdapServiceImplTest extends CategoryTest {
  public static final String INVAILD_CREDENTIALS = "Invaild Credentials";
  public static final String UNKNOWN_RESPONSE_FROM_DELEGATE = "Unknown Response from delegate";
  TaskSetupAbstractionHelper taskSetupAbstractionHelper = mock(TaskSetupAbstractionHelper.class);
  DelegateGrpcClientWrapper delegateGrpcClientWrapper = mock(DelegateGrpcClientWrapper.class);
  AuthSettingsManagerClient managerClient = mock(AuthSettingsManagerClient.class);

  @Spy @InjectMocks private NGLdapServiceImpl ngLdapService;

  private LdapSettingsWithEncryptedDataDetail ldapSettingsWithEncryptedDataDetail;
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";

  @Before
  public void setup() {
    initMocks(this);
    ldapSettingsWithEncryptedDataDetail = LdapSettingsWithEncryptedDataDetail.builder()
                                              .ldapSettings(LdapSettings.builder().build())
                                              .encryptedDataDetail(EncryptedDataDetail.builder().build())
                                              .build();
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testLdapConnectionSuccessfulAndUnsuccessful() {
    final String accountId = "testAccountId";
    LdapSettings ldapSettings = LdapSettings.builder().accountId(accountId).build();
    LdapTestResponse successfulTestResponse =
        LdapTestResponse.builder().status(SUCCESS).message("Connection Successful").build();

    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(successfulTestResponse).build());

    LdapTestResponse ldapTestResponse =
        ngLdapService.validateLdapConnectionSettings(accountId, null, null, ldapSettings);

    assertNotNull(ldapTestResponse);
    assertEquals(successfulTestResponse.getStatus(), ldapTestResponse.getStatus());

    LdapTestResponse unsuccessfulTestResponse =
        LdapTestResponse.builder().status(FAILURE).message("Invalid Credential").build();

    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(unsuccessfulTestResponse).build());

    ldapTestResponse = ngLdapService.validateLdapConnectionSettings(accountId, null, null, ldapSettings);

    assertNotNull(ldapTestResponse);
    assertEquals(unsuccessfulTestResponse.getStatus(), ldapTestResponse.getStatus());
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testLdapConnectionException() {
    final String accountId = "testAccountId";
    LdapSettings ldapSettings = LdapSettings.builder().accountId(accountId).build();

    when(delegateGrpcClientWrapper.executeSyncTask(any())).thenReturn(buildErrorNotifyResponseData());

    LdapTestResponse ldapTestResponse = null;
    try {
      ldapTestResponse = ngLdapService.validateLdapConnectionSettings(accountId, null, null, ldapSettings);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (Exception e) {
      assertNull(ldapTestResponse);
      assertThat(e).isInstanceOf(LdapDelegateException.class);
      assertEquals(e.getMessage(), UNKNOWN_RESPONSE_FROM_DELEGATE);
    }
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSearchLdapGroups() throws IOException {
    int totalMembers = 4;
    Call<RestResponse<LdapSettingsWithEncryptedDataDetail>> request = mock(Call.class);
    RestResponse<LdapSettingsWithEncryptedDataDetail> mockResponse =
        new RestResponse<>(ldapSettingsWithEncryptedDataDetail);
    doReturn(request).when(managerClient).getLdapSettingsWithEncryptedDataDetails(ACCOUNT_ID);
    doReturn(Response.success(mockResponse)).when(request).execute();
    final String groupNameQuery = "grpName";
    LdapGroupResponse response = LdapGroupResponse.builder()
                                     .name(groupNameQuery)
                                     .description("desc")
                                     .dn("uid=ldap_user1,ou=Users,dc=jumpcloud,dc=com")
                                     .totalMembers(totalMembers)
                                     .build();
    Collection<LdapGroupResponse> matchedGroups = Collections.singletonList(response);
    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(NGLdapGroupSearchTaskResponse.builder().ldapListGroupsResponses(matchedGroups).build());
    Collection<LdapGroupResponse> resultUserGroups =
        ngLdapService.searchLdapGroupsByName(ACCOUNT_ID, ORG_ID, PROJECT_ID, "TestLdapID", groupNameQuery);
    assertNotNull(resultUserGroups);
    assertThat(resultUserGroups.size()).isEqualTo(1);
    assertThat(resultUserGroups.iterator().next().getTotalMembers()).isEqualTo(totalMembers);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSearchLdapGroupsEmptyListWhenLdapSettingsNotFound() {
    final String groupNameQuery = "grpName";
    doReturn(null).when(managerClient).getLdapSettingsWithEncryptedDataDetails(ACCOUNT_ID);
    Collection<LdapGroupResponse> resultUserGroups = null;
    try {
      resultUserGroups =
          ngLdapService.searchLdapGroupsByName(ACCOUNT_ID, ORG_ID, PROJECT_ID, "TestLdapID", groupNameQuery);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (Exception exc) {
      assertNull(resultUserGroups);
      assertThat(exc).isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSearchLdapGroupsDelegateResponseExceptionCase() throws IOException {
    Call<RestResponse<LdapSettingsWithEncryptedDataDetail>> request = mock(Call.class);
    RestResponse<LdapSettingsWithEncryptedDataDetail> mockResponse =
        new RestResponse<>(ldapSettingsWithEncryptedDataDetail);
    doReturn(request).when(managerClient).getLdapSettingsWithEncryptedDataDetails(ACCOUNT_ID);
    doReturn(Response.success(mockResponse)).when(request).execute();
    final String groupNameQuery = "grpName";
    when(delegateGrpcClientWrapper.executeSyncTask(any())).thenReturn(buildErrorNotifyResponseData());
    Collection<LdapGroupResponse> resultUserGroups = null;

    try {
      resultUserGroups =
          ngLdapService.searchLdapGroupsByName(ACCOUNT_ID, ORG_ID, PROJECT_ID, "TestLdapID", groupNameQuery);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (Exception exc) {
      assertNull(resultUserGroups);
      assertThat(exc).isInstanceOf(LdapDelegateException.class);
      assertEquals(exc.getMessage(), UNKNOWN_RESPONSE_FROM_DELEGATE);
    }
  }

  private ErrorNotifyResponseData buildErrorNotifyResponseData() {
    return ErrorNotifyResponseData.builder().errorMessage(INVAILD_CREDENTIALS).build();
  }
}
