/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.ldap;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import software.wings.helpers.ext.ldap.LdapGroupConfig;
import software.wings.helpers.ext.ldap.LdapSearch;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@OwnedBy(PL)
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LdapListGroupsRequest {
  LdapSearch ldapSearch;
  String[] returnArguments;
  LdapGroupConfig ldapGroupConfig;
  int responseTimeoutInSeconds;

  public LdapListGroupsRequest(
      LdapSearch ldapSearch, String[] returnArguments, LdapGroupConfig ldapGroupConfig, int responseTimeoutInSeconds) {
    this.ldapSearch = ldapSearch;
    this.returnArguments = returnArguments != null ? returnArguments.clone() : null;
    this.ldapGroupConfig = ldapGroupConfig;
    this.responseTimeoutInSeconds = responseTimeoutInSeconds;
  }
}
