/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessModule._970_RBAC_CORE;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.rule.OwnerRule.NANDAN;
import static io.harness.rule.OwnerRule.RAMA;

import static software.wings.beans.Account.AccountKeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.Account.Builder;
import software.wings.beans.security.HarnessUserGroup;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.HarnessUserGroupService;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 *
 * @author rktummala
 */
@OwnedBy(HarnessTeam.PL)
@TargetModule(_970_RBAC_CORE)
public class HarnessUserGroupServiceTest extends WingsBaseTest {
  @Mock private AccountService accountService;
  @Mock private Account account;

  @InjectMocks @Inject private HarnessUserGroupService harnessUserGroupService;

  private String accountId1 = UUIDGenerator.generateUuid();
  private String accountId2 = UUIDGenerator.generateUuid();
  private String harnessUserGroupId1 = UUIDGenerator.generateUuid();
  private String harnessUserGroupId2 = UUIDGenerator.generateUuid();
  private String memberId1 = "memberId1";
  private String memberId2 = "memberId2";
  private String harnessUserGroupName1 = "harnessUserGroupName1";
  private String harnessUserGroupName2 = "harnessUserGroupName2";
  private String description1 = "harnessUserGroup 1";
  private String description2 = "harnessUserGroup 2";
  private HarnessUserGroup.GroupType groupType1 = HarnessUserGroup.GroupType.RESTRICTED;

  /**
   * Sets mocks.
   */
  @Before
  public void setupMocks() {
    when(accountService.get(anyString())).thenReturn(account);
  }

