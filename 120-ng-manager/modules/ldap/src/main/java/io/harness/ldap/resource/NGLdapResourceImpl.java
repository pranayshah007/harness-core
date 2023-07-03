/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.resource;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Objects.isNull;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ldap.service.NGLdapService;
import io.harness.ng.core.api.UserGroupService;
import io.harness.rest.RestResponse;

import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapGroupSettings;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.beans.sso.LdapUserSettings;
import software.wings.helpers.ext.ldap.LdapResponse;

import com.google.inject.Inject;
import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.Collection;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
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
  private final Validator validator;

  @Override
  public RestResponse<LdapTestResponse> validateLdapConnectionSettings(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, LdapSettings settings) {
    validateLdapSettings(settings);
    LdapTestResponse ldapTestResponse =
        ngLdapService.validateLdapConnectionSettings(accountIdentifier, orgIdentifier, projectIdentifier, settings);
    return new RestResponse<>(ldapTestResponse);
  }

  @Override
  public RestResponse<LdapTestResponse> validateLdapUserSettings(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, LdapSettings settings) {
    validateLdapSettings(settings);
    LdapTestResponse ldapTestResponse =
        ngLdapService.validateLdapUserSettings(accountIdentifier, orgIdentifier, projectIdentifier, settings);
    return new RestResponse<>(ldapTestResponse);
  }

  @Override
  public RestResponse<LdapTestResponse> validateLdapGroupSettings(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, LdapSettings settings) {
    validateLdapSettings(settings);
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

  @Override
  public RestResponse<LdapResponse> postLdapAuthenticationTest(
      String accountId, String orgIdentifier, String projectIdentifier, String email, String password) {
    return new RestResponse<>(
        ngLdapService.testLDAPLogin(accountId, orgIdentifier, projectIdentifier, email, password));
  }

  @Override
  public RestResponse<Void> syncUserGroupLinkedToLDAP(
      String userGroupId, String accountId, String orgIdentifier, String projectIdentifier) {
    ngLdapService.syncAUserGroupJob(userGroupId, accountId, orgIdentifier, projectIdentifier);
    return new RestResponse<>(null);
  }

  public void validateLdapSettings(LdapSettings ldapSettings) {
    if (isEmpty(ldapSettings.getAccountId())) {
      throw new InvalidRequestException("accountId cannot be empty for ldap settings");
    }
    if (isNull(ldapSettings.getConnectionSettings())) {
      throw new InvalidRequestException("Connection settings are not defined for ldap settings");
    }

    Set<ConstraintViolation<LdapConnectionSettings>> connectionSettingsViolations =
        validator.validate(ldapSettings.getConnectionSettings());
    if (!connectionSettingsViolations.isEmpty()) {
      throw new JerseyViolationException(connectionSettingsViolations, null);
    }

    if (isNotEmpty(ldapSettings.getUserSettingsList())) {
      ldapSettings.getUserSettingsList().forEach(userSetting -> {
        Set<ConstraintViolation<LdapUserSettings>> userSettingsViolations = validator.validate(userSetting);
        if (!userSettingsViolations.isEmpty()) {
          throw new JerseyViolationException(userSettingsViolations, null);
        }
      });
    }

    if (isNotEmpty(ldapSettings.getGroupSettingsList())) {
      ldapSettings.getGroupSettingsList().forEach(groupSetting -> {
        Set<ConstraintViolation<LdapGroupSettings>> groupSettingsViolations = validator.validate(groupSetting);
        if (!groupSettingsViolations.isEmpty()) {
          throw new JerseyViolationException(groupSettingsViolations, null);
        }
      });
    }
  }
}
