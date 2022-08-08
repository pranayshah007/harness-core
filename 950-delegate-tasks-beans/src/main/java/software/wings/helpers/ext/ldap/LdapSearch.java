/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.ldap;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.SearchControls;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;
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
import org.ldaptive.DefaultConnectionFactory;
import org.ldaptive.LdapAttribute;
import org.ldaptive.LdapEntry;
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

    if (StringUtils.isNotBlank(searchFilter) && searchFilter.toLowerCase().contains("group")) {
      try (Connection connection = connectionFactory.getConnection()) {
        connection.open();
        SearchOperation search = new SearchOperation(connection);
        return search.execute(request).getResult();
      }
    } else {
      log.info("Using ldap paginated query with searchfilter {} and baseDN {}", searchFilter, baseDN);
      Hashtable<String, Object> env = new Hashtable<>();

      env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
      env.put(Context.PROVIDER_URL, ((DefaultConnectionFactory) connectionFactory).getConnectionConfig().getLdapUrl());
      env.put(Context.SECURITY_PRINCIPAL, bindDn);
      env.put(Context.SECURITY_CREDENTIALS, bindCredential);
      env.put(Context.SECURITY_AUTHENTICATION, "simple");

      try {
        LdapContext ctx = new InitialLdapContext(env, null);

        byte[] cookie = null;
        ctx.setRequestControls(new Control[] {new PagedResultsControl(pageSize, Control.NONCRITICAL)});
        int total;
        Collection<LdapEntry> entries = new ArrayList<>();
        do {
          NamingEnumeration results = ctx.search(baseDN, searchFilter, new SearchControls());

          while (results != null && results.hasMore()) {
            javax.naming.directory.SearchResult entry = (javax.naming.directory.SearchResult) results.next();

            List<LdapAttribute> ldapAttributeList = new ArrayList<>();
            ldapAttributeList.add(new LdapAttribute("cn", (String) entry.getAttributes().get("cn").get()));
            ldapAttributeList.add(new LdapAttribute("mail", (String) entry.getAttributes().get("mail").get()));
            ldapAttributeList.add(new LdapAttribute("uid", (String) entry.getAttributes().get("uid").get()));

            if (entry.getAttributes().get(SAM_ACCOUNT_NAME) != null
                && entry.getAttributes().get(SAM_ACCOUNT_NAME).get() != null) {
              log.info("samAccountName for mail {} is {}", entry.getAttributes().get("mail").get(),
                  entry.getAttributes().get(SAM_ACCOUNT_NAME).get());
              ldapAttributeList.add(
                  new LdapAttribute(SAM_ACCOUNT_NAME, (String) entry.getAttributes().get(SAM_ACCOUNT_NAME).get()));
            }

            String dn = "dn=uid" + entry.getAttributes().get("uid").get() + baseDN;
            LdapEntry ldapEntry = new LdapEntry(dn, ldapAttributeList);
            entries.add(ldapEntry);
          }

          Control[] controls = ctx.getResponseControls();
          if (controls != null) {
            for (Control control : controls) {
              if (control instanceof PagedResultsResponseControl) {
                PagedResultsResponseControl prrc = (PagedResultsResponseControl) control;
                total = prrc.getResultSize();
                if (total != 0) {
                  log.info("Total number of members found are {} with query {}", total, searchFilter);
                } else {
                  log.info("No members found with query {}", searchFilter);
                }
                cookie = prrc.getCookie();
              }
            }
          } else {
            log.info("No controls were sent from the server");
          }
          ctx.setRequestControls(new Control[] {new PagedResultsControl(pageSize, cookie, Control.CRITICAL)});
        } while (cookie != null);
        ctx.close();
        if (isNotEmpty(entries)) {
          return new SearchResult(entries);
        }
      } catch (Exception e) {
        log.error("Error querying to ldap server with pagination", e);
      }
      try (Connection connection = connectionFactory.getConnection()) {
        log.info("Trying to query second time to LDAP server with old logic with searchfilter {} and baseDN {}",
            searchFilter, baseDN);
        connection.open();
        SearchOperation search = new SearchOperation(connection);
        return search.execute(request).getResult();
      } catch (Exception e) {
        log.error("Error querying second time to LDAP server with old logic ", e);
      }
    }
    return null;
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
