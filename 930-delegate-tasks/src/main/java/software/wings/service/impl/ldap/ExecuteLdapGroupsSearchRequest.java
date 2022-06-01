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
import software.wings.helpers.ext.ldap.LdapResponse;
import software.wings.helpers.ext.ldap.LdapResponse.Status;
import software.wings.helpers.ext.ldap.LdapSearch;

import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.ldaptive.LdapException;
import org.ldaptive.SearchResult;

/**
 * Function to fetch ldap group search results
 */
@OwnedBy(PL)
@Slf4j
public class ExecuteLdapGroupsSearchRequest implements Function<LdapListGroupsRequest, LdapListGroupsResponse> {
  @Override
  public LdapListGroupsResponse apply(LdapListGroupsRequest ldapListGroupsRequest) {
    Status searchStatus = Status.FAILURE;
    String searchStatusMessage = null;

    LdapSearch ldapSearch = ldapListGroupsRequest.getLdapSearch();
    LdapGroupConfig ldapGroupConfig = ldapListGroupsRequest.getLdapGroupConfig();

    SearchResult searchResult = null;
    try {
      searchResult = ldapSearch.execute(ldapListGroupsRequest.getReturnArguments());
    } catch (LdapException le) {
      log.error("LdapException occurred while searchGroupbyName for base {} and searchQuery {}", ldapSearch.getBaseDN(),
          ldapSearch.getSearchFilter(), le);
      searchStatusMessage = le.getResultCode().toString();
    }

    if (searchResult != null) {
      searchStatus = Status.SUCCESS;
    }

    LdapResponse ldapResponse = LdapResponse.builder().status(searchStatus).message(searchStatusMessage).build();
    return new LdapListGroupsResponse(searchResult, ldapResponse, ldapGroupConfig);
  }
}
