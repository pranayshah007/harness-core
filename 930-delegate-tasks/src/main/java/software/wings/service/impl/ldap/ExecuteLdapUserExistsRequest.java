/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.ldap;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;

import software.wings.helpers.ext.ldap.LdapResponse;
import software.wings.helpers.ext.ldap.LdapResponse.Status;
import software.wings.helpers.ext.ldap.LdapSearch;
import software.wings.helpers.ext.ldap.LdapUserConfig;

import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.ldaptive.LdapException;
import org.ldaptive.auth.SearchDnResolver;
import org.ldaptive.auth.User;

@OwnedBy(PL)
@Slf4j
public class ExecuteLdapUserExistsRequest implements Function<LdapUserExistsRequest, LdapUserExistsResponse> {
  @Override
  public LdapUserExistsResponse apply(LdapUserExistsRequest ldapUserExistsRequest) {
    Status searchStatus;
    String searchStatusMessage = null;

    LdapSearch ldapSearch = ldapUserExistsRequest.getLdapSearch();
    String identifier = ldapUserExistsRequest.getIdentifier();
    LdapUserConfig ldapUserConfig = ldapUserExistsRequest.getLdapUserConfig();

    SearchDnResolver resolver = ldapSearch.getSearchDnResolver(ldapUserConfig.getUserFilter());
    String dn = null;
    try {
      dn = resolver.resolve(new User(identifier));
      searchStatus = Status.SUCCESS;
    } catch (LdapException le) {
      String defaultErrorMessage = String.format(
          "LdapException exception occurred when executing user exists request for baseDN = %s, searchFilter = %s",
          ldapUserConfig.getBaseDN(), ldapUserConfig.getSearchFilter());
      log.warn(defaultErrorMessage + " with exception: " + le.getMessage());
      searchStatus = Status.FAILURE;
      searchStatusMessage = isNull(le.getMessage()) ? defaultErrorMessage : le.getMessage();
    }

    LdapResponse ldapResponse = LdapResponse.builder().status(searchStatus).message(searchStatusMessage).build();

    return new LdapUserExistsResponse(ldapUserConfig, ldapResponse, dn);
  }
}
