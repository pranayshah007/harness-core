/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.sso;

import static io.harness.annotations.dev.HarnessModule._950_NG_AUTHENTICATION_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.helpers.ext.ldap.LdapConstants;
import software.wings.helpers.ext.ldap.LdapUserConfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(PL)
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
@TargetModule(_950_NG_AUTHENTICATION_SERVICE)
public class LdapUserSettings implements LdapUserConfig {
  @JsonProperty @NotBlank String baseDN;
  @JsonProperty @NotBlank String searchFilter = LdapConstants.DEFAULT_USER_SEARCH_FILTER;
  @JsonProperty @NotBlank String uidAttr = "uid";
  @JsonProperty @NotBlank String samAccountNameAttr = "sAMAccountName";
  @JsonProperty @NotBlank String emailAttr = "mail";
  @JsonProperty @NotBlank String displayNameAttr = "cn";
  @JsonProperty @NotBlank String groupMembershipAttr = "memberOf";

  @JsonIgnore
  @Override
  public String getUserFilter() {
    String userFilter = String.format("(&(%s={user})%s)", emailAttr, searchFilter);
    log.info("LDAP UserFilter is {}", userFilter);
    return userFilter;
  }

  @JsonIgnore
  @Override
  public String getLoadUsersFilter() {
    String loadUserFilter = String.format("(&%s(%s=*))", searchFilter, emailAttr);
    log.info("LDAP loadUserFilter is {}", loadUserFilter);
    return loadUserFilter;
  }

  @JsonIgnore
  @Override
  public String getGroupMembershipFilter(String groupDn) {
    String groupMembershipFilter = String.format("(&%s(%s:%s:=%s)(%s=*))", searchFilter, groupMembershipAttr,
        LdapConstants.LDAP_MATCHING_RULE_IN_CHAIN, groupDn, emailAttr);
    log.info("LDAP groupMembershipFilter is {}", groupMembershipFilter);
    return groupMembershipFilter;
  }

  @JsonIgnore
  @Override
  public String getFallbackGroupMembershipFilter(String groupDn) {
    String fallBackGroupMembershipFilter = String.format("(&%s(%s=%s))", searchFilter, groupMembershipAttr, groupDn);
    log.info("LDAP fallBackGroupMembershipFilter is {}", fallBackGroupMembershipFilter);
    return fallBackGroupMembershipFilter;
  }

  @JsonIgnore
  @Override
  public String[] getReturnAttrs() {
    return new String[] {emailAttr, displayNameAttr, uidAttr, samAccountNameAttr};
  }
}
