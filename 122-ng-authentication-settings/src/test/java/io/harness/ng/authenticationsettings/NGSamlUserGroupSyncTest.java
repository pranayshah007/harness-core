/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.authenticationsettings;

import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
}