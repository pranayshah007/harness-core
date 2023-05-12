/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.datahandler.services;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.invites.remote.NgInviteClient;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.user.AddUsersResponse;
import io.harness.rule.Owner;

import software.wings.beans.User;
import software.wings.service.intfc.UserService;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class AdminUserServiceImplTest extends CategoryTest {
  private UserService userService;
  private NgInviteClient ngInviteClient;
  private AdminUserServiceImpl adminUserService;

  @Before
  public void doSetup() throws IOException {
    userService = mock(UserService.class);
    ngInviteClient = mock(NgInviteClient.class, RETURNS_DEEP_STUBS);
    adminUserService = new AdminUserServiceImpl(userService, ngInviteClient);
    Call<ResponseDTO<AddUsersResponse>> request = mock(Call.class);
    doReturn(request).when(ngInviteClient).addUsers(any(), any(), any(), any());
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(AddUsersResponse.builder().build())));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void assignAdminRoleToEmail() {
    String accountId = randomAlphabetic(10);
    String email = "ab17@goat.com";
    when(userService.getUserByEmail(email))
        .thenReturn(User.Builder.anUser().uuid(randomAlphabetic(11)).email(email).build());
    assertTrue(adminUserService.assignAdminRoleToUserInNG(accountId, email));
    verify(userService, times(1)).getUserByEmail(any());
    verify(ngInviteClient, times(1)).addUsers(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void assignAdminRoleToUserId() {
    String accountId = randomAlphabetic(10);
    String userId = randomAlphabetic(11);
    when(userService.getUserByEmail(userId)).thenReturn(null);
    when(userService.getUserByUserId(userId, accountId))
        .thenReturn(User.Builder.anUser().uuid(userId).email(randomAlphabetic(12)).build());
    assertTrue(adminUserService.assignAdminRoleToUserInNG(accountId, userId));
    verify(userService, times(1)).getUserByEmail(any());
    verify(userService, times(1)).getUserByUserId(any(), any());
    verify(ngInviteClient, times(1)).addUsers(any(), any(), any(), any());
  }
}