  /**
   * Should save and read.
   *
   */
  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testSaveAndRead() {
    HarnessUserGroup harnessUserGroup = HarnessUserGroup.builder()
                                            .uuid(harnessUserGroupId1)
                                            .accountIds(Sets.newHashSet(accountId2))
                                            .description(description1)
                                            .name(harnessUserGroupName1)
                                            .memberIds(Sets.newHashSet(memberId1))
                                            .groupType(groupType1)
                                            .build();
    HarnessUserGroup savedHarnessUserGroup = harnessUserGroupService.save(harnessUserGroup);
    compare(harnessUserGroup, savedHarnessUserGroup);

    HarnessUserGroup harnessUserGroupFromGet = harnessUserGroupService.get(harnessUserGroupId1);
    compare(savedHarnessUserGroup, harnessUserGroupFromGet);

    harnessUserGroupFromGet = harnessUserGroupService.get(harnessUserGroupId1);
    compare(savedHarnessUserGroup, harnessUserGroupFromGet);
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void test_createHarnessUserGroup() {
    HarnessUserGroup harnessUserGroup = harnessUserGroupService.createHarnessUserGroup(
        harnessUserGroupName1, description1, Sets.newHashSet(memberId1), Sets.newHashSet(accountId1), groupType1);
    List<HarnessUserGroup> harnessUserGroupList = harnessUserGroupService.listHarnessUserGroupForAccount(accountId1);
    assertThat(harnessUserGroupList.size() == 1).isTrue();
    assertThat(harnessUserGroupList.get(0).getUuid()).isEqualTo(harnessUserGroup.getUuid());
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void test_listHarnessUserGroupForAccount() {
    HarnessUserGroup harnessUserGroup1 = harnessUserGroupService.createHarnessUserGroup(
        harnessUserGroupName1, description1, Sets.newHashSet(memberId1), Sets.newHashSet(accountId1), groupType1);
    HarnessUserGroup harnessUserGroup2 = harnessUserGroupService.createHarnessUserGroup(
        harnessUserGroupName2, description2, Sets.newHashSet(memberId2), Sets.newHashSet(accountId2), groupType1);
    List<HarnessUserGroup> harnessUserGroupList = harnessUserGroupService.listHarnessUserGroupForAccount(accountId2);
    assertThat(harnessUserGroupList.size() == 1).isTrue();
    assertThat(harnessUserGroupList.get(0).getUuid()).isEqualTo(harnessUserGroup2.getUuid());
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testList() {
    HarnessUserGroup harnessUserGroup1 = HarnessUserGroup.builder()
                                             .uuid(harnessUserGroupId1)
                                             .description(description1)
                                             .name(harnessUserGroupName1)
                                             .accountIds(Sets.newHashSet(accountId1))
                                             .memberIds(Sets.newHashSet(memberId1))
                                             .groupType(groupType1)
                                             .build();
    HarnessUserGroup savedHarnessUserGroup1 = harnessUserGroupService.save(harnessUserGroup1);

    HarnessUserGroup harnessUserGroup2 = HarnessUserGroup.builder()
                                             .uuid(harnessUserGroupId2)
                                             .description(description2)
                                             .name(harnessUserGroupName2)
                                             .accountIds(Sets.newHashSet(accountId1))
                                             .memberIds(Sets.newHashSet(memberId1))
                                             .groupType(groupType1)
                                             .build();
    HarnessUserGroup savedHarnessUserGroup2 = harnessUserGroupService.save(harnessUserGroup2);

    PageResponse pageResponse = harnessUserGroupService.list(aPageRequest().build());
    assertThat(pageResponse).isNotNull();
    List<HarnessUserGroup> harnessUserGroupList = pageResponse.getResponse();
    assertThat(harnessUserGroupList).isNotNull();
    assertThat(harnessUserGroupList).hasSize(2);
    assertThat(harnessUserGroupList).containsExactlyInAnyOrder(savedHarnessUserGroup1, savedHarnessUserGroup2);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testUpdateMembers() {
    HarnessUserGroup harnessUserGroup = HarnessUserGroup.builder()
                                            .uuid(harnessUserGroupId1)
                                            .description(description1)
                                            .name(harnessUserGroupName1)
                                            .accountIds(Sets.newHashSet(accountId1))
                                            .memberIds(Sets.newHashSet(memberId1))
                                            .groupType(groupType1)
                                            .build();
    HarnessUserGroup savedHarnessUserGroup = harnessUserGroupService.save(harnessUserGroup);
    compare(harnessUserGroup, savedHarnessUserGroup);

    HarnessUserGroup harnessUserGroupFromGet = harnessUserGroupService.get(harnessUserGroupId1);
    compare(savedHarnessUserGroup, harnessUserGroupFromGet);
    Set<String> memberIds = Sets.newHashSet(memberId1, memberId2);
    harnessUserGroupFromGet.setMemberIds(memberIds);

    HarnessUserGroup updatedHarnessUserGroup =
        harnessUserGroupService.updateMembers(harnessUserGroupId1, accountId1, memberIds);
    harnessUserGroupFromGet = harnessUserGroupService.get(harnessUserGroupId1);
    compare(harnessUserGroupFromGet, updatedHarnessUserGroup);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testListAllowedSupportAccounts() {
    Account account1 = Builder.anAccount().withUuid(accountId1).withHarnessGroupAccessAllowed(true).build();
    when(accountService.listHarnessSupportAccounts(any(), any())).thenReturn(Lists.newArrayList(account1));
    List<Account> result = harnessUserGroupService.listAllowedSupportAccounts(
        Sets.newHashSet(accountId2), Sets.newHashSet(AccountKeys.uuid));

    assertThat(result).isNotNull();
    assertThat(result).size().isEqualTo(1);
    assertThat(result).containsExactlyInAnyOrder(account1);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testDelete() {
    HarnessUserGroup harnessUserGroup = HarnessUserGroup.builder()
                                            .uuid(harnessUserGroupId1)
                                            .description(description1)
                                            .name(harnessUserGroupName1)
                                            .accountIds(Sets.newHashSet(accountId1))
                                            .memberIds(Sets.newHashSet(memberId1))
                                            .groupType(groupType1)
                                            .build();
    harnessUserGroupService.save(harnessUserGroup);
    boolean delete = harnessUserGroupService.delete(accountId1, harnessUserGroup.getUuid());

    assertThat(delete).isTrue();

    HarnessUserGroup harnessUserGroupAfterDelete = harnessUserGroupService.get(harnessUserGroupId1);
    assertThat(harnessUserGroupAfterDelete).isNull();
  }

  private void compare(HarnessUserGroup lhs, HarnessUserGroup rhs) {
    assertThat(rhs.getUuid()).isEqualTo(lhs.getUuid());
    assertThat(rhs.getName()).isEqualTo(lhs.getName());
    assertThat(rhs.getDescription()).isEqualTo(lhs.getDescription());
    assertThat(rhs.getMemberIds()).isEqualTo(lhs.getMemberIds());
  }
}
