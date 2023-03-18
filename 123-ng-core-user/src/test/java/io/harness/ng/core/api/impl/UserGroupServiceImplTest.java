/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.accesscontrol.acl.api.AccessControlDTO.AccessControlDTOBuilder;
import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_USERGROUP_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.USERGROUP;
import static io.harness.ng.core.user.entities.UserGroup.UserGroupBuilder;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.KARAN;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.REETIKA;
import static io.harness.utils.PageTestUtils.getPage;
import static io.harness.utils.PageUtils.getPageRequest;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.resourcegroups.api.ResourceGroupDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentAggregateResponseDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentFilterDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResponseDTO;
import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.accesscontrol.scopes.ScopeNameDTO;
import io.harness.ng.accesscontrol.scopes.ScopeNameMapper;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.entities.UserGroup.UserGroupKeys;
import io.harness.ng.core.user.service.LastAdminCheckService;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.ng.core.usergroups.filter.UserGroupFilterType;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ng.core.spring.UserGroupRepository;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class UserGroupServiceImplTest extends CategoryTest {
  @Mock private UserGroupRepository userGroupRepository;
  @Mock private OutboxService outboxService;
  @Mock private AccessControlAdminClient accessControlAdminClient;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private NgUserService ngUserService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private ScopeNameMapper scopeNameMapper;
  @Mock private LastAdminCheckService lastAdminCheckService;
  @Spy @Inject @InjectMocks private UserGroupServiceImpl userGroupService;

  private static final String ACCOUNT_IDENTIFIER = "A1";
  private static final String ORG_IDENTIFIER = "O1";
  private static final String ORG2_IDENTIFIER = "O2";
  private static final String PROJECT_IDENTIFIER = "P1";
  List<UserGroup> userGroupList = new ArrayList<>();
  List<UserGroup> permittedUserGroups = new ArrayList<>();
  List<AccessControlDTO> accessControlDTOS = new ArrayList<>();
  @Before
  public void setup() {
    initMocks(this);
    UserGroupBuilder userGroupBuilder = UserGroup.builder()
                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                            .orgIdentifier(ORG_IDENTIFIER)
                                            .projectIdentifier(PROJECT_IDENTIFIER);

    userGroupList.add(userGroupBuilder.identifier("UG1").build());
    userGroupList.add(userGroupBuilder.identifier("UG2").build());
    userGroupList.add(userGroupBuilder.identifier("UG3").build());

    permittedUserGroups.add(userGroupBuilder.identifier("UG1").build());
    permittedUserGroups.add(userGroupBuilder.identifier("UG2").build());

    AccessControlDTOBuilder accessControlDTOBuilder = AccessControlDTO.builder()
                                                          .resourceType(USERGROUP)
                                                          .permission("core_usergroup_view")
                                                          .resourceScope(ResourceScope.builder()
                                                                             .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                                             .orgIdentifier(ORG_IDENTIFIER)
                                                                             .projectIdentifier(PROJECT_IDENTIFIER)
                                                                             .build());

    accessControlDTOS.add(accessControlDTOBuilder.permitted(true).resourceIdentifier("UG1").build());
    accessControlDTOS.add(accessControlDTOBuilder.permitted(true).resourceIdentifier("UG2").build());
    accessControlDTOS.add(accessControlDTOBuilder.permitted(false).resourceIdentifier("UG3").build());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testRemoveMemberAll() throws IOException {
    String userIdentifier = "u1";
    int randomNum = ThreadLocalRandom.current().nextInt(5, 10);
    List<UserGroup> userGroups = new ArrayList<>();
    while (randomNum > 0) {
      userGroups.add(UserGroup.builder().identifier("UG" + randomNum).build());
      randomNum--;
    }
    ArgumentCaptor<Criteria> captor = ArgumentCaptor.forClass(Criteria.class);
    doReturn(userGroups).when(userGroupRepository).findAll(captor.capture(), any(), any());
    doReturn(Optional.of(UserGroup.builder()
                             .identifier("UG")
                             .users(new ArrayList<>())
                             .notificationConfigs(new ArrayList<>())
                             .build()))
        .when(userGroupRepository)
        .find(any());

    userGroupService.removeMemberAll(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, userIdentifier);
    verify(transactionTemplate, times(userGroups.size())).execute(any());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testAddMember() throws IOException {
    String userGroupIdentifier = "UG";
    List<String> users = new ArrayList<>();
    doReturn(Optional.of(UserGroup.builder()
                             .identifier(userGroupIdentifier)
                             .users(users)
                             .notificationConfigs(new ArrayList<>())
                             .build()))
        .when(userGroupRepository)
        .find(any());
    doReturn(Arrays.asList("u1", "u2")).when(ngUserService).listUserIds(any());

    userGroupService.addMember(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, userGroupIdentifier, "u1");
    assertThat(users.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testListUserGroups() throws IOException {
    String searchTerm = randomAlphabetic(5);
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));
    userGroupService.list(UserGroupFilterDTO.builder()
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJECT_IDENTIFIER)
                              .searchTerm(searchTerm)
                              .identifierFilter(new HashSet<>(Arrays.asList("UG1", "UG2")))
                              .build());
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testAddUserToUserGroups() {
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    String userId = "userId";
    List<String> userGroups = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      String userGroupId = randomAlphabetic(10);
      userGroups.add(userGroupId);
      doReturn(i % 2 == 0)
          .when(userGroupService)
          .checkMember(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, userGroupId, userId);
      if (i % 2 == 1) {
        doReturn(UserGroup.builder().identifier(userGroupId).build())
            .when(userGroupService)
            .addMember(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, userGroupId, userId);
      }
    }
    userGroupService.addUserToUserGroups(scope, userId, userGroups);
    for (int i = 0; i < 5; i++) {
      String userGroupId = userGroups.get(i);
      verify(userGroupService, times(i % 2))
          .addMember(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, userGroupId, userId);
    }
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testThatCantbeUpdatedInExternallyManagedGroup_1() {
    // Case when users are removed
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    String userGroupIdentifier = randomAlphabetic(10);
    UserGroup userGroup = UserGroup.builder()
                              .accountIdentifier(scope.getAccountIdentifier())
                              .orgIdentifier(scope.getOrgIdentifier())
                              .projectIdentifier(scope.getProjectIdentifier())
                              .identifier(userGroupIdentifier)
                              .externallyManaged(true)
                              .isSsoLinked(true)
                              .users(Lists.newArrayList("abc", "def", "ok"))
                              .build();
    doReturn(Optional.of(userGroup))
        .when(userGroupService)
        .get(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), userGroupIdentifier);
    when(ngUserService.listUserIds(scope)).thenReturn(Lists.newArrayList("abc", "def", "ok"));
    UserGroupDTO updatedUserGroupDTO = UserGroupDTO.builder()
                                           .accountIdentifier(scope.getAccountIdentifier())
                                           .orgIdentifier(scope.getOrgIdentifier())
                                           .projectIdentifier(scope.getProjectIdentifier())
                                           .identifier(userGroupIdentifier)
                                           .externallyManaged(true)
                                           .isSsoLinked(true)
                                           .users(Lists.newArrayList("abc", "def"))
                                           .build();
    when(userGroupRepository.save(userGroup)).thenReturn(userGroup);
    assertThatThrownBy(() -> userGroupService.updateWithCheckThatSCIMFieldsAreNotModified(updatedUserGroupDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Update is not supported for externally managed group " + userGroupIdentifier);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testThatGroupCantbeUpdatedInExternallyManagedGroup_2() {
    // Case when users are added
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    String userGroupIdentifier = randomAlphabetic(10);
    UserGroup userGroup = UserGroup.builder()
                              .accountIdentifier(scope.getAccountIdentifier())
                              .orgIdentifier(scope.getOrgIdentifier())
                              .projectIdentifier(scope.getProjectIdentifier())
                              .identifier(userGroupIdentifier)
                              .externallyManaged(true)
                              .isSsoLinked(true)
                              .users(Lists.newArrayList("abc", "def", "ok"))
                              .build();
    doReturn(Optional.of(userGroup))
        .when(userGroupService)
        .get(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), userGroupIdentifier);
    when(ngUserService.listUserIds(scope)).thenReturn(Lists.newArrayList("abc", "def", "ok"));
    UserGroupDTO updatedUserGroupDTO = UserGroupDTO.builder()
                                           .accountIdentifier(scope.getAccountIdentifier())
                                           .orgIdentifier(scope.getOrgIdentifier())
                                           .projectIdentifier(scope.getProjectIdentifier())
                                           .identifier(userGroupIdentifier)
                                           .externallyManaged(true)
                                           .isSsoLinked(true)
                                           .users(Lists.newArrayList("abc", "def", "ok", "kk"))
                                           .build();
    when(userGroupRepository.save(userGroup)).thenReturn(userGroup);
    assertThatThrownBy(() -> userGroupService.updateWithCheckThatSCIMFieldsAreNotModified(updatedUserGroupDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Update is not supported for externally managed group " + userGroupIdentifier);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testThatUserGroupsCanUpdatedInExternallyManagedGroup_3() {
    // Case when users are added
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    String userGroupIdentifier = randomAlphabetic(10);
    UserGroup userGroup = UserGroup.builder()
                              .accountIdentifier(scope.getAccountIdentifier())
                              .orgIdentifier(scope.getOrgIdentifier())
                              .projectIdentifier(scope.getProjectIdentifier())
                              .identifier(userGroupIdentifier)
                              .name("name")
                              .externallyManaged(true)
                              .isSsoLinked(true)
                              .users(Lists.newArrayList("abc", "def", "ok"))
                              .build();
    doReturn(userGroup).when(transactionTemplate).execute(any());
    doReturn(Optional.of(userGroup))
        .when(userGroupService)
        .get(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), userGroupIdentifier);
    when(ngUserService.listUserIds(scope)).thenReturn(Lists.newArrayList("abc", "def", "ok"));
    UserGroupDTO updatedUserGroupDTO = UserGroupDTO.builder()
                                           .accountIdentifier(scope.getAccountIdentifier())
                                           .orgIdentifier(scope.getOrgIdentifier())
                                           .projectIdentifier(scope.getProjectIdentifier())
                                           .identifier(userGroupIdentifier)
                                           .externallyManaged(true)
                                           .name("name")
                                           .isSsoLinked(true)
                                           .users(Lists.newArrayList("abc", "def", "ok"))
                                           .build();
    when(userGroupRepository.save(userGroup)).thenReturn(userGroup);
    UserGroup updatedUserGroup = userGroupService.updateWithCheckThatSCIMFieldsAreNotModified(updatedUserGroupDTO);
    assertThat(updatedUserGroup).isNotNull();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testThatUserGroupsCanUpdatedInExternallyManagedGroup_4() {
    // Case when users are added
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    String userGroupIdentifier = randomAlphabetic(10);
    UserGroup userGroup = UserGroup.builder()
                              .accountIdentifier(scope.getAccountIdentifier())
                              .orgIdentifier(scope.getOrgIdentifier())
                              .projectIdentifier(scope.getProjectIdentifier())
                              .identifier(userGroupIdentifier)
                              .externallyManaged(true)
                              .isSsoLinked(true)
                              .name("name")
                              .users(Lists.newArrayList("abc", "def", "ok"))
                              .build();
    doReturn(userGroup).when(transactionTemplate).execute(any());
    doReturn(Optional.of(userGroup))
        .when(userGroupService)
        .get(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), userGroupIdentifier);
    when(ngUserService.listUserIds(scope)).thenReturn(Lists.newArrayList("abc", "def", "ok"));
    UserGroupDTO updatedUserGroupDTO = UserGroupDTO.builder()
                                           .accountIdentifier(scope.getAccountIdentifier())
                                           .orgIdentifier(scope.getOrgIdentifier())
                                           .projectIdentifier(scope.getProjectIdentifier())
                                           .identifier(userGroupIdentifier)
                                           .externallyManaged(true)
                                           .isSsoLinked(true)
                                           .name("updated-name")
                                           .users(Lists.newArrayList("abc", "def", "ok"))
                                           .build();
    when(userGroupRepository.save(userGroup)).thenReturn(userGroup);
    assertThatThrownBy(() -> userGroupService.updateWithCheckThatSCIMFieldsAreNotModified(updatedUserGroupDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("The name cannot be updated for externally managed group");
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testSanitize() {
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    String userGroupIdentifier = randomAlphabetic(10);
    String validUser = randomAlphabetic(11);
    String invalidUser = randomAlphabetic(12);
    UserGroup userGroup = UserGroup.builder()
                              .accountIdentifier(scope.getAccountIdentifier())
                              .orgIdentifier(scope.getOrgIdentifier())
                              .projectIdentifier(scope.getProjectIdentifier())
                              .identifier(userGroupIdentifier)
                              .users(Lists.newArrayList(validUser, invalidUser))
                              .build();
    doReturn(Optional.of(userGroup))
        .when(userGroupService)
        .get(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), userGroupIdentifier);
    when(ngUserService.listUserIds(scope)).thenReturn(Lists.newArrayList(validUser));
    UserGroup updatedUserGroup = UserGroup.builder()
                                     .accountIdentifier(scope.getAccountIdentifier())
                                     .orgIdentifier(scope.getOrgIdentifier())
                                     .projectIdentifier(scope.getProjectIdentifier())
                                     .identifier(userGroupIdentifier)
                                     .users(Lists.newArrayList(validUser))
                                     .build();
    when(userGroupRepository.save(updatedUserGroup)).thenReturn(updatedUserGroup);

    userGroupService.sanitize(scope, userGroupIdentifier);

    verify(userGroupService, times(1))
        .get(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), userGroupIdentifier);
    verify(ngUserService, times(1)).listUserIds(scope);
    verify(userGroupRepository, times(1)).save(updatedUserGroup);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testListUserGroups_withViewPermissionOnAllUserGroups() {
    when(accessControlClient.hasAccess(ResourceScope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER),
             Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION))
        .thenReturn(true);
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    final Pageable page = getPageRequest(pageRequest);
    String searchTerm = randomAlphabetic(5);
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    userGroupService.list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, searchTerm,
        UserGroupFilterType.INCLUDE_INHERITED_GROUPS, page);
    verify(userGroupRepository, times(0)).findAll(userGroupCriteriaArgumentCaptor.capture(), eq(Pageable.unpaged()));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), eq(page));
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testListUserGroups_withViewPermissionOnSelectedUserGroups() {
    Criteria criteria = new Criteria();
    String searchTerm = randomAlphabetic(5);
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    final Pageable page = getPageRequest(pageRequest);

    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .principal(Principal.builder().principalIdentifier("id").principalType(USER).build())
            .accessControlList(accessControlDTOS)
            .build();

    doReturn(criteria)
        .when(userGroupService)
        .createUserGroupFilterCriteria(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, searchTerm,
            UserGroupFilterType.INCLUDE_INHERITED_GROUPS);
    when(accessControlClient.hasAccess(ResourceScope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER),
             Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION))
        .thenReturn(false);
    final Page<UserGroup> allPages = PageUtils.getPage(userGroupList, 0, 10);
    when(userGroupRepository.findAll(criteria, Pageable.unpaged())).thenReturn(allPages);
    when(accessControlClient.checkForAccessOrThrow(any())).thenReturn(accessCheckResponseDTO);

    userGroupService.list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, searchTerm,
        UserGroupFilterType.INCLUDE_INHERITED_GROUPS, page);

    verify(userGroupRepository, times(1)).findAll(criteria, Pageable.unpaged());
    criteria.and(UserGroupKeys.identifier)
        .in(permittedUserGroups.stream().map(UserGroup::getIdentifier).collect(Collectors.toList()));
    verify(userGroupRepository, times(1)).findAll(criteria, page);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testListUserGroupsBatch() {
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    String searchTerm = randomAlphabetic(5);

    final Page<UserGroup> allPages = PageUtils.getPage(userGroupList, 0, 100);
    when(userGroupRepository.findAll(userGroupCriteriaArgumentCaptor.capture(), eq(Pageable.unpaged())))
        .thenReturn(allPages);

    List<UserGroup> resultUserGroups = userGroupService.list(UserGroupFilterDTO.builder()
                                                                 .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                                 .orgIdentifier(ORG_IDENTIFIER)
                                                                 .projectIdentifier(PROJECT_IDENTIFIER)
                                                                 .searchTerm(searchTerm)
                                                                 .build());

    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), eq(Pageable.unpaged()));
    assertThat(resultUserGroups.stream().map(UserGroup::getIdentifier).collect(Collectors.toList()))
        .isEqualTo(userGroupList.stream().map(UserGroup::getIdentifier).collect(Collectors.toList()));
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testListUserGroupsBasic() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    String searchTerm = randomAlphabetic(5);
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));
    userGroupService.list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, searchTerm,
        UserGroupFilterType.INCLUDE_INHERITED_GROUPS, getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenAccountAndExclude() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));
    userGroupService.list(ACCOUNT_IDENTIFIER, null, null, null, UserGroupFilterType.EXCLUDE_INHERITED_GROUPS,
        getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria = new Criteria()
                                    .and(UserGroupKeys.accountIdentifier)
                                    .is(ACCOUNT_IDENTIFIER)
                                    .and(UserGroupKeys.orgIdentifier)
                                    .is(null)
                                    .and(UserGroupKeys.projectIdentifier)
                                    .is(null);

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenAccountAndExcludeAndSearch() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    String searchTerm = randomAlphabetic(5);
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));
    userGroupService.list(ACCOUNT_IDENTIFIER, null, null, searchTerm, UserGroupFilterType.EXCLUDE_INHERITED_GROUPS,
        getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria =
        new Criteria()
            .and(UserGroupKeys.accountIdentifier)
            .is(ACCOUNT_IDENTIFIER)
            .and(UserGroupKeys.orgIdentifier)
            .is(null)
            .and(UserGroupKeys.projectIdentifier)
            .is(null)
            .andOperator(new Criteria().orOperator(Criteria.where(UserGroupKeys.name).regex(searchTerm, "i"),
                Criteria.where(UserGroupKeys.tags).regex(searchTerm, "i")));

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenOrgAndExclude() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));
    userGroupService.list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null, null, UserGroupFilterType.EXCLUDE_INHERITED_GROUPS,
        getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria = new Criteria()
                                    .and(UserGroupKeys.accountIdentifier)
                                    .is(ACCOUNT_IDENTIFIER)
                                    .and(UserGroupKeys.orgIdentifier)
                                    .is(ORG_IDENTIFIER)
                                    .and(UserGroupKeys.projectIdentifier)
                                    .is(null);

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenOrgAndExcludeAndSearch() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    String searchTerm = randomAlphabetic(5);
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));
    userGroupService.list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null, searchTerm,
        UserGroupFilterType.EXCLUDE_INHERITED_GROUPS, getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria =
        new Criteria()
            .and(UserGroupKeys.accountIdentifier)
            .is(ACCOUNT_IDENTIFIER)
            .and(UserGroupKeys.orgIdentifier)
            .is(ORG_IDENTIFIER)
            .and(UserGroupKeys.projectIdentifier)
            .is(null)
            .andOperator(new Criteria().orOperator(Criteria.where(UserGroupKeys.name).regex(searchTerm, "i"),
                Criteria.where(UserGroupKeys.tags).regex(searchTerm, "i")));

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenProjectAndExclude() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));
    userGroupService.list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null,
        UserGroupFilterType.EXCLUDE_INHERITED_GROUPS, getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria = new Criteria()
                                    .and(UserGroupKeys.accountIdentifier)
                                    .is(ACCOUNT_IDENTIFIER)
                                    .and(UserGroupKeys.orgIdentifier)
                                    .is(ORG_IDENTIFIER)
                                    .and(UserGroupKeys.projectIdentifier)
                                    .is(PROJECT_IDENTIFIER);

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenProjectAndExcludeAndSearch() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    String searchTerm = randomAlphabetic(5);
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));
    userGroupService.list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, searchTerm,
        UserGroupFilterType.EXCLUDE_INHERITED_GROUPS, getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria =
        new Criteria()
            .and(UserGroupKeys.accountIdentifier)
            .is(ACCOUNT_IDENTIFIER)
            .and(UserGroupKeys.orgIdentifier)
            .is(ORG_IDENTIFIER)
            .and(UserGroupKeys.projectIdentifier)
            .is(PROJECT_IDENTIFIER)
            .andOperator(new Criteria().orOperator(Criteria.where(UserGroupKeys.name).regex(searchTerm, "i"),
                Criteria.where(UserGroupKeys.tags).regex(searchTerm, "i")));

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenAccountAndIncludeChildScopeUserGroup() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));
    userGroupService.list(ACCOUNT_IDENTIFIER, null, null, null, UserGroupFilterType.INCLUDE_CHILD_SCOPE_GROUPS,
        getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria = new Criteria().and(UserGroupKeys.accountIdentifier).is(ACCOUNT_IDENTIFIER);

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenAccountAndIncludeChildScopeUserGroupAndSearch() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    String searchTerm = randomAlphabetic(5);
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));
    userGroupService.list(ACCOUNT_IDENTIFIER, null, null, searchTerm, UserGroupFilterType.INCLUDE_CHILD_SCOPE_GROUPS,
        getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria =
        new Criteria()
            .and(UserGroupKeys.accountIdentifier)
            .is(ACCOUNT_IDENTIFIER)
            .andOperator(new Criteria().orOperator(Criteria.where(UserGroupKeys.name).regex(searchTerm, "i"),
                Criteria.where(UserGroupKeys.tags).regex(searchTerm, "i")));

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenOrgAndIncludeChildScopeUserGroup() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));
    userGroupService.list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null, null,
        UserGroupFilterType.INCLUDE_CHILD_SCOPE_GROUPS, getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria = new Criteria()
                                    .and(UserGroupKeys.accountIdentifier)
                                    .is(ACCOUNT_IDENTIFIER)
                                    .and(UserGroupKeys.orgIdentifier)
                                    .is(ORG_IDENTIFIER);

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenOrgAndIncludeChildScopeUserGroupAndSearch() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    String searchTerm = randomAlphabetic(5);
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));
    userGroupService.list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null, searchTerm,
        UserGroupFilterType.INCLUDE_CHILD_SCOPE_GROUPS, getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria =
        new Criteria()
            .and(UserGroupKeys.accountIdentifier)
            .is(ACCOUNT_IDENTIFIER)
            .and(UserGroupKeys.orgIdentifier)
            .is(ORG_IDENTIFIER)
            .andOperator(new Criteria().orOperator(Criteria.where(UserGroupKeys.name).regex(searchTerm, "i"),
                Criteria.where(UserGroupKeys.tags).regex(searchTerm, "i")));

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenProjectAndIncludeChildScopeUserGroup() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));
    userGroupService.list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null,
        UserGroupFilterType.INCLUDE_CHILD_SCOPE_GROUPS, getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria = new Criteria()
                                    .and(UserGroupKeys.accountIdentifier)
                                    .is(ACCOUNT_IDENTIFIER)
                                    .and(UserGroupKeys.orgIdentifier)
                                    .is(ORG_IDENTIFIER)
                                    .and(UserGroupKeys.projectIdentifier)
                                    .is(PROJECT_IDENTIFIER);

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenProjectAndIncludeChildScopeUserGroupAndSearch() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    String searchTerm = randomAlphabetic(5);
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));
    userGroupService.list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, searchTerm,
        UserGroupFilterType.INCLUDE_CHILD_SCOPE_GROUPS, getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria =
        new Criteria()
            .and(UserGroupKeys.accountIdentifier)
            .is(ACCOUNT_IDENTIFIER)
            .and(UserGroupKeys.orgIdentifier)
            .is(ORG_IDENTIFIER)
            .and(UserGroupKeys.projectIdentifier)
            .is(PROJECT_IDENTIFIER)
            .andOperator(new Criteria().orOperator(Criteria.where(UserGroupKeys.name).regex(searchTerm, "i"),
                Criteria.where(UserGroupKeys.tags).regex(searchTerm, "i")));

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenAccountAndInclude() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));
    userGroupService.list(ACCOUNT_IDENTIFIER, null, null, null, UserGroupFilterType.INCLUDE_INHERITED_GROUPS,
        getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria = new Criteria()
                                    .and(UserGroupKeys.accountIdentifier)
                                    .is(ACCOUNT_IDENTIFIER)
                                    .and(UserGroupKeys.orgIdentifier)
                                    .is(null)
                                    .and(UserGroupKeys.projectIdentifier)
                                    .is(null);

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenAccountAndIncludeAndSearch() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    String searchTerm = randomAlphabetic(5);
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));
    userGroupService.list(ACCOUNT_IDENTIFIER, null, null, searchTerm, UserGroupFilterType.INCLUDE_INHERITED_GROUPS,
        getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria =
        new Criteria()
            .and(UserGroupKeys.accountIdentifier)
            .is(ACCOUNT_IDENTIFIER)
            .and(UserGroupKeys.orgIdentifier)
            .is(null)
            .and(UserGroupKeys.projectIdentifier)
            .is(null)
            .andOperator(new Criteria().orOperator(Criteria.where(UserGroupKeys.name).regex(searchTerm, "i"),
                Criteria.where(UserGroupKeys.tags).regex(searchTerm, "i")));

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenOrgAndIncludeAndAccountAccessAndSearchAndInheritedExists() throws IOException {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    String searchTerm = randomAlphabetic(5);
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(accessControlClient.hasAccess(
             ResourceScope.of(ACCOUNT_IDENTIFIER, null, null), Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION))
        .thenReturn(true);
    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));

    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> request = mock(Call.class);
    doReturn(request)
        .when(accessControlAdminClient)
        .getAggregatedFilteredRoleAssignments(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null,
            RoleAssignmentFilterDTO.builder()
                .principalTypeFilter(Collections.singleton(USER_GROUP))
                .principalScopeLevelFilter(
                    Collections.singleton(ScopeLevel.of(ACCOUNT_IDENTIFIER, null, null).toString().toLowerCase()))
                .build());
    ScopeDTO roleAssignmentScopeDTO =
        ScopeDTO.builder().accountIdentifier(ACCOUNT_IDENTIFIER).orgIdentifier(ORG_IDENTIFIER).build();
    doReturn(
        Response.success(ResponseDTO.newResponse(
            RoleAssignmentAggregateResponseDTO.builder()
                .roles(new ArrayList<>(
                    Collections.singleton(RoleResponseDTO.builder()
                                              .harnessManaged(false)
                                              .role(RoleDTO.builder().identifier("ROLE1").name("ROLE1").build())
                                              .build())))
                .resourceGroups(new ArrayList<>(
                    Collections.singleton(ResourceGroupDTO.builder().identifier("RG1").name("RG1").build())))
                .roleAssignments(new ArrayList<>(Collections.singleton(
                    RoleAssignmentDTO.builder()
                        .identifier("RA1")
                        .roleIdentifier("ROLE1")
                        .resourceGroupIdentifier("RG1")
                        .disabled(false)
                        .managed(false)
                        .principal(
                            PrincipalDTO.builder().identifier("IHUG1").type(USER_GROUP).scopeLevel("account").build())
                        .build())))
                .scope(roleAssignmentScopeDTO)
                .build())))
        .when(request)
        .execute();

    userGroupService.list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null, searchTerm,
        UserGroupFilterType.INCLUDE_INHERITED_GROUPS, getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria =
        new Criteria()
            .orOperator(new Criteria().orOperator(Criteria.where(UserGroupKeys.identifier)
                                                      .is("IHUG1")
                                                      .andOperator(new Criteria()
                                                                       .is(ACCOUNT_IDENTIFIER)
                                                                       .and(UserGroupKeys.orgIdentifier)
                                                                       .exists(false)
                                                                       .and(UserGroupKeys.projectIdentifier)
                                                                       .exists(false))),
                new Criteria()
                    .and(UserGroupKeys.accountIdentifier)
                    .is(ACCOUNT_IDENTIFIER)
                    .and(UserGroupKeys.orgIdentifier)
                    .is(ORG_IDENTIFIER)
                    .and(UserGroupKeys.projectIdentifier)
                    .is(null))

            .andOperator(new Criteria().orOperator(Criteria.where(UserGroupKeys.name).regex(searchTerm, "i"),
                Criteria.where(UserGroupKeys.tags).regex(searchTerm, "i")));

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenOrgAndIncludeAndAccountAccessAndSearchAndNoInheritedExists() throws IOException {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    String searchTerm = randomAlphabetic(5);
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(accessControlClient.hasAccess(
             ResourceScope.of(ACCOUNT_IDENTIFIER, null, null), Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION))
        .thenReturn(true);
    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));

    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> request = mock(Call.class);
    doReturn(request)
        .when(accessControlAdminClient)
        .getAggregatedFilteredRoleAssignments(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null,
            RoleAssignmentFilterDTO.builder()
                .principalTypeFilter(Collections.singleton(USER_GROUP))
                .principalScopeLevelFilter(
                    Collections.singleton(ScopeLevel.of(ACCOUNT_IDENTIFIER, null, null).toString().toLowerCase()))
                .build());
    ScopeDTO roleAssignmentScopeDTO =
        ScopeDTO.builder().accountIdentifier(ACCOUNT_IDENTIFIER).orgIdentifier(ORG_IDENTIFIER).build();
    doReturn(Response.success(ResponseDTO.newResponse(RoleAssignmentAggregateResponseDTO.builder()
                                                          .roles(new ArrayList<>())
                                                          .resourceGroups(new ArrayList<>())
                                                          .roleAssignments(new ArrayList<>())
                                                          .scope(roleAssignmentScopeDTO)
                                                          .build())))
        .when(request)
        .execute();

    userGroupService.list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null, searchTerm,
        UserGroupFilterType.INCLUDE_INHERITED_GROUPS, getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria =
        new Criteria()
            .and(UserGroupKeys.accountIdentifier)
            .is(ACCOUNT_IDENTIFIER)
            .and(UserGroupKeys.orgIdentifier)
            .is(ORG_IDENTIFIER)
            .and(UserGroupKeys.projectIdentifier)
            .is(null)

            .andOperator(new Criteria().orOperator(Criteria.where(UserGroupKeys.name).regex(searchTerm, "i"),
                Criteria.where(UserGroupKeys.tags).regex(searchTerm, "i")));

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenOrgAndIncludeAndNoAccountAccessAndSearch() throws IOException {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    String searchTerm = randomAlphabetic(5);
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(accessControlClient.hasAccess(
             ResourceScope.of(ACCOUNT_IDENTIFIER, null, null), Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION))
        .thenReturn(false);
    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));

    userGroupService.list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null, searchTerm,
        UserGroupFilterType.INCLUDE_INHERITED_GROUPS, getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria =
        new Criteria()
            .and(UserGroupKeys.accountIdentifier)
            .is(ACCOUNT_IDENTIFIER)
            .and(UserGroupKeys.orgIdentifier)
            .is(ORG_IDENTIFIER)
            .and(UserGroupKeys.projectIdentifier)
            .is(null)

            .andOperator(new Criteria().orOperator(Criteria.where(UserGroupKeys.name).regex(searchTerm, "i"),
                Criteria.where(UserGroupKeys.tags).regex(searchTerm, "i")));

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenProjectAndIncludeAndAccountOrgAccessAndSearchAndInheritedExists()
      throws IOException {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    String searchTerm = randomAlphabetic(5);
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(accessControlClient.hasAccess(
             ResourceScope.of(ACCOUNT_IDENTIFIER, null, null), Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION))
        .thenReturn(true);
    when(accessControlClient.hasAccess(ResourceScope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null),
             Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION))
        .thenReturn(true);
    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));

    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> request = mock(Call.class);
    doReturn(request)
        .when(accessControlAdminClient)
        .getAggregatedFilteredRoleAssignments(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
            RoleAssignmentFilterDTO.builder()
                .principalTypeFilter(Collections.singleton(USER_GROUP))
                .principalScopeLevelFilter(new HashSet<String>(
                    Arrays.asList(ScopeLevel.of(ACCOUNT_IDENTIFIER, null, null).toString().toLowerCase(),
                        ScopeLevel.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null).toString().toLowerCase())))
                .build());
    ScopeDTO roleAssignmentScopeDTO = ScopeDTO.builder()
                                          .accountIdentifier(ACCOUNT_IDENTIFIER)
                                          .orgIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJECT_IDENTIFIER)
                                          .build();
    doReturn(
        Response.success(ResponseDTO.newResponse(
            RoleAssignmentAggregateResponseDTO.builder()
                .roles(new ArrayList<>(
                    Collections.singleton(RoleResponseDTO.builder()
                                              .harnessManaged(false)
                                              .role(RoleDTO.builder().identifier("ROLE1").name("ROLE1").build())
                                              .build())))
                .resourceGroups(new ArrayList<>(
                    Collections.singleton(ResourceGroupDTO.builder().identifier("RG1").name("RG1").build())))
                .roleAssignments(new ArrayList<>(Arrays.asList(
                    RoleAssignmentDTO.builder()
                        .identifier("RA1")
                        .roleIdentifier("ROLE1")
                        .resourceGroupIdentifier("RG1")
                        .disabled(false)
                        .managed(false)
                        .principal(
                            PrincipalDTO.builder().identifier("IHUG1").type(USER_GROUP).scopeLevel("account").build())
                        .build(),
                    RoleAssignmentDTO.builder()
                        .identifier("RA1")
                        .roleIdentifier("ROLE1")
                        .resourceGroupIdentifier("RG1")
                        .disabled(false)
                        .managed(false)
                        .principal(PrincipalDTO.builder()
                                       .identifier("IHUG2")
                                       .type(USER_GROUP)
                                       .scopeLevel("organization")
                                       .build())
                        .build())))
                .scope(roleAssignmentScopeDTO)
                .build())))
        .when(request)
        .execute();

    userGroupService.list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, searchTerm,
        UserGroupFilterType.INCLUDE_INHERITED_GROUPS, getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria =
        new Criteria()
            .orOperator(new Criteria().orOperator(Criteria.where(UserGroupKeys.identifier)
                                                      .is("IHUG1")
                                                      .andOperator(new Criteria()
                                                                       .is(ACCOUNT_IDENTIFIER)
                                                                       .and(UserGroupKeys.orgIdentifier)
                                                                       .exists(false)
                                                                       .and(UserGroupKeys.projectIdentifier)
                                                                       .exists(false)),
                            Criteria.where(UserGroupKeys.identifier)
                                .is("IHUG2")
                                .andOperator(new Criteria()
                                                 .is(ACCOUNT_IDENTIFIER)
                                                 .and(UserGroupKeys.orgIdentifier)
                                                 .is(ORG_IDENTIFIER)
                                                 .and(UserGroupKeys.projectIdentifier)
                                                 .exists(false))),
                new Criteria()
                    .and(UserGroupKeys.accountIdentifier)
                    .is(ACCOUNT_IDENTIFIER)
                    .and(UserGroupKeys.orgIdentifier)
                    .is(ORG_IDENTIFIER)
                    .and(UserGroupKeys.projectIdentifier)
                    .is(PROJECT_IDENTIFIER))

            .andOperator(new Criteria().orOperator(Criteria.where(UserGroupKeys.name).regex(searchTerm, "i"),
                Criteria.where(UserGroupKeys.tags).regex(searchTerm, "i")));

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenProjectAndIncludeAndAccountOrgAccessAndSearchAndInheritedNotExists()
      throws IOException {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    String searchTerm = randomAlphabetic(5);
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(accessControlClient.hasAccess(
             ResourceScope.of(ACCOUNT_IDENTIFIER, null, null), Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION))
        .thenReturn(true);
    when(accessControlClient.hasAccess(ResourceScope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null),
             Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION))
        .thenReturn(true);
    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));

    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> request = mock(Call.class);
    doReturn(request)
        .when(accessControlAdminClient)
        .getAggregatedFilteredRoleAssignments(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
            RoleAssignmentFilterDTO.builder()
                .principalTypeFilter(Collections.singleton(USER_GROUP))
                .principalScopeLevelFilter(new HashSet<String>(
                    Arrays.asList(ScopeLevel.of(ACCOUNT_IDENTIFIER, null, null).toString().toLowerCase(),
                        ScopeLevel.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null).toString().toLowerCase())))
                .build());
    ScopeDTO roleAssignmentScopeDTO = ScopeDTO.builder()
                                          .accountIdentifier(ACCOUNT_IDENTIFIER)
                                          .orgIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJECT_IDENTIFIER)
                                          .build();
    doReturn(Response.success(ResponseDTO.newResponse(RoleAssignmentAggregateResponseDTO.builder()
                                                          .roles(new ArrayList<>())
                                                          .resourceGroups(new ArrayList<>())
                                                          .roleAssignments(new ArrayList<>())
                                                          .scope(roleAssignmentScopeDTO)
                                                          .build())))
        .when(request)
        .execute();

    userGroupService.list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, searchTerm,
        UserGroupFilterType.INCLUDE_INHERITED_GROUPS, getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria =
        new Criteria()
            .and(UserGroupKeys.accountIdentifier)
            .is(ACCOUNT_IDENTIFIER)
            .and(UserGroupKeys.orgIdentifier)
            .is(ORG_IDENTIFIER)
            .and(UserGroupKeys.projectIdentifier)
            .is(PROJECT_IDENTIFIER)
            .andOperator(new Criteria().orOperator(Criteria.where(UserGroupKeys.name).regex(searchTerm, "i"),
                Criteria.where(UserGroupKeys.tags).regex(searchTerm, "i")));

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenProjectAndIncludeAndAccountAccessOrgNotAccessAndSearchAndInheritedNotExists()
      throws IOException {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    String searchTerm = randomAlphabetic(5);
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(accessControlClient.hasAccess(
             ResourceScope.of(ACCOUNT_IDENTIFIER, null, null), Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION))
        .thenReturn(true);
    when(accessControlClient.hasAccess(ResourceScope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null),
             Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION))
        .thenReturn(false);
    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));

    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> request = mock(Call.class);
    doReturn(request)
        .when(accessControlAdminClient)
        .getAggregatedFilteredRoleAssignments(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
            RoleAssignmentFilterDTO.builder()
                .principalTypeFilter(Collections.singleton(USER_GROUP))
                .principalScopeLevelFilter(new HashSet<>(
                    Arrays.asList(ScopeLevel.of(ACCOUNT_IDENTIFIER, null, null).toString().toLowerCase())))
                .build());
    ScopeDTO roleAssignmentScopeDTO = ScopeDTO.builder()
                                          .accountIdentifier(ACCOUNT_IDENTIFIER)
                                          .orgIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJECT_IDENTIFIER)
                                          .build();
    doReturn(
        Response.success(ResponseDTO.newResponse(
            RoleAssignmentAggregateResponseDTO.builder()
                .roles(new ArrayList<>(
                    Collections.singleton(RoleResponseDTO.builder()
                                              .harnessManaged(false)
                                              .role(RoleDTO.builder().identifier("ROLE1").name("ROLE1").build())
                                              .build())))
                .resourceGroups(new ArrayList<>(
                    Collections.singleton(ResourceGroupDTO.builder().identifier("RG1").name("RG1").build())))
                .roleAssignments(new ArrayList<>(Arrays.asList(
                    RoleAssignmentDTO.builder()
                        .identifier("RA1")
                        .roleIdentifier("ROLE1")
                        .resourceGroupIdentifier("RG1")
                        .disabled(false)
                        .managed(false)
                        .principal(
                            PrincipalDTO.builder().identifier("IHUG1").type(USER_GROUP).scopeLevel("account").build())
                        .build())))
                .scope(roleAssignmentScopeDTO)
                .build())))
        .when(request)
        .execute();

    userGroupService.list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, searchTerm,
        UserGroupFilterType.INCLUDE_INHERITED_GROUPS, getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria =
        new Criteria()
            .orOperator(new Criteria().orOperator(Criteria.where(UserGroupKeys.identifier)
                                                      .is("IHUG1")
                                                      .andOperator(new Criteria()
                                                                       .is(ACCOUNT_IDENTIFIER)
                                                                       .and(UserGroupKeys.orgIdentifier)
                                                                       .exists(false)
                                                                       .and(UserGroupKeys.projectIdentifier)
                                                                       .exists(false))),
                new Criteria()
                    .and(UserGroupKeys.accountIdentifier)
                    .is(ACCOUNT_IDENTIFIER)
                    .and(UserGroupKeys.orgIdentifier)
                    .is(ORG_IDENTIFIER)
                    .and(UserGroupKeys.projectIdentifier)
                    .is(PROJECT_IDENTIFIER))

            .andOperator(new Criteria().orOperator(Criteria.where(UserGroupKeys.name).regex(searchTerm, "i"),
                Criteria.where(UserGroupKeys.tags).regex(searchTerm, "i")));

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testListUserGroupsWhenProjectAndIncludeAndNotAccountOrgAccessAndSearch() throws IOException {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    String searchTerm = randomAlphabetic(5);
    final ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(accessControlClient.hasAccess(
             ResourceScope.of(ACCOUNT_IDENTIFIER, null, null), Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION))
        .thenReturn(false);
    when(accessControlClient.hasAccess(ResourceScope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null),
             Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION))
        .thenReturn(false);
    when(userGroupRepository.findAll(any(Criteria.class), any())).thenReturn(getPage(emptyList(), 0));

    userGroupService.list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, searchTerm,
        UserGroupFilterType.INCLUDE_INHERITED_GROUPS, getPageRequest(pageRequest));
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture(), any());

    Criteria developedCriteria = userGroupCriteriaArgumentCaptor.getValue();
    Criteria expectedCriteria =
        new Criteria()
            .and(UserGroupKeys.accountIdentifier)
            .is(ACCOUNT_IDENTIFIER)
            .and(UserGroupKeys.orgIdentifier)
            .is(ORG_IDENTIFIER)
            .and(UserGroupKeys.projectIdentifier)
            .is(PROJECT_IDENTIFIER)
            .andOperator(new Criteria().orOperator(Criteria.where(UserGroupKeys.name).regex(searchTerm, "i"),
                Criteria.where(UserGroupKeys.tags).regex(searchTerm, "i")));

    assertThat(developedCriteria.getCriteriaObject().keySet()).isEqualTo(expectedCriteria.getCriteriaObject().keySet());

    verifyNoMoreInteractions(userGroupRepository);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetInheritingChildScopeListNotExists() throws IOException {
    Criteria userGroupFetchCriteria = new Criteria()
                                          .and(UserGroupKeys.accountIdentifier)
                                          .is(ACCOUNT_IDENTIFIER)
                                          .and(UserGroupKeys.orgIdentifier)
                                          .is(ORG_IDENTIFIER)
                                          .and(UserGroupKeys.projectIdentifier)
                                          .is(PROJECT_IDENTIFIER)
                                          .and(UserGroupKeys.identifier)
                                          .is("UG1");

    when(userGroupRepository.find(userGroupFetchCriteria)).thenReturn(Optional.empty());
    try {
      userGroupService.getInheritingChildScopeList(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "UG1");
      fail("Expected failure as usergroup does not exist");
    } catch (InvalidRequestException ex) {
      assertThat(ex.getParams().get("message"))
          .isEqualTo(String.format("User Group is not available %s:%s:%s:%s", ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
              PROJECT_IDENTIFIER, "UG1"));
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetInheritingChildScopeList() throws IOException {
    Criteria userGroupFetchCriteria = new Criteria()
                                          .and(UserGroupKeys.accountIdentifier)
                                          .is(ACCOUNT_IDENTIFIER)
                                          .and(UserGroupKeys.orgIdentifier)
                                          .is(null)
                                          .and(UserGroupKeys.projectIdentifier)
                                          .is(null)
                                          .and(UserGroupKeys.identifier)
                                          .is("UG1");
    UserGroup ug1 = UserGroup.builder().identifier("UG1").accountIdentifier(ACCOUNT_IDENTIFIER).build();
    when(userGroupRepository.find(userGroupFetchCriteria)).thenReturn(Optional.of(ug1));

    RoleAssignmentFilterDTO roleAssignmentFilterDTO =
        RoleAssignmentFilterDTO.builder()
            .principalFilter(Collections.singleton(
                PrincipalDTO.builder()
                    .identifier("UG1")
                    .type(USER_GROUP)
                    .scopeLevel(ScopeLevel.of(ACCOUNT_IDENTIFIER, null, null).toString().toLowerCase())
                    .build()))
            .build();

    Call<ResponseDTO<List<RoleAssignmentResponseDTO>>> request = mock(Call.class);
    doReturn(request)
        .when(accessControlAdminClient)
        .getFilteredRoleAssignmentsIncludingChildScopes(ACCOUNT_IDENTIFIER, null, null, roleAssignmentFilterDTO);
    ScopeDTO roleAssignmentScopeDTO1 = ScopeDTO.builder()
                                           .accountIdentifier(ACCOUNT_IDENTIFIER)
                                           .orgIdentifier(ORG_IDENTIFIER)
                                           .projectIdentifier(PROJECT_IDENTIFIER)
                                           .build();
    ScopeDTO roleAssignmentScopeDTO2 =
        ScopeDTO.builder().accountIdentifier(ACCOUNT_IDENTIFIER).orgIdentifier(ORG2_IDENTIFIER).build();
    ScopeDTO roleAssignmentScopeDTO3 = ScopeDTO.builder().accountIdentifier(ACCOUNT_IDENTIFIER).build();

    doReturn(Response.success(ResponseDTO.newResponse(new ArrayList<>(Arrays.asList(
                 RoleAssignmentResponseDTO.builder()
                     .scope(roleAssignmentScopeDTO1)
                     .roleAssignment(
                         RoleAssignmentDTO.builder()
                             .identifier("RA1")
                             .roleIdentifier("ROLE1")
                             .resourceGroupIdentifier("RG1")
                             .disabled(false)
                             .managed(false)
                             .principal(
                                 PrincipalDTO.builder()
                                     .identifier("UG1")
                                     .type(USER_GROUP)
                                     .scopeLevel(ScopeLevel.of(ACCOUNT_IDENTIFIER, null, null).toString().toLowerCase())
                                     .build())
                             .build())
                     .build(),
                 RoleAssignmentResponseDTO.builder()
                     .scope(roleAssignmentScopeDTO1)
                     .roleAssignment(
                         RoleAssignmentDTO.builder()
                             .identifier("RA2")
                             .roleIdentifier("ROLE1")
                             .resourceGroupIdentifier("RG1")
                             .disabled(false)
                             .managed(false)
                             .principal(
                                 PrincipalDTO.builder()
                                     .identifier("UG1")
                                     .type(USER_GROUP)
                                     .scopeLevel(ScopeLevel.of(ACCOUNT_IDENTIFIER, null, null).toString().toLowerCase())
                                     .build())
                             .build())
                     .build(),
                 RoleAssignmentResponseDTO.builder()
                     .scope(roleAssignmentScopeDTO2)
                     .roleAssignment(
                         RoleAssignmentDTO.builder()
                             .identifier("RA3")
                             .roleIdentifier("ROLE1")
                             .resourceGroupIdentifier("RG1")
                             .disabled(false)
                             .managed(false)
                             .principal(
                                 PrincipalDTO.builder()
                                     .identifier("UG1")
                                     .type(USER_GROUP)
                                     .scopeLevel(ScopeLevel.of(ACCOUNT_IDENTIFIER, null, null).toString().toLowerCase())
                                     .build())
                             .build())
                     .build(),
                 RoleAssignmentResponseDTO.builder()
                     .scope(roleAssignmentScopeDTO3)
                     .roleAssignment(
                         RoleAssignmentDTO.builder()
                             .identifier("RA4")
                             .roleIdentifier("ROLE1")
                             .resourceGroupIdentifier("RG1")
                             .disabled(false)
                             .managed(false)
                             .principal(
                                 PrincipalDTO.builder()
                                     .identifier("UG1")
                                     .type(USER_GROUP)
                                     .scopeLevel(ScopeLevel.of(ACCOUNT_IDENTIFIER, null, null).toString().toLowerCase())
                                     .build())
                             .build())
                     .build())))))
        .when(request)
        .execute();
    ScopeNameDTO scopeNameDTO1 = ScopeNameDTO.builder()
                                     .accountIdentifier(ACCOUNT_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .projectIdentifier(PROJECT_IDENTIFIER)
                                     .orgName("ORG")
                                     .projectName("PROJ")
                                     .build();
    ScopeNameDTO scopeNameDTO2 = ScopeNameDTO.builder()
                                     .accountIdentifier(ACCOUNT_IDENTIFIER)
                                     .orgIdentifier(ORG2_IDENTIFIER)
                                     .projectIdentifier(PROJECT_IDENTIFIER)
                                     .orgName("ORG")
                                     .projectName("PROJ")
                                     .build();
    when(scopeNameMapper.toScopeNameDTO(roleAssignmentScopeDTO2)).thenReturn(scopeNameDTO2);
    when(scopeNameMapper.toScopeNameDTO(roleAssignmentScopeDTO1)).thenReturn(scopeNameDTO1);
    List<ScopeNameDTO> returnedValue =
        userGroupService.getInheritingChildScopeList(ACCOUNT_IDENTIFIER, null, null, "UG1");
    assertThat(returnedValue.size()).isEqualTo(2);
    assertThat(returnedValue).isEqualTo(new ArrayList<>(Arrays.asList(scopeNameDTO1, scopeNameDTO2)));
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetAllUserGroupsForAUser_WithUsersAndTagsExcluded() {
    // Case when user groups are fetched as well for user
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    String userGroupIdentifier = randomAlphabetic(10);
    UserGroup userGroup = UserGroup.builder()
                              .accountIdentifier(scope.getAccountIdentifier())
                              .orgIdentifier(scope.getOrgIdentifier())
                              .projectIdentifier(scope.getProjectIdentifier())
                              .identifier(userGroupIdentifier)
                              .externallyManaged(true)
                              .isSsoLinked(false)
                              .build();

    ArgumentCaptor<Criteria> userGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    doReturn(Collections.singletonList(userGroup))
        .when(userGroupRepository)
        .findAll(userGroupCriteriaArgumentCaptor.capture());
    userGroupService.getUserGroupsForUser(ACCOUNT_IDENTIFIER, "testUserId");
    verify(userGroupRepository, times(1)).findAll(userGroupCriteriaArgumentCaptor.capture());
  }
}