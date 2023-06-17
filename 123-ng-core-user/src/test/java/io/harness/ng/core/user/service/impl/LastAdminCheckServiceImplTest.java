/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.user.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static java.util.Collections.emptyList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.licensing.Edition;
import io.harness.licensing.services.LicenseService;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.remote.dto.LastAdminCheckFilter;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(PL)
public class LastAdminCheckServiceImplTest extends CategoryTest {
  @Mock private LicenseService licenseService;
  @Mock private NgUserService ngUserService;
  @Mock private UserGroupService userGroupService;
  @Mock private AccessControlAdminClient accessControlAdminClient;
  @Spy @Inject @InjectMocks private LastAdminCheckServiceImpl lastAdminCheckService;
  private final String accountIdentifier = randomAlphabetic(10);

  @Before
  public void setup() throws NoSuchFieldException {
    initMocks(this);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void doesAdminExistAfterUserDeletionCommunityEdition() {
    when(licenseService.calculateAccountEdition(accountIdentifier)).thenReturn(Edition.COMMUNITY);
    String userIdentifier = randomAlphabetic(10);
    LastAdminCheckFilter lastAdminCheckFilter = new LastAdminCheckFilter(userIdentifier, null);

    // user found in user membership
    when(ngUserService.listUserIds(Scope.of(accountIdentifier, null, null)))
        .thenReturn(Lists.newArrayList(randomAlphabetic(11)));
    assertTrue(lastAdminCheckService.doesAdminExistAfterRemoval(accountIdentifier, lastAdminCheckFilter));

    // user not found in user membership
    when(ngUserService.listUserIds(Scope.of(accountIdentifier, null, null))).thenReturn(emptyList());
    assertFalse(lastAdminCheckService.doesAdminExistAfterRemoval(accountIdentifier, lastAdminCheckFilter));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void doesAdminExistAfterUserDeletionTeamEdition() {
    when(licenseService.calculateAccountEdition(accountIdentifier)).thenReturn(Edition.TEAM);
    String userIdentifier = randomAlphabetic(10);
    LastAdminCheckFilter lastAdminCheckFilter = new LastAdminCheckFilter(userIdentifier, null);

    // another user is admin
    List<PrincipalDTO> principals =
        Lists.newArrayList(PrincipalDTO.builder().identifier(randomAlphabetic(11)).type(PrincipalType.USER).build());
    assertLastAdminCheck(true, principals, null, lastAdminCheckFilter);

    // no user is admin
    principals = new ArrayList<>();
    assertLastAdminCheck(false, principals, null, lastAdminCheckFilter);

    // one user group with different user is admin
    String userGroupIdentifier = randomAlphabetic(11);
    principals = Lists.newArrayList(
        PrincipalDTO.builder().identifier(userGroupIdentifier).type(PrincipalType.USER_GROUP).build());
    List<UserGroup> userGroups = Lists.newArrayList(UserGroup.builder()
                                                        .accountIdentifier(accountIdentifier)
                                                        .identifier(userGroupIdentifier)
                                                        .users(Lists.newArrayList(randomAlphabetic(12)))
                                                        .build());
    assertLastAdminCheck(true, principals, userGroups, lastAdminCheckFilter);

    // one user group with same user is admin
    userGroups = Lists.newArrayList(UserGroup.builder()
                                        .accountIdentifier(accountIdentifier)
                                        .identifier(userGroupIdentifier)
                                        .users(Lists.newArrayList(userIdentifier))
                                        .build());
    assertLastAdminCheck(false, principals, userGroups, lastAdminCheckFilter);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void doesAdminExistAfterUserGroupDeletionCommunityEdition() {
    when(licenseService.calculateAccountEdition(accountIdentifier)).thenReturn(Edition.COMMUNITY);
    String userGroupIdentifier = randomAlphabetic(10);
    LastAdminCheckFilter lastAdminCheckFilter = new LastAdminCheckFilter(null, userGroupIdentifier);

    assertTrue(lastAdminCheckService.doesAdminExistAfterRemoval(accountIdentifier, lastAdminCheckFilter));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void doesAdminExistAfterUserGroupDeletionTeamEdition() {
    when(licenseService.calculateAccountEdition(accountIdentifier)).thenReturn(Edition.TEAM);
    String userGroupIdentifier = randomAlphabetic(10);
    LastAdminCheckFilter lastAdminCheckFilter = new LastAdminCheckFilter(null, userGroupIdentifier);

    // one user is admin
    List<PrincipalDTO> principals =
        Lists.newArrayList(PrincipalDTO.builder().identifier(randomAlphabetic(10)).type(PrincipalType.USER).build());
    assertLastAdminCheck(true, principals, null, lastAdminCheckFilter);

    // no user is admin
    principals = new ArrayList<>();
    assertLastAdminCheck(false, principals, null, lastAdminCheckFilter);

    // another user group with different user is admin
    String otherUserGroupIdentifier = randomAlphabetic(11);
    principals = Lists.newArrayList(
        PrincipalDTO.builder().identifier(otherUserGroupIdentifier).type(PrincipalType.USER_GROUP).build());
    List<UserGroup> userGroups = Lists.newArrayList(UserGroup.builder()
                                                        .accountIdentifier(accountIdentifier)
                                                        .identifier(otherUserGroupIdentifier)
                                                        .users(Lists.newArrayList(randomAlphabetic(10)))
                                                        .build());
    assertLastAdminCheck(true, principals, userGroups, lastAdminCheckFilter);

    // another user group with no user is admin
    userGroups = Lists.newArrayList(UserGroup.builder()
                                        .accountIdentifier(accountIdentifier)
                                        .identifier(otherUserGroupIdentifier)
                                        .users(Lists.newArrayList())
                                        .build());
    assertLastAdminCheck(false, principals, userGroups, lastAdminCheckFilter);

    // only same user group is admin
    principals = Lists.newArrayList(
        PrincipalDTO.builder().identifier(userGroupIdentifier).type(PrincipalType.USER_GROUP).build());
    userGroups = Lists.newArrayList(UserGroup.builder()
                                        .accountIdentifier(accountIdentifier)
                                        .identifier(userGroupIdentifier)
                                        .users(Lists.newArrayList(randomAlphabetic(10)))
                                        .build());
    assertLastAdminCheck(false, principals, userGroups, lastAdminCheckFilter);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void doesAdminExistAfterUserRemovalFromUserGroupCommunityEdition() {
    when(licenseService.calculateAccountEdition(accountIdentifier)).thenReturn(Edition.COMMUNITY);
    String userIdentifier = randomAlphabetic(10);
    String userGroupIdentifier = randomAlphabetic(11);
    LastAdminCheckFilter lastAdminCheckFilter = new LastAdminCheckFilter(userIdentifier, userGroupIdentifier);

    assertTrue(lastAdminCheckService.doesAdminExistAfterRemoval(accountIdentifier, lastAdminCheckFilter));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void doesAdminExistAfterUserRemovalFromUserGroupTeamEdition() {
    when(licenseService.calculateAccountEdition(accountIdentifier)).thenReturn(Edition.TEAM);
    String userIdentifier = randomAlphabetic(10);
    String userGroupIdentifier = randomAlphabetic(11);
    LastAdminCheckFilter lastAdminCheckFilter = new LastAdminCheckFilter(userIdentifier, userGroupIdentifier);

    // another user is admin
    List<PrincipalDTO> principals =
        Lists.newArrayList(PrincipalDTO.builder().identifier(randomAlphabetic(10)).type(PrincipalType.USER).build());
    assertLastAdminCheck(true, principals, null, lastAdminCheckFilter);

    // same user is admin
    principals = Lists.newArrayList(PrincipalDTO.builder().identifier(userIdentifier).type(PrincipalType.USER).build());
    assertLastAdminCheck(true, principals, null, lastAdminCheckFilter);

    // no user is admin
    principals = new ArrayList<>();
    assertLastAdminCheck(false, principals, null, lastAdminCheckFilter);

    // another user group with different user is admin
    String otherUserGroupIdentifier = randomAlphabetic(11);
    principals = Lists.newArrayList(
        PrincipalDTO.builder().identifier(otherUserGroupIdentifier).type(PrincipalType.USER_GROUP).build());
    List<UserGroup> userGroups = Lists.newArrayList(UserGroup.builder()
                                                        .accountIdentifier(accountIdentifier)
                                                        .identifier(otherUserGroupIdentifier)
                                                        .users(Lists.newArrayList(randomAlphabetic(11)))
                                                        .build());
    assertLastAdminCheck(true, principals, userGroups, lastAdminCheckFilter);

    // another user group with no user is admin
    userGroups = Lists.newArrayList(UserGroup.builder()
                                        .accountIdentifier(accountIdentifier)
                                        .identifier(otherUserGroupIdentifier)
                                        .users(Lists.newArrayList())
                                        .build());
    assertLastAdminCheck(false, principals, userGroups, lastAdminCheckFilter);

    // same user group with different user is admin
    principals = Lists.newArrayList(
        PrincipalDTO.builder().identifier(userGroupIdentifier).type(PrincipalType.USER_GROUP).build());
    userGroups = Lists.newArrayList(UserGroup.builder()
                                        .accountIdentifier(accountIdentifier)
                                        .identifier(userGroupIdentifier)
                                        .users(Lists.newArrayList(randomAlphabetic(11)))
                                        .build());
    assertLastAdminCheck(true, principals, userGroups, lastAdminCheckFilter);

    // same user group with same user is admin
    userGroups = Lists.newArrayList(UserGroup.builder()
                                        .accountIdentifier(accountIdentifier)
                                        .identifier(userGroupIdentifier)
                                        .users(Lists.newArrayList(userIdentifier))
                                        .build());
    assertLastAdminCheck(false, principals, userGroups, lastAdminCheckFilter);
  }

  private void assertLastAdminCheck(boolean expected, List<PrincipalDTO> principals, List<UserGroup> userGroups,
      LastAdminCheckFilter lastAdminCheckFilter) {
    doReturn(principals).when(lastAdminCheckService).getAdminsFromAccessControl(accountIdentifier);
    if (userGroups != null) {
      when(userGroupService.list(any(UserGroupFilterDTO.class))).thenReturn(userGroups);
    }
    assertEquals(expected, lastAdminCheckService.doesAdminExistAfterRemoval(accountIdentifier, lastAdminCheckFilter));
  }
}
