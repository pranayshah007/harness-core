/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.resource;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ldap.service.NGLdapService;
import io.harness.rest.RestResponse;

import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapSettingsMapper;
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

  @Override
  public RestResponse<LdapTestResponse> validateLdapConnectionSettings(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, LdapSettings settings) {
    LdapTestResponse ldapTestResponse = ngLdapService.validateLdapConnectionSettings(
        accountIdentifier, orgIdentifier, projectIdentifier, LdapSettingsMapper.ldapSettingsDTO(settings));
    return new RestResponse<>(ldapTestResponse);
  }

  @Override
  public RestResponse<Collection<LdapGroupResponse>> searchLdapGroups(
      String ldapId, String accountId, String orgIdentifier, String projectIdentifier, String name) {
    Collection<LdapGroupResponse> groups =
        ngLdapService.searchLdapGroupsByName(accountId, orgIdentifier, projectIdentifier, ldapId, name);
    return new RestResponse<>(groups);
  }
}
