/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.KARAN;
import static io.harness.rule.OwnerRule.REETIKA;
import static io.harness.utils.PageTestUtils.getPage;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.service.LastAdminCheckService;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ng.core.spring.UserGroupRepository;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class UserGroupServiceImplTest extends CategoryTest {
  @Mock private UserGroupRepository userGroupRepository;
  @Mock private OutboxService outboxService;
  @Mock private AccessControlAdminClient accessControlAdminClient;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private NgUserService ngUserService;
  @Mock private LastAdminCheckService lastAdminCheckService;
  @Spy @Inject @InjectMocks private UserGroupServiceImpl userGroupService;

  private static final String ACCOUNT_IDENTIFIER = "A1";
  private static final String ORG_IDENTIFIER = "O1";
  private static final String PROJECT_IDENTIFIER = "P1";

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testCreateValidate() throws IOException {
    List<String> users = Lists.newArrayList("u1", "u2", "u3");
    List<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(UserInfo.builder().uuid("u1").build());
    userInfos.add(UserInfo.builder().uuid("u2").build());

    String ACCOUNT_IDENTIFIER = "A1";
    UserGroupDTO userGroupDTO = UserGroupDTO.builder()
                                    .users(users)
                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                    .orgIdentifier(ORG_IDENTIFIER)
                                    .projectIdentifier(PROJECT_IDENTIFIER)
                                    .build();

    // Users with all valid users with failing membership
    assertThatThrownBy(() -> userGroupService.create(userGroupDTO))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("The following users are not valid: [u1, u2, u3]");

    doReturn(Arrays.asList("u1", "u2")).when(ngUserService).listUserIds(any());

    // Users with all valid users with few memberships
    assertThatThrownBy(() -> userGroupService.create(userGroupDTO))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("The following user is not valid: [u3]");

    doReturn(Arrays.asList("u1", "u2", "u3")).when(ngUserService).listUserIds(any());

    // Users with all valid users with all memberships
    userGroupService.create(userGroupDTO);

    UserGroup userGroup = UserGroup.builder().users(users).build();
    doReturn(userGroup).when(transactionTemplate).execute(any());
    assertThat(userGroupService.create(userGroupDTO)).isEqualTo(userGroup);

    users.add("u1");
    assertThatThrownBy(() -> userGroupService.create(userGroupDTO))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Duplicate users");
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

    List<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(UserInfo.builder().uuid("u1").build());

    doReturn(Arrays.asList("u1", "u2")).when(ngUserService).listUserIds(any());

    userGroupService.addMember(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, userGroupIdentifier, "u1");
    assertThat(users.size()).isEqualTo(1);

    userInfos.add(UserInfo.builder().uuid("u2").build());
    userGroupService.addMember(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, userGroupIdentifier, "u2");
    assertThat(users.size()).isEqualTo(2);

    userInfos.add(UserInfo.builder().uuid("u2").build());
    try {
      userGroupService.addMember(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, userGroupIdentifier, "u2");
      fail("Expected failure as user already present.");
    } catch (InvalidRequestException exception) {
      // all good here
    }
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
}
