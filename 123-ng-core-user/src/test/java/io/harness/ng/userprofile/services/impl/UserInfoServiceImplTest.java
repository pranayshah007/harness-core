/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.userprofile.services.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGE_USER_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.USER;
import static io.harness.rule.OwnerRule.NAMANG;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.user.remote.UserClient;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import retrofit2.Call;
import retrofit2.Response;

@Slf4j
@OwnedBy(PL)
@RunWith(PowerMockRunner.class)
@PrepareForTest({SourcePrincipalContextBuilder.class})
public class UserInfoServiceImplTest extends CategoryTest {
  private UserClient userClient;
  private NgUserService ngUserService;
  private AccessControlClient accessControlClient;
  private UserInfoServiceImpl userInfoServiceImpl;
  private UserPrincipal userPrincipal;
  private UserInfo userInfoWithName;
  private UserInfo userInfoWithDiffName;
  private UserInfo userInfoWithNameAndEmail;
  private UserInfo userInfoWithNameAndDiffEmail;
  private UserInfo userInfoWithDiffNameAndEmail;
  private UserInfo userInfoWithNameAndEmailAndUuid;
  private UserInfo userInfoWithDiffNameAndEmailAndUuid;
  private ServiceAccountPrincipal serviceAccountPrincipal;
  @Before
  public void setup() {
    userClient = mock(UserClient.class);
    ngUserService = mock(NgUserService.class);
    accessControlClient = mock(AccessControlClient.class);
    userInfoServiceImpl = spy(new UserInfoServiceImpl(userClient, ngUserService, accessControlClient));
    userPrincipal = new UserPrincipal("ABC", "abc@harness.io", "abc", randomAlphabetic(10));
    userInfoWithName = UserInfo.builder().name("abc").build();
    userInfoWithDiffName = UserInfo.builder().name("abcx").build();
    userInfoWithNameAndEmail = UserInfo.builder().name("abc").email("abc@harness.io").build();
    userInfoWithNameAndDiffEmail = UserInfo.builder().name("abc").email("abcx@harness.io").build();
    userInfoWithDiffNameAndEmail = UserInfo.builder().name("abcx").email("abc@harness.io").build();
    userInfoWithNameAndEmailAndUuid =
        UserInfo.builder().uuid(randomAlphabetic(20)).name("abc").email("abc@harness.io").build();
    userInfoWithDiffNameAndEmailAndUuid =
        UserInfo.builder().uuid(randomAlphabetic(20)).name("abcx").email("abc@harness.io").build();
    serviceAccountPrincipal = new ServiceAccountPrincipal("SA_ABC", "saabc@harness.io", "sa_abc");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testUpdateWhenUserTypeAndUserInfoMailNotPresent() throws IOException {
    PowerMockito.mockStatic(SourcePrincipalContextBuilder.class);
    when(SourcePrincipalContextBuilder.getSourcePrincipal()).thenReturn(userPrincipal);

    RestResponse<Optional<UserInfo>> restResponse =
        new RestResponse<>(Optional.ofNullable(userInfoWithDiffNameAndEmailAndUuid));
    Call<RestResponse<Optional<UserInfo>>> restResponseCall = mock(Call.class);

    when(userClient.updateUser(userInfoWithDiffNameAndEmail)).thenReturn(restResponseCall);
    when(restResponseCall.execute()).thenReturn(Response.success(restResponse));
    UserInfo updatedUserInfo = userInfoServiceImpl.update(userInfoWithDiffName, randomAlphabetic(10));
    ArgumentCaptor<UserInfo> userInfoArgument = ArgumentCaptor.forClass(UserInfo.class);
    verify(userClient, times(1)).updateUser(userInfoArgument.capture());

    assertThat(updatedUserInfo).isEqualTo(userInfoWithDiffNameAndEmailAndUuid);
    assertThat(userInfoArgument.getValue()).isEqualTo(userInfoWithDiffNameAndEmail);
    verifyNoMoreInteractions(userClient);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testUpdateWhenUserTypeAndUserInfoMailNotEqualTargetUserEmail() {
    PowerMockito.mockStatic(SourcePrincipalContextBuilder.class);
    when(SourcePrincipalContextBuilder.getSourcePrincipal()).thenReturn(userPrincipal);

    try {
      userInfoServiceImpl.update(userInfoWithNameAndDiffEmail, randomAlphabetic(10));
      fail("Expected failure as Cannot modify details of different user");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message")).isEqualTo("Cannot modify details of different user");
    }
    verifyNoMoreInteractions(userClient);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testUpdateWhenUserTypeAndUserInfoMailEqualsTargetUserEmail() throws IOException {
    PowerMockito.mockStatic(SourcePrincipalContextBuilder.class);
    when(SourcePrincipalContextBuilder.getSourcePrincipal()).thenReturn(userPrincipal);

    RestResponse<Optional<UserInfo>> restResponse =
        new RestResponse<>(Optional.ofNullable(userInfoWithDiffNameAndEmailAndUuid));
    Call<RestResponse<Optional<UserInfo>>> restResponseCall = mock(Call.class);

    when(userClient.updateUser(userInfoWithDiffNameAndEmail)).thenReturn(restResponseCall);
    when(restResponseCall.execute()).thenReturn(Response.success(restResponse));
    UserInfo updatedUserInfo = userInfoServiceImpl.update(userInfoWithDiffNameAndEmail, randomAlphabetic(10));
    ArgumentCaptor<UserInfo> userInfoArgument = ArgumentCaptor.forClass(UserInfo.class);
    verify(userClient, times(1)).updateUser(userInfoArgument.capture());

    assertThat(updatedUserInfo).isEqualTo(userInfoWithDiffNameAndEmailAndUuid);
    assertThat(userInfoArgument.getValue()).isEqualTo(userInfoWithDiffNameAndEmail);
    verifyNoMoreInteractions(userClient);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testUpdateWhenServiceAccountTypeAndAccountIdNotPresent() throws IOException {
    PowerMockito.mockStatic(SourcePrincipalContextBuilder.class);
    when(SourcePrincipalContextBuilder.getSourcePrincipal()).thenReturn(serviceAccountPrincipal);

    RestResponse<Optional<UserInfo>> restResponse =
        new RestResponse<>(Optional.ofNullable(userInfoWithNameAndEmailAndUuid));
    Call<RestResponse<Optional<UserInfo>>> restResponseCall = mock(Call.class);

    when(userClient.getUserByEmailId(userInfoWithNameAndEmail.getEmail())).thenReturn(restResponseCall);
    when(restResponseCall.execute()).thenReturn(Response.success(restResponse));
    try {
      userInfoServiceImpl.update(userInfoWithNameAndEmail, null);
      fail("Expected failure as accountId Null");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message"))
          .isEqualTo("Account Identifier is required to update user details");
    }
    verify(userClient, times(0)).updateUser(any());
    verify(accessControlClient, times(0)).checkForAccessOrThrow(any(), any(), any());
    verify(ngUserService, times(0)).isUserAtScope(any(), any());
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testUpdateWhenServiceAccountTypeAndTargetEmailIdNotPresent() {
    PowerMockito.mockStatic(SourcePrincipalContextBuilder.class);
    when(SourcePrincipalContextBuilder.getSourcePrincipal()).thenReturn(serviceAccountPrincipal);

    try {
      userInfoServiceImpl.update(userInfoWithName, randomAlphabetic(10));
      fail("Expected failure as target email id Null");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message")).isEqualTo("Email ID is required to update user details");
    }
    verify(userClient, times(0)).updateUser(any());
    verify(accessControlClient, times(0)).checkForAccessOrThrow(any(), any(), any());
    verify(ngUserService, times(0)).isUserAtScope(any(), any());
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testUpdateWhenServiceAccountTypeAndTargetEmailIdNotPresentAndAccountIdNotPresent() {
    PowerMockito.mockStatic(SourcePrincipalContextBuilder.class);
    when(SourcePrincipalContextBuilder.getSourcePrincipal()).thenReturn(serviceAccountPrincipal);

    try {
      userInfoServiceImpl.update(userInfoWithName, null);
      fail("Expected failure as target email id Null");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message")).isEqualTo("Email ID is required to update user details");
    }
    verify(userClient, times(0)).updateUser(any());
    verify(accessControlClient, times(0)).checkForAccessOrThrow(any(), any(), any());
    verify(ngUserService, times(0)).isUserAtScope(any(), any());
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testUpdateWhenServiceAccountTypeAndTargetEmailIdPresentAndAccountIdPresentAndUserDne()
      throws IOException {
    PowerMockito.mockStatic(SourcePrincipalContextBuilder.class);
    when(SourcePrincipalContextBuilder.getSourcePrincipal()).thenReturn(serviceAccountPrincipal);

    RestResponse<Optional<UserInfo>> restResponse = new RestResponse<>(Optional.ofNullable(null));
    Call<RestResponse<Optional<UserInfo>>> restResponseCall = mock(Call.class);

    when(userClient.getUserByEmailId(userInfoWithNameAndEmail.getEmail())).thenReturn(restResponseCall);
    when(restResponseCall.execute()).thenReturn(Response.success(restResponse));
    try {
      userInfoServiceImpl.update(userInfoWithNameAndEmail, randomAlphabetic(10));
      fail("Expected failure as User with given email ID doesn't exist");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message"))
          .isEqualTo(String.format("User with %s email ID doesn't exist", userInfoWithNameAndEmail.getEmail()));
    }
    verify(userClient, times(0)).updateUser(any());
    verify(accessControlClient, times(0)).checkForAccessOrThrow(any(), any(), any());
    verify(ngUserService, times(0)).isUserAtScope(any(), any());
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void
  testUpdateWhenServiceAccountTypeAndTargetEmailIdPresentAndAccountIdPresentAndUserExistButNotPartOfAccount()
      throws IOException {
    PowerMockito.mockStatic(SourcePrincipalContextBuilder.class);
    when(SourcePrincipalContextBuilder.getSourcePrincipal()).thenReturn(serviceAccountPrincipal);

    RestResponse<Optional<UserInfo>> restResponse =
        new RestResponse<>(Optional.ofNullable(userInfoWithNameAndEmailAndUuid));
    Call<RestResponse<Optional<UserInfo>>> restResponseCall = mock(Call.class);
    String accountIdentifier = randomAlphabetic(10);

    when(userClient.getUserByEmailId(userInfoWithNameAndEmail.getEmail())).thenReturn(restResponseCall);
    when(restResponseCall.execute()).thenReturn(Response.success(restResponse));
    when(ngUserService.isUserAtScope(
             userInfoWithNameAndEmailAndUuid.getUuid(), Scope.builder().accountIdentifier(accountIdentifier).build()))
        .thenReturn(false);
    try {
      userInfoServiceImpl.update(userInfoWithNameAndEmail, accountIdentifier);
      fail("Expected failure as target user not part of account");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message"))
          .isEqualTo(String.format("User with %s email ID is not a part of the account %s",
              userInfoWithNameAndEmailAndUuid.getEmail(), accountIdentifier));
    }
    verify(ngUserService, times(1))
        .isUserAtScope(
            userInfoWithNameAndEmailAndUuid.getUuid(), Scope.builder().accountIdentifier(accountIdentifier).build());
    verify(userClient, times(0)).updateUser(any());
    verify(accessControlClient, times(0)).checkForAccessOrThrow(any(), any(), any());
    verifyNoMoreInteractions(ngUserService);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void
  testUpdateWhenServiceAccountTypeAndTargetEmailIdPresentAndAccountIdPresentAndUserExistAndPartOfAccountButAccessNot()
      throws IOException {
    PowerMockito.mockStatic(SourcePrincipalContextBuilder.class);
    when(SourcePrincipalContextBuilder.getSourcePrincipal()).thenReturn(serviceAccountPrincipal);
    String accountIdentifier = randomAlphabetic(10);

    RestResponse<Optional<UserInfo>> restResponse =
        new RestResponse<>(Optional.ofNullable(userInfoWithNameAndEmailAndUuid));
    Call<RestResponse<Optional<UserInfo>>> restResponseCall = mock(Call.class);
    when(userClient.getUserByEmailId(userInfoWithNameAndEmail.getEmail())).thenReturn(restResponseCall);
    when(restResponseCall.execute()).thenReturn(Response.success(restResponse));

    when(ngUserService.isUserAtScope(
             userInfoWithNameAndEmailAndUuid.getUuid(), Scope.builder().accountIdentifier(accountIdentifier).build()))
        .thenReturn(true);

    doThrow(new NGAccessDeniedException("Sample access denied message", WingsException.USER,
                Collections.singletonList(
                    PermissionCheckDTO.builder()
                        .permission(MANAGE_USER_PERMISSION)
                        .resourceType(USER)
                        .resourceIdentifier(userInfoWithNameAndEmailAndUuid.getUuid())
                        .resourceScope(ResourceScope.builder().accountIdentifier(accountIdentifier).build())
                        .build())))
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.builder().accountIdentifier(accountIdentifier).build(),
            Resource.of(USER, userInfoWithNameAndEmailAndUuid.getUuid()), MANAGE_USER_PERMISSION);
    try {
      userInfoServiceImpl.update(userInfoWithNameAndEmail, accountIdentifier);
      fail("Expected failure as not permitted");
    } catch (NGAccessDeniedException exception) {
    }
    verify(userClient, times(0)).updateUser(any());
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.builder().accountIdentifier(accountIdentifier).build(),
            Resource.of(USER, userInfoWithNameAndEmailAndUuid.getUuid()), MANAGE_USER_PERMISSION);
    verify(ngUserService, times(1))
        .isUserAtScope(
            userInfoWithNameAndEmailAndUuid.getUuid(), Scope.builder().accountIdentifier(accountIdentifier).build());
    verifyNoMoreInteractions(accessControlClient);
    verifyNoMoreInteractions(ngUserService);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void
  testUpdateWhenServiceAccountTypeAndTargetEmailIdPresentAndAccountIdPresentAndUserExistAndPartOfAccountAndAccess()
      throws IOException {
    PowerMockito.mockStatic(SourcePrincipalContextBuilder.class);
    when(SourcePrincipalContextBuilder.getSourcePrincipal()).thenReturn(serviceAccountPrincipal);
    String accountIdentifier = randomAlphabetic(10);

    RestResponse<Optional<UserInfo>> restResponse =
        new RestResponse<>(Optional.ofNullable(userInfoWithNameAndEmailAndUuid));
    Call<RestResponse<Optional<UserInfo>>> restResponseCall = mock(Call.class);
    when(userClient.getUserByEmailId(userInfoWithNameAndEmail.getEmail())).thenReturn(restResponseCall);
    when(restResponseCall.execute()).thenReturn(Response.success(restResponse));

    when(ngUserService.isUserAtScope(
             userInfoWithNameAndEmailAndUuid.getUuid(), Scope.builder().accountIdentifier(accountIdentifier).build()))
        .thenReturn(true);

    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.builder().accountIdentifier(accountIdentifier).build(),
            Resource.of(USER, userInfoWithNameAndEmailAndUuid.getUuid()), MANAGE_USER_PERMISSION);

    when(userClient.updateUser(userInfoWithNameAndEmail)).thenReturn(restResponseCall);
    when(restResponseCall.execute()).thenReturn(Response.success(restResponse));
    UserInfo updatedUserInfo = userInfoServiceImpl.update(userInfoWithNameAndEmail, accountIdentifier);
    ArgumentCaptor<UserInfo> userInfoArgument = ArgumentCaptor.forClass(UserInfo.class);
    verify(userClient, times(1)).updateUser(userInfoArgument.capture());
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.builder().accountIdentifier(accountIdentifier).build(),
            Resource.of(USER, userInfoWithNameAndEmailAndUuid.getUuid()), MANAGE_USER_PERMISSION);
    verify(ngUserService, times(1))
        .isUserAtScope(
            userInfoWithNameAndEmailAndUuid.getUuid(), Scope.builder().accountIdentifier(accountIdentifier).build());
    verify(userClient, times(1)).getUserByEmailId(userInfoWithNameAndEmail.getEmail());
    assertThat(updatedUserInfo).isEqualTo(userInfoWithNameAndEmailAndUuid);
    assertThat(userInfoArgument.getValue()).isEqualTo(userInfoWithNameAndEmail);
    verifyNoMoreInteractions(userClient);
    verifyNoMoreInteractions(accessControlClient);
    verifyNoMoreInteractions(ngUserService);
  }
}
