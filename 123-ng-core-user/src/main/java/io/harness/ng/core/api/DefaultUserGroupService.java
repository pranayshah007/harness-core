/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.ng.core.user.entities.UserGroup;

import java.util.List;
import java.util.Optional;

/**
 * This interface exposes methods needed for Default User Group operations.
 */
@OwnedBy(PL)
public interface DefaultUserGroupService {
  /**
   * This method is to be used for Default User Group creation.
   * @param scope Scope to which Default User Group to create
   * @param userIds list of userIds to be included while creating Default User Group.
   * @return UserGroup
   */
  UserGroup create(Scope scope, List<String> userIds);

  /**
   * This method is to be used for adding user to Default User Group.
   * @param scope Scope to which Default User Group to create
   * @param userId userId to be added to Default User Group.
   * @return Nothing
   */
  void addUserToDefaultUserGroup(Scope scope, String userId);

  /**
   * This method is to be used to get Default User Group.
   * @param scope Scope to Default User Group
   * @return
   */
  Optional<UserGroup> get(Scope scope);

  /**
   * The method is used to check if User Group with identifier is Default User Group.
   * @param scope to Default User Group
   * @param identifier to check whether default User Group
   * @return boolean Returns indicating if it is Default User Group.
   */
  boolean isDefaultUserGroup(Scope scope, String identifier);

  /**
   * The method can be used to create or update User Group at scope.
   * @param scope to Default User Group
   */
  UserGroup createOrUpdateUserGroupAtScope(Scope scope);
}
