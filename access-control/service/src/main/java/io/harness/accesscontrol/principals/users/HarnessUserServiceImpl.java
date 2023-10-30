/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.users;

import static java.lang.Boolean.TRUE;
import static java.util.Objects.nonNull;

import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.usermembership.remote.UserMembershipClient;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class HarnessUserServiceImpl implements HarnessUserService {
  private final UserMembershipClient userMembershipClient;
  private final UserService userService;

  @Inject
  public HarnessUserServiceImpl(
      @Named("PRIVILEGED") UserMembershipClient userMembershipClient, UserService userService) {
    this.userMembershipClient = userMembershipClient;
    this.userService = userService;
  }

  @Override
  public void sync(String identifier, Scope scope) {
    HarnessScopeParams scopeParams = ScopeMapper.toParams(scope);
    Boolean isUserInScope =
        NGRestUtils.getResponse(userMembershipClient.isUserInScope(identifier, scopeParams.getAccountIdentifier(),
                                    scopeParams.getOrgIdentifier(), scopeParams.getProjectIdentifier()),
            "Could not find the user with the given identifier");
    UserMetadataDTO userMetadataDTO =
        NGRestUtils.getResponse(userMembershipClient.getUser(identifier, scopeParams.getAccountIdentifier()));
    if (TRUE.equals(isUserInScope) && nonNull(userMetadataDTO)) {
      User user = User.builder()
                      .identifier(identifier)
                      .scopeIdentifier(scope.toString())
                      .name(userMetadataDTO.getName())
                      .email(userMetadataDTO.getEmail())
                      .build();
      userService.createIfNotPresent(user);
    } else {
      userService.deleteIfPresent(identifier, scope.toString());
    }
  }
}
