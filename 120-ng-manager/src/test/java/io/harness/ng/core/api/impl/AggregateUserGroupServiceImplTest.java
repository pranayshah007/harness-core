/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.SATHISH;
import static io.harness.utils.PageUtils.getPageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.resourcegroups.api.ResourceGroupDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentAggregateResponseDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentFilterDTO;
import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserGroupAggregateDTO;
import io.harness.ng.core.entities.NotificationSettingConfig;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.ng.core.usergroups.filter.UserGroupFilterType;
import io.harness.ng.core.utils.UserGroupMapper;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.PageImpl;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class AggregateUserGroupServiceImplTest extends CategoryTest {
  @Mock private UserGroupService userGroupService;
  @Mock private AccessControlAdminClient accessControlAdminClient;
  @Mock private NgUserService ngUserService;
  @Inject @InjectMocks private AggregateUserGroupServiceImpl aggregateUserGroupService;

  private static final String ACCOUNT_IDENTIFIER = "ACCOUNT_IDENTIFIER";
  private static final String ORG_IDENTIFIER = "ORG_IDENTIFIER";
  private static final String PROJECT_IDENTIFIER = "PROJECT_IDENTIFIER";

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testListAggregateUserGroups() throws IOException {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(2).build();
    List<NotificationSettingConfig> notificationConfigs = new ArrayList<>();
    List<String> users1 = Lists.newArrayList("u1", "u2", "u3", "u4", "u5", "u6", "u7");
    List<String> users2 = Lists.newArrayList("u3", "u4", "u5", "u6", "u7", "u8");
    List<String> users3 = Lists.newArrayList("u2");
    List<String> users4 = Lists.newArrayList();
    List<UserMetadataDTO> users =
        Lists.newArrayList(getUserMetadata("u1"), getUserMetadata("u2"), getUserMetadata("u3"), getUserMetadata("u4"),
            getUserMetadata("u5"), getUserMetadata("u6"), getUserMetadata("u7"), getUserMetadata("u8"));
    // normal usergroups
    UserGroup ug1 = UserGroup.builder()
                        .identifier("UG1")
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .users(users1)
                        .notificationConfigs(notificationConfigs)
                        .build();
    UserGroup ug2 = UserGroup.builder()
                        .identifier("UG2")
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .users(users2)
                        .notificationConfigs(notificationConfigs)
                        .build();
    // inherited usergroups
    UserGroup ug3 = UserGroup.builder()
                        .identifier("UG3")
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .users(users3)
                        .notificationConfigs(notificationConfigs)
                        .build();
    UserGroup ug4 = UserGroup.builder()
                        .identifier("UG4")
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .users(users4)
                        .notificationConfigs(notificationConfigs)
                        .build();
    List<UserGroup> userGroups = Lists.newArrayList(ug1, ug2, ug3, ug4);

    doReturn(new PageImpl<>(userGroups))
        .when(userGroupService)
        .list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null,
            UserGroupFilterType.EXCLUDE_INHERITED_GROUPS, getPageRequest(pageRequest));
    Set<PrincipalDTO> principalDTOSet =
        userGroups.stream()
            .map(userGroup
                -> PrincipalDTO.builder()
                       .identifier(userGroup.getIdentifier())
                       .type(USER_GROUP)
                       .scopeLevel(ScopeLevel
                                       .of(userGroup.getAccountIdentifier(), userGroup.getOrgIdentifier(),
                                           userGroup.getProjectIdentifier())
                                       .toString()
                                       .toLowerCase())
                       .build())
            .collect(Collectors.toSet());
    doReturn(users).when(ngUserService).getUserMetadata(anyList());

    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> request = mock(Call.class);
    doReturn(request)
        .when(accessControlAdminClient)
        .getAggregatedFilteredRoleAssignments(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
            RoleAssignmentFilterDTO.builder().principalFilter(principalDTOSet).build());
    doReturn(Response.success(ResponseDTO.newResponse(RoleAssignmentAggregateResponseDTO.builder()
                                                          .roles(new ArrayList<>())
                                                          .resourceGroups(new ArrayList<>())
                                                          .roleAssignments(new ArrayList<>())
                                                          .build())))
        .when(request)
        .execute();

    PageResponse<UserGroupAggregateDTO> response = aggregateUserGroupService.listAggregateUserGroups(pageRequest,
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null, 2, UserGroupFilterType.EXCLUDE_INHERITED_GROUPS);
    assertThat(response.getContent()).hasSize(4);

    assertThat(response.getContent().get(0).getUsers().size()).isEqualTo(2);
    assertThat(
        response.getContent().get(0).getUsers().stream().map(UserMetadataDTO::getUuid).collect(Collectors.toList()))
        .containsExactly("u7", "u6");
    assertThat(response.getContent().get(1).getUsers().size()).isEqualTo(2);
    assertThat(
        response.getContent().get(1).getUsers().stream().map(UserMetadataDTO::getUuid).collect(Collectors.toList()))
        .containsExactly("u8", "u7");
    assertThat(response.getContent().get(2).getUsers().size()).isEqualTo(1);
    assertThat(
        response.getContent().get(2).getUsers().stream().map(UserMetadataDTO::getUuid).collect(Collectors.toList()))
        .containsExactly("u2");
    assertThat(response.getContent().get(3).getUsers().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testListAggregateUserGroupsGetAllUsers() throws IOException {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(2).build();
    List<NotificationSettingConfig> notificationConfigs = new ArrayList<>();
    List<String> users1 = Lists.newArrayList("u1", "u2", "u3", "u4", "u5", "u6", "u7");
    List<UserMetadataDTO> users =
        Lists.newArrayList(getUserMetadata("u1"), getUserMetadata("u2"), getUserMetadata("u3"), getUserMetadata("u4"),
            getUserMetadata("u5"), getUserMetadata("u6"), getUserMetadata("u7"));
    UserGroup ug1 = UserGroup.builder()
                        .identifier("UG1")
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .users(users1)
                        .notificationConfigs(notificationConfigs)
                        .build();
    List<UserGroup> userGroups = Lists.newArrayList(ug1);

    doReturn(new PageImpl<>(userGroups))
        .when(userGroupService)
        .list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null,
            UserGroupFilterType.INCLUDE_INHERITED_GROUPS, getPageRequest(pageRequest));
    Set<PrincipalDTO> principalDTOSet =
        userGroups.stream()
            .map(userGroup
                -> PrincipalDTO.builder()
                       .identifier(userGroup.getIdentifier())
                       .type(USER_GROUP)
                       .scopeLevel(ScopeLevel
                                       .of(userGroup.getAccountIdentifier(), userGroup.getOrgIdentifier(),
                                           userGroup.getProjectIdentifier())
                                       .toString()
                                       .toLowerCase())
                       .build())
            .collect(Collectors.toSet());
    doReturn(users).when(ngUserService).getUserMetadata(anyList());

    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> request = mock(Call.class);
    doReturn(request)
        .when(accessControlAdminClient)
        .getAggregatedFilteredRoleAssignments(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
            RoleAssignmentFilterDTO.builder().principalFilter(principalDTOSet).build());
    doReturn(Response.success(ResponseDTO.newResponse(RoleAssignmentAggregateResponseDTO.builder()
                                                          .roles(new ArrayList<>())
                                                          .resourceGroups(new ArrayList<>())
                                                          .roleAssignments(new ArrayList<>())
                                                          .build())))
        .when(request)
        .execute();

    PageResponse<UserGroupAggregateDTO> response = aggregateUserGroupService.listAggregateUserGroups(pageRequest,
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null, -1, UserGroupFilterType.INCLUDE_INHERITED_GROUPS);
    assertThat(response.getContent()).hasSize(1);

    assertThat(response.getContent().get(0).getUsers().size()).isEqualTo(7);
    assertThat(
        response.getContent().get(0).getUsers().stream().map(UserMetadataDTO::getUuid).collect(Collectors.toList()))
        .contains("u1", "u2", "u3", "u4", "u5", "u6", "u7");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAggregateUserGroupsWithSameRoleAssignmentScope() throws IOException {
    List<NotificationSettingConfig> notificationConfigs = new ArrayList<>();
    List<String> users1 = Lists.newArrayList("u1", "u2", "u3", "u4", "u5", "u6", "u7");
    List<UserMetadataDTO> users =
        Lists.newArrayList(getUserMetadata("u1"), getUserMetadata("u2"), getUserMetadata("u3"), getUserMetadata("u4"),
            getUserMetadata("u5"), getUserMetadata("u6"), getUserMetadata("u7"));
    UserGroup ug1 = UserGroup.builder()
                        .identifier("UG1")
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .users(users1)
                        .notificationConfigs(notificationConfigs)
                        .build();

    doReturn(Optional.of(ug1))
        .when(userGroupService)
        .get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "UG1");
    PrincipalDTO principalDTO =
        PrincipalDTO.builder()
            .identifier("UG1")
            .type(USER_GROUP)
            .scopeLevel(ScopeLevel.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER).toString().toLowerCase())
            .build();
    doReturn(users).when(ngUserService).getUserMetadata(anyList());

    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> request = mock(Call.class);
    doReturn(request)
        .when(accessControlAdminClient)
        .getAggregatedFilteredRoleAssignments(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
            RoleAssignmentFilterDTO.builder().principalFilter(Collections.singleton(principalDTO)).build());
    doReturn(Response.success(ResponseDTO.newResponse(RoleAssignmentAggregateResponseDTO.builder()
                                                          .roles(new ArrayList<>())
                                                          .resourceGroups(new ArrayList<>())
                                                          .roleAssignments(new ArrayList<>())
                                                          .build())))
        .when(request)
        .execute();

    UserGroupAggregateDTO response =
        aggregateUserGroupService.getAggregatedUserGroup(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "UG1",
            ScopeDTO.builder()
                .accountIdentifier(ACCOUNT_IDENTIFIER)
                .orgIdentifier(ORG_IDENTIFIER)
                .projectIdentifier(PROJECT_IDENTIFIER)
                .build());

    assertThat(response.getUserGroupDTO()).isEqualTo(UserGroupMapper.toDTO(ug1));
    assertThat(response.getRoleAssignmentsMetadataDTO()).isEqualTo(null);
    assertThat(response.getUsers()).isEqualTo(users);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAggregateUserGroupsWithDifferentRoleAssignmentScope() throws IOException {
    // get account level usergroup with project level role assignments
    // roleassignmentDTO is child of usergroup scope is checked in resource layer
    List<NotificationSettingConfig> notificationConfigs = new ArrayList<>();
    List<String> users1 = Lists.newArrayList("u1", "u2", "u3", "u4", "u5", "u6", "u7");
    List<UserMetadataDTO> users =
        Lists.newArrayList(getUserMetadata("u1"), getUserMetadata("u2"), getUserMetadata("u3"), getUserMetadata("u4"),
            getUserMetadata("u5"), getUserMetadata("u6"), getUserMetadata("u7"));
    UserGroup ug1 = UserGroup.builder()
                        .identifier("UG1")
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .users(users1)
                        .notificationConfigs(notificationConfigs)
                        .build();

    doReturn(Optional.of(ug1)).when(userGroupService).get(ACCOUNT_IDENTIFIER, null, null, "UG1");
    PrincipalDTO principalDTO = PrincipalDTO.builder()
                                    .identifier("UG1")
                                    .type(USER_GROUP)
                                    .scopeLevel(ScopeLevel.of(ACCOUNT_IDENTIFIER, null, null).toString().toLowerCase())
                                    .build();
    doReturn(users).when(ngUserService).getUserMetadata(anyList());

    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> request = mock(Call.class);
    doReturn(request)
        .when(accessControlAdminClient)
        .getAggregatedFilteredRoleAssignments(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
            RoleAssignmentFilterDTO.builder().principalFilter(Collections.singleton(principalDTO)).build());
    ScopeDTO roleAssignmentScopeDTO = ScopeDTO.builder()
                                          .accountIdentifier(ACCOUNT_IDENTIFIER)
                                          .orgIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJECT_IDENTIFIER)
                                          .build();
    doReturn(Response.success(ResponseDTO.newResponse(
                 RoleAssignmentAggregateResponseDTO.builder()
                     .roles(new ArrayList<>(
                         Collections.singleton(RoleResponseDTO.builder()
                                                   .harnessManaged(false)
                                                   .role(RoleDTO.builder().identifier("ROLE1").name("ROLE1").build())
                                                   .build())))
                     .resourceGroups(new ArrayList<>(
                         Collections.singleton(ResourceGroupDTO.builder().identifier("RG1").name("RG1").build())))
                     .roleAssignments(new ArrayList<>(Collections.singleton(RoleAssignmentDTO.builder()
                                                                                .identifier("RA1")
                                                                                .roleIdentifier("ROLE1")
                                                                                .resourceGroupIdentifier("RG1")
                                                                                .disabled(false)
                                                                                .managed(false)
                                                                                .principal(principalDTO)
                                                                                .build())))
                     .scope(roleAssignmentScopeDTO)
                     .build())))
        .when(request)
        .execute();

    UserGroupAggregateDTO response =
        aggregateUserGroupService.getAggregatedUserGroup(ACCOUNT_IDENTIFIER, null, null, "UG1", roleAssignmentScopeDTO);

    assertThat(response.getUserGroupDTO()).isEqualTo(UserGroupMapper.toDTO(ug1));
    assertThat(response.getRoleAssignmentsMetadataDTO().size()).isEqualTo(1);
    assertThat(response.getRoleAssignmentsMetadataDTO().get(0).getIdentifier()).isEqualTo("RA1");
    assertThat(response.getRoleAssignmentsMetadataDTO().get(0).getRoleIdentifier()).isEqualTo("ROLE1");
    assertThat(response.getRoleAssignmentsMetadataDTO().get(0).getResourceGroupIdentifier()).isEqualTo("RG1");
    assertThat(response.getRoleAssignmentsMetadataDTO().get(0).isManagedRole()).isEqualTo(false);
    assertThat(response.getUsers()).isEqualTo(users);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAggregateUserGroupsWhenUsergroupDNE() {
    // get account level usergroup with project level role assignments
    // roleassignmentDTO is child of usergroup scope is checked in resource layer
    doReturn(Optional.empty())
        .when(userGroupService)
        .get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "UG1");

    ScopeDTO roleAssignmentScopeDTO = ScopeDTO.builder()
                                          .accountIdentifier(ACCOUNT_IDENTIFIER)
                                          .orgIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJECT_IDENTIFIER)
                                          .build();

    try {
      aggregateUserGroupService.getAggregatedUserGroup(
          ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "UG1", roleAssignmentScopeDTO);
      fail("Expected failure as usergroup does not exist");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message"))
          .isEqualTo(String.format("User Group is not available %s:%s:%s:%s", ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
              PROJECT_IDENTIFIER, "UG1"));
    }
  }

  private static UserMetadataDTO getUserMetadata(String user) {
    return UserMetadataDTO.builder().name(user).email(user).uuid(user).build();
  }
}
