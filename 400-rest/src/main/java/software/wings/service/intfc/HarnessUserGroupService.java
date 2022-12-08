/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessModule._970_RBAC_CORE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.security.HarnessSupportUserDTO;
import software.wings.beans.security.HarnessUserGroup;
import software.wings.beans.security.HarnessUserGroupDTO;

import java.util.List;
import java.util.Set;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * @author rktummala on 05/06/18
 */
@OwnedBy(HarnessTeam.PL)
@TargetModule(_970_RBAC_CORE)
public interface HarnessUserGroupService {
  /**
   * Save.
   *
   * @param harnessPermissions the harness permissions
   * @return the userGroup
   */
  HarnessUserGroup save(HarnessUserGroup harnessPermissions);

  /**
   * List page response.
   *
   * @param req the req
   * @return the page response
   */
  PageResponse<HarnessUserGroup> list(PageRequest<HarnessUserGroup> req);

  List<Account> listAllowedSupportAccounts(Set<String> excludeAccountIds, Set<String> fieldsToBeIncluded);

  /**
   * Find by uuid.
   *
   * @param uuid the uuid
   * @return the harnessPermissions
   */
  HarnessUserGroup get(@NotEmpty String uuid);

  /**
   * Update Overview.
   *
   * @param uuid the harness permissions id
   * @param memberIds the memberIds
   * @return the userGroup
   */
  HarnessUserGroup updateMembers(@NotEmpty String uuid, String accountId, Set<String> memberIds);

  HarnessUserGroup updateMembers(@NotEmpty String uuid, String accountId, HarnessUserGroupDTO harnessUserGroupDTO);
  /**
   * Check if user is part of harness support.
   * @param userId
   * @return
   */
  boolean isHarnessSupportUser(String userId);

  boolean delete(String accountId, @NotEmpty String uuid);

  boolean isHarnessSupportEnabledForAccount(String accountId);

  boolean isHarnessSupportEnabled(String accountId, String userId);

  HarnessUserGroup createHarnessUserGroup(String accountId, HarnessUserGroupDTO harnessUserGroupDTO);

  HarnessUserGroup createHarnessUserGroup(String name, String description, Set<String> memberIds,
      Set<String> accountIds, HarnessUserGroup.GroupType groupType);

  List<HarnessUserGroup> listHarnessUserGroupForAccount(String accountId);

  List<HarnessUserGroup> listHarnessUserGroup(String accountId, String memberId, HarnessUserGroup.GroupType groupType);

  List<User> listAllHarnessSupportUsers();

  List<User> listAllHarnessSupportUserInternal();

  HarnessSupportUserDTO toHarnessSupportUser(User user);

  List<HarnessSupportUserDTO> toHarnessSupportUser(List<User> userList);
}
