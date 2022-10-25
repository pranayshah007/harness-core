/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.ldap;

import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;
import org.ldaptive.Connection;
import org.ldaptive.ConnectionFactory;
import org.ldaptive.LdapException;
import org.ldaptive.SearchFilter;
import org.ldaptive.SearchOperation;
import org.ldaptive.SearchRequest;
import org.ldaptive.SearchResult;
import org.ldaptive.SearchScope;
import org.ldaptive.auth.SearchDnResolver;
import org.ldaptive.handler.RecursiveEntryHandler;
import org.ldaptive.referral.SearchReferralHandler;

// TODO: Move these to  portal/rest/src/main/java/software/wings/security
@Getter
@Builder(builderClassName = "Builder", buildMethodName = "internalBuild")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class LdapSearch implements LdapValidator {
  @NotNull ConnectionFactory connectionFactory;
  @NotNull String baseDN;
  @NotBlank String searchFilter;

  @NotBlank String bindDn;
  @NotBlank String bindCredential;

  private static final int pageSize = 1000;
  private static final String SAM_ACCOUNT_NAME = "sAMAccountName";

  /**
   * This is required in case of Oracle Directory services
   */
  String fallBackSearchFilter;
  @NotNull @Default SearchScope searchScope = SearchScope.SUBTREE;
  int limit;
  boolean referralsEnabled;
  int maxReferralHops;
  String recursiveSearchAttr;
  String recursiveMergeAttr;

  public static class Builder {
    public LdapSearch build() {
      LdapSearch search = internalBuild();
      search.validate(log);
      return search;
    }
  }

  @SuppressWarnings("PMD")
  public SearchResult execute(String... attrs) throws LdapException {
    SearchRequest request = new SearchRequest();

    request.setBaseDn(baseDN);
    request.setSearchFilter(new SearchFilter(searchFilter));
    request.setSizeLimit(limit);
    request.setSearchScope(searchScope);

    if (StringUtils.isNotBlank(recursiveSearchAttr) && StringUtils.isNotBlank(recursiveMergeAttr)) {
      request.setSearchEntryHandlers(new RecursiveEntryHandler(recursiveSearchAttr, recursiveMergeAttr));
    }

    if (ArrayUtils.isNotEmpty(attrs)) {
      request.setReturnAttributes(attrs);
    }

    if (referralsEnabled) {
      request.setReferralHandler(new SearchReferralHandler(maxReferralHops));
    }

    log.info("LdapSearchRequest : [{}]", request);

    try (Connection connection = connectionFactory.getConnection()) {
      connection.open();
      SearchOperation search = new SearchOperation(connection);
      return search.execute(request).getResult();
    } catch (Exception e) {
      log.error("Error querying second time to LDAP server with old logic ", e);
      throw e;
    }
  }

  public SearchDnResolver getSearchDnResolver(String userFilter) {
    SearchDnResolver resolver = new SearchDnResolver(connectionFactory);
    resolver.setBaseDn(baseDN);
    resolver.setUserFilter(userFilter);
    if (searchScope == SearchScope.SUBTREE) {
      resolver.setSubtreeSearch(true);
    }
    return resolver;
  }
}
