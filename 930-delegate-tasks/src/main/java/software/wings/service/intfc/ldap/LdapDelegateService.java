/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.ldap;

import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.TaskType;
import software.wings.beans.dto.LdapSettings;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.delegatetasks.DelegateTaskType;
import software.wings.helpers.ext.ldap.LdapResponse;

import java.util.Collection;

/**
 *  Delegate Service Interface for LDAP API's.
 * Created by Pranjal on 08/21/2018
 */
public interface LdapDelegateService {
  /**
   * API to test LDAP connection settings for given {@link LdapSettings}
   * @param settings
   * @param encryptedDataDetail
   * @return
   */
  @DelegateTaskType(TaskType.LDAP_TEST_CONN_SETTINGS)
  LdapTestResponse validateLdapConnectionSettings(LdapSettings settings, EncryptedDataDetail encryptedDataDetail);

  /**
   * API to test LDAP User settings for given {@link LdapSettings}
   * @param settings
   * @param encryptedDataDetail
   * @return
   */
  @DelegateTaskType(TaskType.LDAP_TEST_USER_SETTINGS)
  LdapTestResponse validateLdapUserSettings(LdapSettings settings, EncryptedDataDetail encryptedDataDetail);

  /**
   * API to test LDAP Group settings for given {@link LdapSettings}
   * @param settings
   * @param encryptedDataDetail
   * @return
   */
  @DelegateTaskType(TaskType.LDAP_TEST_GROUP_SETTINGS)
  LdapTestResponse validateLdapGroupSettings(LdapSettings settings, EncryptedDataDetail encryptedDataDetail);

  /**
   * API to authenticate username and password using ldap settings
   * @param settings
   * @param settingsEncryptedDataDetail
   * @param username
   * @param passwordEncryptedDataDetail
   * @return
   */
  @DelegateTaskType(TaskType.LDAP_AUTHENTICATION)
  LdapResponse authenticate(LdapSettings settings, EncryptedDataDetail settingsEncryptedDataDetail, String username,
      EncryptedDataDetail passwordEncryptedDataDetail);

  /**
   * API to search ldap groups by name
   * @param settings
   * @param encryptedDataDetail
   * @return
   */
  @DelegateTaskType(TaskType.LDAP_SEARCH_GROUPS)
  Collection<LdapGroupResponse> searchGroupsByName(
      LdapSettings settings, EncryptedDataDetail encryptedDataDetail, String name);

  /**
   * API to fetch one ldap group by dn populated with users
   *
   * @param settings
   * @param encryptedDataDetail
   * @param dn
   * @return
   */
  @DelegateTaskType(TaskType.LDAP_FETCH_GROUP)
  LdapGroupResponse fetchGroupByDn(LdapSettings settings, EncryptedDataDetail encryptedDataDetail, String dn);
}
