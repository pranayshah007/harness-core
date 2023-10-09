/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.users;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.KARAN;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.scopes.HarnessScopeLevel;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.rule.Owner;
import io.harness.usermembership.remote.UserMembershipClient;

import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import retrofit2.Response;

@OwnedBy(PL)
public class HarnessUserServiceImplTest extends AccessControlTestBase {
  private UserMembershipClient userMembershipClient;
  private UserService userService;
  private HarnessUserService harnessUserService;

  @Before
  public void setup() {
    userMembershipClient = mock(UserMembershipClient.class, RETURNS_DEEP_STUBS);
    userService = mock(UserService.class);
    harnessUserService = spy(new HarnessUserServiceImpl(userMembershipClient, userService));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testSyncFound() throws IOException {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(11);
    Scope scope =
        Scope.builder().level(HarnessScopeLevel.ACCOUNT).parentScope(null).instanceId(accountIdentifier).build();
    User user = User.builder().identifier(identifier).scopeIdentifier(scope.toString()).build();
    when(userMembershipClient.isUserInScope(identifier, accountIdentifier, null, null).execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(Boolean.TRUE)));
    when(userMembershipClient.getUser(identifier, accountIdentifier).execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(UserMetadataDTO.builder().build())));
    when(userService.createIfNotPresent(user)).thenReturn(user);
    harnessUserService.sync(identifier, scope);
    verify(userMembershipClient, atLeastOnce()).isUserInScope(identifier, accountIdentifier, null, null);
    verify(userService, times(1)).createIfNotPresent(user);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testSyncNotFound() throws IOException {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(11);
    Scope scope =
        Scope.builder().level(HarnessScopeLevel.ACCOUNT).parentScope(null).instanceId(accountIdentifier).build();
    User user = User.builder().identifier(identifier).scopeIdentifier(scope.toString()).build();
    when(userMembershipClient.isUserInScope(identifier, accountIdentifier, null, null).execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(Boolean.FALSE)));
    when(userMembershipClient.getUser(identifier, accountIdentifier).execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(UserMetadataDTO.builder().build())));
    when(userService.deleteIfPresent(identifier, scope.toString())).thenReturn(Optional.of(user));
    harnessUserService.sync(identifier, scope);
    verify(userMembershipClient, atLeastOnce()).isUserInScope(identifier, accountIdentifier, null, null);
    verify(userService, times(1)).deleteIfPresent(identifier, scope.toString());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void shouldNotFailIfUserMetadataIsNull() throws IOException {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(11);
    Scope scope =
        Scope.builder().level(HarnessScopeLevel.ACCOUNT).parentScope(null).instanceId(accountIdentifier).build();
    when(userMembershipClient.isUserInScope(identifier, accountIdentifier, null, null).execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(Boolean.TRUE)));
    when(userMembershipClient.getUser(identifier, accountIdentifier).execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(null)));

    harnessUserService.sync(identifier, scope);

    verify(userMembershipClient, atLeastOnce()).isUserInScope(identifier, accountIdentifier, null, null);
    verify(userMembershipClient, atLeastOnce()).getUser(identifier, accountIdentifier);
    verify(userService, times(1)).deleteIfPresent(identifier, scope.toString());
  }
}
