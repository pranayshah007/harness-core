/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.usergroups;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.usergroups.UserGroupClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Optional;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@ValidateOnExecution
@Slf4j
public class HarnessUserGroupServiceImpl implements HarnessUserGroupService {
  private final UserGroupClient userGroupClient;
  private final UserGroupService userGroupService;

  @Inject
  public HarnessUserGroupServiceImpl(
      @Named("PRIVILEGED") UserGroupClient userGroupClient, UserGroupService userGroupService) {
    this.userGroupClient = userGroupClient;
    this.userGroupService = userGroupService;
  }

  @Override
  public void sync(String identifier, Scope scope) {
    HarnessScopeParams scopeParams = ScopeMapper.toParams(scope);
    Optional<UserGroupDTO> userGroupDTOOpt = Optional.empty();
    try {
      userGroupDTOOpt = Optional.ofNullable(
          NGRestUtils.getResponse(userGroupClient.getUserGroup(identifier, scopeParams.getAccountIdentifier(),
                                      scopeParams.getOrgIdentifier(), scopeParams.getProjectIdentifier()),
              "Could not find the user group with the given identifier"));
      if (userGroupDTOOpt.isPresent()) {
        userGroupService.upsert(UserGroupFactory.buildUserGroup(userGroupDTOOpt.get()));
      }
    } catch (InvalidRequestException e) {
      if (userGroupDTOOpt.isEmpty()) {
        deleteIfPresent(identifier, scope);
      } else {
        throw e;
      }
    } catch (Exception e) {
      log.error("Exception while syncing user groups", e);
    }
  }

  public void deleteIfPresent(String identifier, Scope scope) {
    log.warn("Removing user group with identifier {} in scope {}.", identifier, scope.toString());
    userGroupService.deleteIfPresent(identifier, scope.toString());
  }
}
