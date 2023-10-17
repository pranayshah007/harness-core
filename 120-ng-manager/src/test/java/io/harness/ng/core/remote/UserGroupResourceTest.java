/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_USERGROUP_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.USERGROUP;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.utils.PageUtils.getPageRequest;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@OwnedBy(PL)
public class UserGroupResourceTest extends CategoryTest {
  @Mock private UserGroupService userGroupService;
  @Mock private AccessControlClient accessControlClient;

  @InjectMocks UserGroupResource userGroupResource;

  String accountIdentifier = randomAlphabetic(10);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testListFilter_withAccessOnResourceType() {
    UserGroupFilterDTO userGroupFilterDTO = UserGroupFilterDTO.builder().accountIdentifier(accountIdentifier).build();
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    when(accessControlClient.hasAccess(
             ResourceScope.of(accountIdentifier, null, null), Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION))
        .thenReturn(true);
    when(userGroupService.list(eq(userGroupFilterDTO), any()))
        .thenReturn(PageUtils.getPage(Collections.emptyList(), 0, 10));
    userGroupResource.list(accountIdentifier, userGroupFilterDTO, pageRequest);
    verify(userGroupService, times(1)).list(userGroupFilterDTO, getPageRequest(pageRequest));
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testListFilter_withAccessOnSelectedResource() {
    List<UserGroup> userGroupList = getUserGroupList();
    UserGroupFilterDTO userGroupFilterDTO = UserGroupFilterDTO.builder().accountIdentifier(accountIdentifier).build();
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    Pageable pageable = Pageable.ofSize(50000);
    Page<UserGroup> page = PageUtils.getPage(userGroupList, 0, 10);
    when(accessControlClient.hasAccess(
             ResourceScope.of(accountIdentifier, null, null), Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION))
        .thenReturn(false);
    when(userGroupService.list(userGroupFilterDTO, pageable)).thenReturn(page);
    when(userGroupService.getPermittedUserGroups(page.getContent())).thenReturn(List.of(userGroupList.get(0)));
    ResponseDTO<PageResponse<UserGroupDTO>> result =
        userGroupResource.list(accountIdentifier, userGroupFilterDTO, pageRequest);
    verify(userGroupService, times(1)).list(userGroupFilterDTO, pageable);
    verify(userGroupService, times(1)).getPermittedUserGroups(page.getContent());
    assertThat(result.getData().getContent().size()).isEqualTo(1);
  }

  private List<UserGroup> getUserGroupList() {
    return List.of(UserGroup.builder().identifier("ug1").name("ug1").build(),
        UserGroup.builder().identifier("ug2").name("ug2").build());
  }
}
