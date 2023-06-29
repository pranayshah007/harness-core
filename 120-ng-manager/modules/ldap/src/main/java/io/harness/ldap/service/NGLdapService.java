/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.helpers.ext.ldap.LdapResponse;

import java.util.Collection;
import javax.validation.constraints.NotNull;

@OwnedBy(HarnessTeam.PL)
public interface NGLdapService {
  LdapTestResponse validateLdapConnectionSettings(
      @NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier, LdapSettings settings);
  LdapTestResponse validateLdapUserSettings(
      @NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier, LdapSettings settings);
  LdapTestResponse validateLdapGroupSettings(
      @NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier, LdapSettings settings);
  Collection<LdapGroupResponse> searchLdapGroupsByName(@NotNull String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NotNull String ldapId, @NotNull String name);
  void syncUserGroupsJob(@NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier);
  LdapResponse testLDAPLogin(@NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier,
      @NotNull String email, @NotNull String password);
  void syncAUserGroupJob(@NotNull String userGroupIdentifier, @NotNull String accountIdentifier, String orgIdentifier,
      String projectIdentifier);
}
