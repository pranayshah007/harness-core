/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.dto.LdapSettings;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapTestResponse;

import java.util.Collection;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@OwnedBy(HarnessTeam.PL)
public interface NGLdapService {
  LdapTestResponse validateLdapConnectionSettings(
      @NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier, @Valid LdapSettings settings);
  Collection<LdapGroupResponse> searchLdapGroupsByName(@NotNull String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NotNull String ldapId, @NotNull String name);
}
