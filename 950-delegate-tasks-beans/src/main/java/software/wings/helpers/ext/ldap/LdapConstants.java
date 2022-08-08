/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.ldap;

import lombok.experimental.UtilityClass;

@UtilityClass
public class LdapConstants {
  public static final int DEFAULT_CONNECTION_PORT = 636;
  public static final boolean DEFAULT_SSL_STATE = true;
  public static final int DEFAULT_MAX_REFERRAL_HOPS = 1;
  public static final int DEFAULT_CONNECT_TIMEOUT = 5000;
  public static final int DEFAULT_RESPONSE_TIMEOUT = 5000;
  public static final String BIND_PASSWORD_KEY = "bindPassword";
  public static final String USER_PASSWORD_KEY = "password";
  public static final String LDAP_MATCHING_RULE_IN_CHAIN = "1.2.840.113556.1.4.1941";
  public static final int MIN_USER_QUERY_SIZE = 10;
  public static final int MIN_GROUP_QUERY_SIZE = 10;
  public static final int MAX_GROUP_SEARCH_SIZE = 10;
  public static final String CONNECTION_SUCCESS = "Connection successful.";
  public static final String USER_CONFIG_SUCCESS =
      "Configuration looks good. Server returned non-zero number of records.";
  public static final String USER_CONFIG_FAILURE =
      "Please check configuration. Server returned zero records for the configuration.";
  public static final String GROUP_CONFIG_SUCCESS =
      "Configuration looks good. Server returned non-zero number of records.";
  public static final String GROUP_CONFIG_FAILURE =
      "Please check configuration. Server returned zero records for the configuration.";
  public static final String DEFAULT_USER_SEARCH_FILTER = "(objectClass=person)";
  public static final String DEFAULT_GROUP_SEARCH_FILTER = "(objectClass=group)";
  public static final String USER_NOT_FOUND = "User not found";
  public static final String AUTHENTICATION_SUCCESS = "Authentication successful";
  public static final String MASKED_STRING = "*****";
  public static final String GROUP_SIZE_ATTR = "groupSize";
  public static final int MAX_GROUP_MEMBERS_LIMIT = 1500;
  public static final String GROUP_MEMBERS_EXCEEDED =
      String.format("Group has more than %s members.", MAX_GROUP_MEMBERS_LIMIT);
  public static final String USER_GROUP_SYNC_FAILED = "User group syncing failed for SSO Provider: %s";
  public static final String USER_GROUP_SYNC_INVALID_REMOTE_GROUP =
      "User group syncing failed for group: %s. Remote group could be wrong or empty.";
  public static final String USER_GROUP_SYNC_NOT_ELIGIBLE = "User group %s not eligible for syncing. %s";
  public static final String SSO_PROVIDER_NOT_REACHABLE = "SSO Provider %s not reachable from delegates.";
  public static final String NO_USER_CONFIG_FAILURE =
      "No user config found. Please send at least one user configuration.";
  public static final String INVALID_CREDENTIALS = "INVALID CREDENTIALS";
}
