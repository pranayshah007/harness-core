/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import software.wings.beans.Role;
import software.wings.service.intfc.ownership.OwnedByAccount;

import dev.morphia.query.Query;
/**
 * Created by anubhaw on 3/28/16.
 */
@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public interface RoleService extends OwnedByAccount {
  /**
   * List.
   *
   * @param pageRequest the page request
   * @return the page response
   */
  PageResponse<Role> list(PageRequest<Role> pageRequest);

  /**
   * Save.
   *
   * @param role the role
   * @return the role
   */
  Role save(Role role);

  /**
   * Find by uuid.
   *
   * @param uuid the uuid
   * @return the role
   */
  Role get(String uuid);

  /**
   * Update.
   *
   * @param role the role
   * @return the role
   */
  Role update(Role role);

  /**
   * Delete.
   *
   * @param roleId the role id
   */
  void delete(String roleId);

  /**
   * Gets account admin role.
   *
   * @return the account admin role
   */
  Role getAccountAdminRole(String accountId);

  /**
   * Gets account roles.
   *
   * @return the account roles
   */
  Query<Role> getAccountRolesQuery(String accountId);

  /**
   * Gets app admin role.
   *
   * @return the app admin role
   */
  Role getAppAdminRole(String accountId, String appId);
}
