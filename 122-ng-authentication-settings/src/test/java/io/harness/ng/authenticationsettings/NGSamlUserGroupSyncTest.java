/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.authenticationsettings;

import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.utils.PageUtils.getPageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PL)
public class NGSamlUserGroupSyncTest extends AuthenticationSettingTestBase {
  @Mock private UserGroupService userGroupService;
  @Mock private NgUserService ngUserService;
  @InjectMocks NGSamlUserGroupSync ngSamlUserGroupSync;

  @Before
  public void setup() {
    initMocks(this);
    //    userGroupService = mock(UserGroupService.class);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void test_getUserGroupsAtScope_WithNoUsergroups() {
    String accountId = "accountId1";
    String orgId = "orgId1";
    String projectId = "projectId1";
    Page<UserGroup> pagedUserGroups = Page.empty();
    when(userGroupService.list(accountId, orgId, projectId, null,
             getPageRequest(PageRequest.builder().pageIndex(0).pageSize(40).build())))
        .thenReturn(pagedUserGroups);
    final List<UserGroup> userGroupsAtScope = ngSamlUserGroupSync.getUserGroupsAtScope(accountId, orgId, projectId);
    assertThat(userGroupsAtScope.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUserRemovalPostSync() {
    String userId = "userId";

    List<UserGroup> userGroupList = new ArrayList<>();

    when(userGroupService.list(any(Criteria.class), any(), any())).thenReturn(userGroupList);
    ngSamlUserGroupSync.removeUsersFromScopesPostSync(userId);
    verify(ngUserService, atLeast(1)).removeUserWithCriteria(anyString(), any(), any());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUserRemovalPostSync3() {
    String userId = "userId";

    List<UserGroup> userGroupList = new ArrayList<>();

    when(userGroupService.list(any(Criteria.class), any(), any())).thenReturn(userGroupList);
    ngSamlUserGroupSync.removeUsersFromScopesPostSync(userId);
    verify(ngUserService, times(3)).removeUserWithCriteria(anyString(), any(), any());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUserRemovalPostSync2() {
    String accountId = "accountId1";
    String orgId = "orgId1";
    String projectId = "projectId1";
    String userId = "userId";

    UserGroup userGroup = UserGroup.builder()
                              .name("name")
                              .accountIdentifier(accountId)
                              .orgIdentifier(orgId)
                              .projectIdentifier(projectId)
                              .identifier("userGroup")
                              .build();
    List<UserGroup> userGroupList = new ArrayList<>();
    userGroupList.add(userGroup);

    when(userGroupService.list(any(Criteria.class), any(), any())).thenReturn(userGroupList);
    ngSamlUserGroupSync.removeUsersFromScopesPostSync(userId);
    verify(ngUserService, times(0)).removeUserWithCriteria(anyString(), any(), any());
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void test_getUserGroupsAtScope() {
    String accountId = "accountId1";
    String orgId = "orgId1";
    String projectId = "projectId1";
    Page<UserGroup> mockedUsergroupResponse = createMockedUsergroupResponse(accountId, orgId, projectId);
    when(userGroupService.list(any(), any(), any(), any(), any())).thenReturn(mockedUsergroupResponse);

    List<UserGroup> userGroupsAtScope = ngSamlUserGroupSync.getUserGroupsAtScope(accountId, orgId, projectId);
    assertThat(userGroupsAtScope.size()).isEqualTo(6);
  }

  private Page<UserGroup> createMockedUsergroupResponse(String accountId, String orgId, String projectId) {
    UserGroup userGroup1 =
        UserGroup.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build();
    UserGroup userGroup2 =
        UserGroup.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build();
    UserGroup userGroup3 =
        UserGroup.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build();
    UserGroup userGroup4 =
        UserGroup.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build();
    UserGroup userGroup5 =
        UserGroup.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build();
    UserGroup userGroup6 =
        UserGroup.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build();
    List<UserGroup> pagedUserGroup =
        Arrays.asList(userGroup1, userGroup2, userGroup3, userGroup4, userGroup5, userGroup6);
    return new PageImpl<>(pagedUserGroup);
  }
}
