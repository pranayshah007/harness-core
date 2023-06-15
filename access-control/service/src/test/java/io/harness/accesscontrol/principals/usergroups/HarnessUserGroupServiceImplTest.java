/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.usergroups;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static java.util.Collections.emptySet;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.harness.HarnessScopeLevel;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.rule.Owner;
import io.harness.usergroups.UserGroupClient;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import retrofit2.Response;

@OwnedBy(PL)
public class HarnessUserGroupServiceImplTest extends AccessControlTestBase {
  private UserGroupClient userGroupClient;
  private UserGroupService userGroupService;
  private HarnessUserGroupService harnessUserGroupService;

  @Before
  public void setup() {
    userGroupClient = mock(UserGroupClient.class, RETURNS_DEEP_STUBS);
    userGroupService = mock(UserGroupService.class);
    harnessUserGroupService = spy(new HarnessUserGroupServiceImpl(userGroupClient, userGroupService));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testSyncFound() throws IOException {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(11);
    Scope scope =
        Scope.builder().level(HarnessScopeLevel.ACCOUNT).parentScope(null).instanceId(accountIdentifier).build();
    UserGroup userGroup =
        UserGroup.builder().identifier(identifier).scopeIdentifier(scope.toString()).users(emptySet()).build();
    when(userGroupClient.getUserGroup(identifier, accountIdentifier, null, null).execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            UserGroupDTO.builder().accountIdentifier(accountIdentifier).identifier(identifier).build())));
    when(userGroupService.upsert(userGroup)).thenReturn(userGroup);
    harnessUserGroupService.sync(identifier, scope);
    verify(userGroupClient, atLeastOnce()).getUserGroup(identifier, accountIdentifier, null, null);
    verify(userGroupService, times(1)).upsert(userGroup);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testSyncNotFound() throws IOException {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(11);
    Scope scope =
        Scope.builder().level(HarnessScopeLevel.ACCOUNT).parentScope(null).instanceId(accountIdentifier).build();
    when(userGroupClient.getUserGroup(identifier, accountIdentifier, null, null).execute())
        .thenThrow(InvalidRequestException.class);
    doNothing().when(userGroupService).deleteIfPresent(identifier, scope.toString());
    harnessUserGroupService.sync(identifier, scope);
    verify(userGroupClient, atLeastOnce()).getUserGroup(identifier, accountIdentifier, null, null);
    verify(userGroupService, times(1)).deleteIfPresent(identifier, scope.toString());
  }
}
