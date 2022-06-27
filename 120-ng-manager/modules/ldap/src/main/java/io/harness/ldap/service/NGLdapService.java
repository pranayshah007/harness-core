package io.harness.ldap.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.dto.LdapSettings;
import software.wings.beans.sso.LdapTestResponse;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
@OwnedBy(HarnessTeam.PL)
public interface NGLdapService {
  LdapTestResponse validateLdapConnectionSettings(
      @NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier, @Valid LdapSettings settings);
}