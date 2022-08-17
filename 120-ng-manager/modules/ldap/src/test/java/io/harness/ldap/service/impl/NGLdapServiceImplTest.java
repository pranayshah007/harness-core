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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import io.harness.delegate.beans.ldap.NGLdapGroupSyncTaskResponse;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ldap.scheduler.NGLdapGroupSyncHelper;
import io.harness.ng.authenticationsettings.remote.AuthSettingsManagerClient;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.beans.dto.LdapSettings;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.beans.sso.LdapUserResponse;
import software.wings.service.impl.ldap.LdapDelegateException;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
  public static final String INVALID_CREDENTIALS = "Invalid Credentials";
  public static final String UNKNOWN_RESPONSE_FROM_DELEGATE = "Unknown Response from delegate";
  TaskSetupAbstractionHelper taskSetupAbstractionHelper = mock(TaskSetupAbstractionHelper.class);
  DelegateGrpcClientWrapper delegateGrpcClientWrapper = mock(DelegateGrpcClientWrapper.class);
  AuthSettingsManagerClient managerClient = mock(AuthSettingsManagerClient.class);
  NGLdapGroupSyncHelper groupSyncHelper = mock(NGLdapGroupSyncHelper.class);
  UserGroupService userGroupService = mock(UserGroupService.class);

  @Spy @InjectMocks private NGLdapServiceImpl ngLdapService;

  private LdapSettingsWithEncryptedDataDetail ldapSettingsWithEncryptedDataDetail;
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final String LDAP_SETTINGS_ID = "SSO_ID";

  @Before
  public void setup() {
    initMocks(this);
    ldapSettingsWithEncryptedDataDetail = LdapSettingsWithEncryptedDataDetail.builder()
                                              .ldapSettings(LdapSettings.builder().uuid(LDAP_SETTINGS_ID).build())
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
    doReturn(request).when(managerClient).getLdapSettingsWithEncryptedDataDetails(ACCOUNT_ID, null);
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
    doReturn(null).when(managerClient).getLdapSettingsWithEncryptedDataDetails(ACCOUNT_ID, null);
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
    doReturn(request).when(managerClient).getLdapSettingsWithEncryptedDataDetails(ACCOUNT_ID, null);
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

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testLdapUserQuerySuccessfulAndUnsuccessful() {
    final String accountId = "testAccountId";
    LdapSettings ldapSettings = LdapSettings.builder().accountId(accountId).build();
    LdapTestResponse successfulTestResponse =
        LdapTestResponse.builder()
            .status(SUCCESS)
            .message("Configuration looks good. Server returned non-zero number of records")
            .build();

    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(successfulTestResponse).build());

    LdapTestResponse ldapTestResponse = ngLdapService.validateLdapUserSettings(accountId, null, null, ldapSettings);

    assertNotNull(ldapTestResponse);
    assertEquals(successfulTestResponse.getStatus(), ldapTestResponse.getStatus());

    LdapTestResponse unsuccessfulTestResponse = LdapTestResponse.builder().status(FAILURE).message(null).build();

    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(unsuccessfulTestResponse).build());

    ldapTestResponse = ngLdapService.validateLdapUserSettings(accountId, null, null, ldapSettings);

    assertNotNull(ldapTestResponse);
    assertEquals(unsuccessfulTestResponse.getStatus(), ldapTestResponse.getStatus());
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testLdapGroupQuerySuccessfulAndUnsuccessful() {
    final String accountId = "testAccountId";
    LdapSettings ldapSettings = LdapSettings.builder().accountId(accountId).build();
    LdapTestResponse successfulTestResponse =
        LdapTestResponse.builder()
            .status(SUCCESS)
            .message("Configuration looks good. Server returned non-zero number of records")
            .build();

    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(successfulTestResponse).build());

    LdapTestResponse ldapTestResponse = ngLdapService.validateLdapGroupSettings(accountId, null, null, ldapSettings);

    assertNotNull(ldapTestResponse);
    assertEquals(successfulTestResponse.getStatus(), ldapTestResponse.getStatus());

    LdapTestResponse unsuccessfulTestResponse =
        LdapTestResponse.builder()
            .status(FAILURE)
            .message("Please check configuration. Server returned zero records for the configuration.")
            .build();

    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(unsuccessfulTestResponse).build());

    ldapTestResponse = ngLdapService.validateLdapGroupSettings(accountId, null, null, ldapSettings);

    assertNotNull(ldapTestResponse);
    assertEquals(unsuccessfulTestResponse.getStatus(), ldapTestResponse.getStatus());
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testLdapUserQueryException() {
    final String accountId = "testAccountId";
    LdapSettings ldapSettings = LdapSettings.builder().accountId(accountId).build();

    when(delegateGrpcClientWrapper.executeSyncTask(any())).thenReturn(buildErrorNotifyResponseData());

    LdapTestResponse ldapTestResponse = null;
    try {
      ldapTestResponse = ngLdapService.validateLdapUserSettings(accountId, null, null, ldapSettings);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (Exception e) {
      assertNull(ldapTestResponse);
      assertThat(e).isInstanceOf(LdapDelegateException.class);
      assertEquals(e.getMessage(), UNKNOWN_RESPONSE_FROM_DELEGATE);
    }
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testLdapGroupQueryException() {
    final String accountId = "testAccountId";
    LdapSettings ldapSettings = LdapSettings.builder().accountId(accountId).build();

    when(delegateGrpcClientWrapper.executeSyncTask(any())).thenReturn(buildErrorNotifyResponseData());

    LdapTestResponse ldapTestResponse = null;
    try {
      ldapTestResponse = ngLdapService.validateLdapGroupSettings(accountId, null, null, ldapSettings);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (Exception e) {
      assertNull(ldapTestResponse);
      assertThat(e).isInstanceOf(LdapDelegateException.class);
      assertEquals(e.getMessage(), UNKNOWN_RESPONSE_FROM_DELEGATE);
    }
  }
  private ErrorNotifyResponseData buildErrorNotifyResponseData() {
    return ErrorNotifyResponseData.builder().errorMessage(INVALID_CREDENTIALS).build();
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSyncLdapGroups() throws IOException {
    int totalMembers = 1;
    Call<RestResponse<LdapSettingsWithEncryptedDataDetail>> request = mock(Call.class);
    RestResponse<LdapSettingsWithEncryptedDataDetail> mockResponse =
        new RestResponse<>(ldapSettingsWithEncryptedDataDetail);
    doReturn(request).when(managerClient).getLdapSettingsWithEncryptedDataDetails(ACCOUNT_ID, null);
    doReturn(Response.success(mockResponse)).when(request).execute();
    final String groupDn = "testGrpDn";
    final String testUserEmail = "test123@hn.io";
    final String testUserName = "test 123";
    LdapUserResponse usrResponse = LdapUserResponse.builder().email(testUserEmail).name(testUserName).build();
    LdapGroupResponse response = LdapGroupResponse.builder()
                                     .name("testLdapGroup")
                                     .description("desc")
                                     .dn(groupDn)
                                     .totalMembers(totalMembers)
                                     .users(Collections.singletonList(usrResponse))
                                     .build();
    UserGroup ug1 = UserGroup.builder()
                        .identifier("UG1")
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_ID)
                        .projectIdentifier(PROJECT_ID)
                        .ssoGroupId(groupDn)
                        .users(Collections.singletonList(testUserEmail))
                        .build();

    Map<UserGroup, LdapGroupResponse> usrGroupToLdapGroupMap = new HashMap<>();
    usrGroupToLdapGroupMap.put(ug1, response);

    doNothing().when(groupSyncHelper).reconcileAllUserGroups(any(), anyString(), anyString());
    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(NGLdapGroupSyncTaskResponse.builder().ldapGroupsResponse(response).build());
    when(userGroupService.getUserGroupsBySsoId(anyString(), anyString())).thenReturn(Collections.singletonList(ug1));
    ngLdapService.syncUserGroupsJob(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    verify(managerClient, times(1)).getLdapSettingsWithEncryptedDataDetails(ACCOUNT_ID, null);
    verify(groupSyncHelper, times(1)).reconcileAllUserGroups(usrGroupToLdapGroupMap, LDAP_SETTINGS_ID, ACCOUNT_ID);
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTask(any());
  }
}
