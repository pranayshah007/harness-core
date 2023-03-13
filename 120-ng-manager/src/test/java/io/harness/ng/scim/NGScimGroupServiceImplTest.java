/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.scim;
import static io.harness.NGConstants.CREATED;
import static io.harness.NGConstants.LAST_MODIFIED;
import static io.harness.NGConstants.LOCATION;
import static io.harness.NGConstants.RESOURCE_TYPE;
import static io.harness.NGConstants.VERSION;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.PL_JPMC_SCIM_REQUIREMENTS;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.VIKAS_M;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.NgManagerTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.rule.Owner;
import io.harness.scim.Member;
import io.harness.scim.ScimGroup;
import io.harness.scim.ScimListResponse;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class NGScimGroupServiceImplTest extends NgManagerTestBase {
  private static final Integer MAX_RESULT_COUNT = 20;

  private UserGroupService userGroupService;
  private NgUserService ngUserService;

  private NGScimGroupServiceImpl scimGroupService;
  private NGFeatureFlagHelperService ngFeatureFlagHelperService;

  @Before
  public void setup() throws IllegalAccessException {
    ngUserService = mock(NgUserService.class);
    userGroupService = mock(UserGroupService.class);
    ngFeatureFlagHelperService = mock(NGFeatureFlagHelperService.class);

    scimGroupService = new NGScimGroupServiceImpl(userGroupService, ngUserService, ngFeatureFlagHelperService);
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
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateGroup_shouldReturnMeta_ifFFTurnedOn() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("displayname");
    scimGroup.setId("id");

    UserGroup userGroup = UserGroup.builder()
                              .name(scimGroup.getDisplayName())
                              .identifier(scimGroup.getDisplayName().replaceAll("\\.", "_"))
                              .build();
    when(userGroupService.create(any())).thenReturn(userGroup);
    when(ngFeatureFlagHelperService.isEnabled(accountId, PL_JPMC_SCIM_REQUIREMENTS)).thenReturn(true);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(scimGroup.getDisplayName());
    assertNotNull(userGroupCreated.getMeta());
    assertNotNull(userGroupCreated.getMeta().get(RESOURCE_TYPE));
    assertNotNull(userGroupCreated.getMeta().get(CREATED));
    assertNotNull(userGroupCreated.getMeta().get(LAST_MODIFIED));
    assertNotNull(userGroupCreated.getMeta().get(LOCATION));
    assertNotNull(userGroupCreated.getMeta().get(VERSION));
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateGroup_shouldNotReturnMeta_ifFFTurnedOff() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("displayname");
    scimGroup.setId("id");

    UserGroup userGroup = UserGroup.builder()
                              .name(scimGroup.getDisplayName())
                              .identifier(scimGroup.getDisplayName().replaceAll("\\.", "_"))
                              .build();
    when(userGroupService.create(any())).thenReturn(userGroup);
    when(ngFeatureFlagHelperService.isEnabled(accountId, PL_JPMC_SCIM_REQUIREMENTS)).thenReturn(false);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(scimGroup.getDisplayName());
    assertNull(userGroupCreated.getMeta());
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
    Integer count = 1;
    Integer startIndex = 1;

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("testDisplayName");
    scimGroup.setId("id");

    UserGroup userGroup1 = UserGroup.builder().name(scimGroup.getDisplayName()).identifier(scimGroup.getId()).build();

    when(userGroupService.list(any(), any(), any())).thenReturn(new ArrayList<UserGroup>() {
      { add(userGroup1); }
    });

    ScimListResponse<ScimGroup> response =
        scimGroupService.searchGroup("displayName eq \"testDisplayName\"", accountId, count, startIndex);

    assertThat(response.getTotalResults()).isEqualTo(1);
    assertThat(response.getStartIndex()).isEqualTo(startIndex);
    assertThat(response.getItemsPerPage()).isEqualTo(count);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testSearchGroup_returnsNotNullRefInMembers() {
    String accountId = "accountId";
    Integer count = 1;
    Integer startIndex = 1;

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("testDisplayName");
    scimGroup.setId("id");

    UserGroup userGroup1 = UserGroup.builder().name(scimGroup.getDisplayName()).identifier(scimGroup.getId()).build();
    UserMetadataDTO userMetadataDTO =
        UserMetadataDTO.builder().name("testName").email("dummy@gmail.com").uuid("UUID").build();

    when(userGroupService.list(any(), any(), any())).thenReturn(new ArrayList<UserGroup>() {
      { add(userGroup1); }
    });

    when(userGroupService.getUsersInUserGroup(any(), any())).thenReturn(new ArrayList<UserMetadataDTO>() {
      { add(userMetadataDTO); }
    });

    ScimListResponse<ScimGroup> response = scimGroupService.searchGroup(null, accountId, count, startIndex);

    assertThat(response.getTotalResults()).isEqualTo(1);
    assertThat(response.getStartIndex()).isEqualTo(startIndex);
    assertThat(response.getItemsPerPage()).isEqualTo(count);
    ScimGroup scimGroup1 = response.getResources().get(0);
    Member member = scimGroup1.getMembers().get(0);
    assertNotNull(member);
    assertNotNull(member.getRef());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testSearchGroupByName_WithStartIndexAndCountAsNULL() {
    String accountId = "accountId";
    Integer count = null;
    Integer startIndex = null;

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("testDisplayName");
    scimGroup.setId("id");

    UserGroup userGroup1 = UserGroup.builder().name(scimGroup.getDisplayName()).identifier(scimGroup.getId()).build();

    when(userGroupService.list(any(), any(), any())).thenReturn(new ArrayList<UserGroup>() {
      { add(userGroup1); }
    });

    ScimListResponse<ScimGroup> response =
        scimGroupService.searchGroup("displayName eq \"testDisplayName\"", accountId, count, startIndex);

    assertThat(response.getTotalResults()).isEqualTo(1);
    assertThat(response.getStartIndex()).isEqualTo(0);
    assertThat(response.getItemsPerPage()).isEqualTo(MAX_RESULT_COUNT);
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

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testNoSearchQueryNoSkipNoCountReturnsDefaultResult() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("testDisplayName");
    scimGroup.setId("id");

    UserGroup userGroup1 = UserGroup.builder().name(scimGroup.getDisplayName()).identifier(scimGroup.getId()).build();

    when(userGroupService.list(any(), any(), any())).thenReturn(new ArrayList<>() {
      { add(userGroup1); }
    });

    ScimListResponse<ScimGroup> response = scimGroupService.searchGroup(null, accountId, null, null);

    assertThat(response.getTotalResults()).isEqualTo(1);
    assertThat(response.getStartIndex()).isEqualTo(0);
    assertThat(response.getItemsPerPage()).isEqualTo(20);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testNoSearchQueryNoSkipWithCountReturnsDefaultResult() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("testDisplayName");
    scimGroup.setId("id");

    UserGroup userGroup1 = UserGroup.builder().name(scimGroup.getDisplayName()).identifier(scimGroup.getId()).build();

    when(userGroupService.list(any(), any(), any())).thenReturn(new ArrayList<>() {
      { add(userGroup1); }
    });

    int startIdx = 5;
    ScimListResponse<ScimGroup> response = scimGroupService.searchGroup(null, accountId, startIdx, 0);

    assertThat(response.getTotalResults()).isEqualTo(1);
    assertThat(response.getStartIndex()).isEqualTo(0);
    assertThat(response.getItemsPerPage()).isEqualTo(startIdx);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testNoSkipNoCountNoSearchQueryReturnsDefaultResult() {
    String accountId = "accountId";

    UserGroup userGroup1 =
        UserGroup.builder().name("testDisplayName").identifier("testId").externallyManaged(false).build();

    when(userGroupService.list(any(), any(), any()))
        .thenReturn(
            new ArrayList<>()); // the above user group 'usergroup1' wont be returned as it is not externallyManaged

    ScimListResponse<ScimGroup> response = scimGroupService.searchGroup(null, accountId, null, null);

    assertThat(response.getTotalResults()).isEqualTo(0);
    assertThat(response.getStartIndex()).isEqualTo(0);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_createUserGroupForDash1() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("displayname");
    scimGroup.setId("id");

    String userGroupId = scimGroup.getDisplayName().replaceAll("-", "_");
    UserGroup userGroup = UserGroup.builder().name(scimGroup.getDisplayName()).identifier(userGroupId).build();
    when(userGroupService.create(any())).thenReturn(userGroup);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(scimGroup.getDisplayName());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_createUserGroupForDash2() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("display-name");
    scimGroup.setId("id");

    String userGroupId = scimGroup.getDisplayName().replaceAll("-", "_");
    UserGroup userGroup = UserGroup.builder().name(scimGroup.getDisplayName()).identifier(userGroupId).build();
    when(userGroupService.create(any())).thenReturn(userGroup);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(userGroupId);
    assertThat(userGroupCreated.getId()).isEqualTo("display_name");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_createUserGroupForDash3() {
    String accountId = "accountId";
    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("display_name");
    scimGroup.setId("id");

    String userGroupId = scimGroup.getDisplayName().replaceAll("-", "_");
    UserGroup userGroup = UserGroup.builder().name(scimGroup.getDisplayName()).identifier(userGroupId).build();
    when(userGroupService.create(any())).thenReturn(userGroup);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(userGroupId);
    assertThat(userGroupCreated.getId()).isEqualTo("display_name");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_createUserGroupForDash4() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("display-name");
    scimGroup.setId("id");

    when(userGroupService.create(any())).thenReturn(null);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNull();
    assertThat(userGroupCreated.getId()).isNull();
    assertThat(userGroupCreated.getMembers()).isNull();
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void test_createUserGroupForSpace1() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("displayname");
    scimGroup.setId("id");

    String userGroupId = scimGroup.getDisplayName().replaceAll(" ", "_");
    UserGroup userGroup = UserGroup.builder().name(scimGroup.getDisplayName()).identifier(userGroupId).build();
    when(userGroupService.create(any())).thenReturn(userGroup);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(scimGroup.getDisplayName());
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void test_createUserGroupForSpace2() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("display name");
    scimGroup.setId("id");

    String userGroupId = scimGroup.getDisplayName().replaceAll(" ", "_");
    UserGroup userGroup = UserGroup.builder().name(scimGroup.getDisplayName()).identifier(userGroupId).build();
    when(userGroupService.create(any())).thenReturn(userGroup);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(userGroupId);
    assertThat(userGroupCreated.getId()).isEqualTo("display_name");
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void test_createUserGroupForSpace3() {
    String accountId = "accountId";
    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("display_name");
    scimGroup.setId("id");

    String userGroupId = scimGroup.getDisplayName().replaceAll(" ", "_");
    UserGroup userGroup = UserGroup.builder().name(scimGroup.getDisplayName()).identifier(userGroupId).build();
    when(userGroupService.create(any())).thenReturn(userGroup);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(userGroupId);
    assertThat(userGroupCreated.getId()).isEqualTo("display_name");
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void test_createUserGroupForSpace4() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("display name");
    scimGroup.setId("id");

    when(userGroupService.create(any())).thenReturn(null);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNull();
    assertThat(userGroupCreated.getId()).isNull();
    assertThat(userGroupCreated.getMembers()).isNull();
  }
}