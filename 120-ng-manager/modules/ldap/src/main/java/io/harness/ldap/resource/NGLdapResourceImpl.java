/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.resource;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ldap.service.NGLdapService;
import io.harness.ng.core.api.UserGroupService;
import io.harness.rest.RestResponse;

import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapTestResponse;

import com.google.inject.Inject;
import java.util.Collection;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class NGLdapResourceImpl implements NGLdapResource {
  NGLdapService ngLdapService;
  AccessControlClient accessControlClient;
  UserGroupService userGroupService;

  @Override
  public RestResponse<LdapTestResponse> validateLdapConnectionSettings(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, LdapSettings settings) {
    LdapTestResponse ldapTestResponse =
        ngLdapService.validateLdapConnectionSettings(accountIdentifier, orgIdentifier, projectIdentifier, settings);
    return new RestResponse<>(ldapTestResponse);
  }

  @Override
  public RestResponse<LdapTestResponse> validateLdapUserSettings(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, LdapSettings settings) {
    LdapTestResponse ldapTestResponse =
        ngLdapService.validateLdapUserSettings(accountIdentifier, orgIdentifier, projectIdentifier, settings);
    return new RestResponse<>(ldapTestResponse);
  }

  @Override
  public RestResponse<LdapTestResponse> validateLdapGroupSettings(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, LdapSettings settings) {
    LdapTestResponse ldapTestResponse =
        ngLdapService.validateLdapGroupSettings(accountIdentifier, orgIdentifier, projectIdentifier, settings);
    return new RestResponse<>(ldapTestResponse);
  }

  @Override
  public RestResponse<Collection<LdapGroupResponse>> searchLdapGroups(
      String ldapId, String accountId, String orgIdentifier, String projectIdentifier, String name) {
    Collection<LdapGroupResponse> groups =
        ngLdapService.searchLdapGroupsByName(accountId, orgIdentifier, projectIdentifier, ldapId, name);
    return new RestResponse<>(groups);
  }

  @Override
  public RestResponse<Boolean> syncLdapGroups(String accountId, String orgIdentifier, String projectIdentifier) {
    ngLdapService.syncUserGroupsJob(accountId, orgIdentifier, projectIdentifier);
    return new RestResponse<>(true);
  }
}
