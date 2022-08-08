/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.ldap;

import static io.harness.annotations.dev.HarnessModule._360_CG_MANAGER;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.helpers.ext.ldap.LdapResponse;
import software.wings.helpers.ext.ldap.LdapResponse.Status;
import software.wings.helpers.ext.ldap.LdapSearch;
import software.wings.helpers.ext.ldap.LdapUserConfig;

import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.ldaptive.LdapException;
import org.ldaptive.ResultCode;
import org.ldaptive.SearchResult;

@OwnedBy(PL)
@Slf4j
@TargetModule(_360_CG_MANAGER)
public class ExecuteLdapGetUsersRequest implements Function<LdapGetUsersRequest, LdapGetUsersResponse> {
  @Override
  public LdapGetUsersResponse apply(LdapGetUsersRequest ldapGetUsersRequest) {
    Status searchStatus = Status.FAILURE;
    String searchStatusMsg = null;
    ResultCode ldapResultCode = null;

    LdapSearch ldapSearch = ldapGetUsersRequest.getLdapSearch();
    LdapUserConfig ldapUserConfig = ldapGetUsersRequest.getLdapUserConfig();
    SearchResult searchResult = null;
    boolean useRecursiveSearch = isUseRecursiveSearch(ldapGetUsersRequest);
    if (useRecursiveSearch) {
      try {
        searchResult = ldapSearch.execute(ldapUserConfig.getReturnAttrs());
        if (searchResult == null || searchResult.size() == 0) {
          log.info("Got zero results with regular search, trying with fallbackLDAPSearch instead for search to work");
          searchResult = getFallBackLdapSearch(ldapSearch).execute(ldapUserConfig.getReturnAttrs());
        }
      } catch (LdapException le) {
        ldapResultCode = le.getResultCode();
        try {
          log.info("LDAP Search failed errorCode = {} , trying fallback ldapSearch", ldapResultCode);
          searchResult = getFallBackLdapSearch(ldapSearch).execute(ldapUserConfig.getReturnAttrs());
        } catch (LdapException ldapException) {
          ldapResultCode = ldapException.getResultCode();
          log.error("LdapException ErrorCode = {}", ldapResultCode);
          log.error("LdapException exception occurred for user config with baseDN = {}, searchFilter = {}",
              ldapUserConfig.getBaseDN(), ldapUserConfig.getSearchFilter());
        }
        searchStatusMsg = ldapResultCode != null ? ldapResultCode.toString() : "";
      }
    } else {
      try {
        log.info("LDAP isUseOnlyFallBackMechanism is true, trying fallback ldapSearch");
        searchResult = getFallBackLdapSearch(ldapSearch).execute(ldapUserConfig.getReturnAttrs());
      } catch (LdapException ldapException) {
        ldapResultCode = ldapException.getResultCode();
        log.error("LdapException ErrorCode = {}", ldapResultCode);
        log.error("LdapException exception occurred for user config with baseDN = {}, searchFilter = {}",
            ldapUserConfig.getBaseDN(), ldapUserConfig.getSearchFilter());
      }
      searchStatusMsg = ldapResultCode != null ? ldapResultCode.toString() : "";
    }

    if (searchResult != null) { // Scenario when search result
      log.info("The search result for baseDN {} and searchfilter {} found is {}", ldapUserConfig.getBaseDN(),
          ldapUserConfig.getSearchFilter(), searchResult);
      searchStatus = Status.SUCCESS;
    }

    LdapResponse ldapResponse = LdapResponse.builder().status(searchStatus).message(searchStatusMsg).build();

    return new LdapGetUsersResponse(ldapUserConfig, ldapResponse, searchResult, ldapGetUsersRequest.getGroupBaseDn());
  }

  private boolean isUseRecursiveSearch(LdapGetUsersRequest ldapGetUsersRequest) {
    if (ldapGetUsersRequest.getUseRecursiveGroupMembershipSearch() != null) {
      return ldapGetUsersRequest.getUseRecursiveGroupMembershipSearch();
    } else {
      return true;
    }
  }

  private LdapSearch getFallBackLdapSearch(LdapSearch ldapSearch) {
    return LdapSearch.builder()
        .baseDN(ldapSearch.getBaseDN())
        .bindDn(ldapSearch.getBindDn())
        .bindCredential(ldapSearch.getBindCredential())
        .connectionFactory(ldapSearch.getConnectionFactory())
        .referralsEnabled(ldapSearch.isReferralsEnabled())
        .maxReferralHops(ldapSearch.getMaxReferralHops())
        .searchFilter(ldapSearch.getFallBackSearchFilter())
        .build();
  }
}
