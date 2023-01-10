/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.NGConstants.DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.repositories.user.spring.UserMembershipRepository;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(PL)
public class DefaultUserGroupServiceTest extends CategoryTest {
  @Mock private UserGroupService userGroupService;
  @Mock AccessControlAdminClient accessControlAdminClient;
  @Mock NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock UserMembershipRepository userMembershipRepository;
  DefaultUserGroupServiceImpl defaultUserGroupService;

  private static final String ACCOUNT_IDENTIFIER = "Account1";

  @Before
  public void setup() {
    initMocks(this);
    defaultUserGroupService = new DefaultUserGroupServiceImpl(
        userGroupService, accessControlAdminClient, ngFeatureFlagHelperService, userMembershipRepository);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void testGetDefaultUserGroup() {
    Optional<UserGroup> userGroupOptional = Optional.of(UserGroup.builder().build());
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, null, null);
    when(userGroupService.get(ACCOUNT_IDENTIFIER, null, null, getUserGroupIdentifier(scope)))
        .thenReturn(userGroupOptional);
    Optional<UserGroup> result = defaultUserGroupService.get(scope);
    assertThat(userGroupOptional).isEqualTo(result);
    verify(userGroupService, times(1)).get(ACCOUNT_IDENTIFIER, null, null, getUserGroupIdentifier(scope));
  }

  private String getUserGroupIdentifier(Scope scope) {
    String userGroupIdentifier = DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER;
    if (isNotEmpty(scope.getProjectIdentifier())) {
      userGroupIdentifier = DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER;
    } else if (isNotEmpty(scope.getOrgIdentifier())) {
      userGroupIdentifier = DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER;
    }
    return userGroupIdentifier;
  }
}
