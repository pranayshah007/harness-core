/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.scim;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.NgManagerTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.rule.Owner;
import io.harness.scim.ScimGroup;
import io.harness.scim.ScimListResponse;

import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class NGScimGroupServiceImplTest extends NgManagerTestBase {
  private UserGroupService userGroupService;
  private NgUserService ngUserService;

  private NGScimGroupServiceImpl scimGroupService;

  @Before
  public void setup() throws IllegalAccessException {
    ngUserService = mock(NgUserService.class);
    userGroupService = mock(UserGroupService.class);

    scimGroupService = new NGScimGroupServiceImpl(userGroupService, ngUserService);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testCreateGroup() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("displayname");
    scimGroup.setId("id");

    UserGroup userGroup = UserGroup.builder()
                              .name(scimGroup.getDisplayName())
                              .identifier(scimGroup.getDisplayName().replaceAll("\\.", "_"))
                              .build();
    when(userGroupService.create(any())).thenReturn(userGroup);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(scimGroup.getDisplayName());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testCreateGroup2() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("display.name");
    scimGroup.setId("id");

    UserGroup userGroup = UserGroup.builder()
                              .name(scimGroup.getDisplayName())
                              .identifier(scimGroup.getDisplayName().replaceAll("\\.", "_"))
                              .build();
    when(userGroupService.create(any())).thenReturn(userGroup);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(scimGroup.getDisplayName().replaceAll("\\.", "_"));
    assertThat(userGroupCreated.getId()).isEqualTo("display_name");
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testCreateGroup4() {
    String accountId = "accountId";
    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("display_name");
    scimGroup.setId("id");

    UserGroup userGroup = UserGroup.builder()
                              .name(scimGroup.getDisplayName())
                              .identifier(scimGroup.getDisplayName().replaceAll("\\.", "_"))
                              .build();
    when(userGroupService.create(any())).thenReturn(userGroup);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(scimGroup.getDisplayName().replaceAll("\\.", "_"));
    assertThat(userGroupCreated.getId()).isEqualTo("display_name");
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testCreateGroup3() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("display.name");
    scimGroup.setId("id");

    when(userGroupService.create(any())).thenReturn(null);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNull();
    assertThat(userGroupCreated.getId()).isNull();
    assertThat(userGroupCreated.getMembers()).isNull();
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSearchGroupByName() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("testDisplayName");
    scimGroup.setId("id");

    UserGroup userGroup1 = UserGroup.builder().name(scimGroup.getDisplayName()).identifier(scimGroup.getId()).build();

    when(userGroupService.list(any(), any(), any())).thenReturn(new ArrayList<UserGroup>() {
      { add(userGroup1); }
    });

    ScimListResponse<ScimGroup> response =
        scimGroupService.searchGroup("displayName eq \"testDisplayName\"", accountId, 1, 0);

    assertThat(response.getTotalResults()).isEqualTo(1);
    assertThat(response.getStartIndex()).isEqualTo(0);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSearchGroupByNameNoSkipNoCountReturnsDefaultResult() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("testDisplayName");
    scimGroup.setId("id");

    UserGroup userGroup1 = UserGroup.builder().name(scimGroup.getDisplayName()).identifier(scimGroup.getId()).build();

    when(userGroupService.list(any(), any(), any())).thenReturn(new ArrayList<>() {
      { add(userGroup1); }
    });

    ScimListResponse<ScimGroup> response =
        scimGroupService.searchGroup("displayName eq \"testDisplayName\"", accountId, null, null);

    assertThat(response.getTotalResults()).isEqualTo(1);
    assertThat(response.getStartIndex()).isEqualTo(0);
  }
}