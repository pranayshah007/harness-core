/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.ldap;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import software.wings.helpers.ext.ldap.LdapResponse;
import software.wings.helpers.ext.ldap.LdapUserConfig;

import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.ldaptive.SearchResult;

@OwnedBy(PL)
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LdapGetUsersResponse extends AbstractLdapResponse {
  SearchResult searchResult;
  String groupBaseDn;

  LdapGetUsersResponse(@NotNull LdapUserConfig ldapUserConfig, @NotNull LdapResponse ldapResponse,
      @NotNull SearchResult searchResult, String groupBaseDn) {
    super(ldapUserConfig, ldapResponse);
    this.searchResult = searchResult;
    this.groupBaseDn = groupBaseDn;
  }
}
