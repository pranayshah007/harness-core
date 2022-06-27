package io.harness.ldap.resource;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ldap.service.NGLdapService;
import io.harness.rest.RestResponse;

import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapSettingsMapper;
import software.wings.beans.sso.LdapTestResponse;

import com.google.inject.Inject;
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
}