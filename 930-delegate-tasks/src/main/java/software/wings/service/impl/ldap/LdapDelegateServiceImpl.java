/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.ldap;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.dto.LdapSettings;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.beans.sso.LdapTestResponse.Status;
import software.wings.beans.sso.LdapUserResponse;
import software.wings.helpers.ext.ldap.LdapConstants;
import software.wings.helpers.ext.ldap.LdapGroupConfig;
import software.wings.helpers.ext.ldap.LdapResponse;
import software.wings.helpers.ext.ldap.LdapUserConfig;
import software.wings.service.intfc.ldap.LdapDelegateService;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ldaptive.LdapAttribute;
import org.ldaptive.LdapEntry;
import org.ldaptive.LdapException;
import org.ldaptive.ResultCode;
import org.ldaptive.SearchResult;

/**
 * Impl for Ldap Delegate Service  {@link LdapDelegateService}.
 * Created by Pranjal on 08/21/2018
 */
@OwnedBy(PL)
@Singleton
@Slf4j
public class LdapDelegateServiceImpl implements LdapDelegateService {
  @Inject private EncryptionService encryptionService;

  @Override
  public LdapTestResponse validateLdapConnectionSettings(
      LdapSettings settings, EncryptedDataDetail encryptedDataDetail) {
    log.info("Initiating validateLdapConnectionSettings with ldap settings : {}", settings);
    settings.decryptFields(encryptedDataDetail, encryptionService);
    LdapHelper helper = new LdapHelper(settings.getConnectionSettings());
    LdapResponse response = helper.validateConnectionConfig();
    if (response.getStatus() == LdapResponse.Status.SUCCESS) {
      return LdapTestResponse.builder().status(Status.SUCCESS).message(response.getMessage()).build();
    }
    return LdapTestResponse.builder().status(Status.FAILURE).message(response.getMessage()).build();
  }

  @Override
  public LdapTestResponse validateLdapUserSettings(LdapSettings settings, EncryptedDataDetail encryptedDataDetail) {
    log.info("Initiating validateLdapUserSettings with ldap settings : {}", settings);
    settings.decryptFields(encryptedDataDetail, encryptionService);
    LdapHelper helper = new LdapHelper(settings.getConnectionSettings());
    LdapResponse response = helper.validateUserConfig(settings);
    if (response.getStatus() == LdapResponse.Status.SUCCESS) {
      return LdapTestResponse.builder().status(Status.SUCCESS).message(response.getMessage()).build();
    }
    return LdapTestResponse.builder().status(Status.FAILURE).message(response.getMessage()).build();
  }

  @Override
  public LdapTestResponse validateLdapGroupSettings(LdapSettings settings, EncryptedDataDetail encryptedDataDetail) {
    log.info("Initiating validateLdapGroupSettings with ldap settings : {}", settings);
    settings.decryptFields(encryptedDataDetail, encryptionService);
    LdapHelper helper = new LdapHelper(settings.getConnectionSettings());
    LdapResponse response = helper.validateGroupConfig(settings);
    if (response.getStatus() == LdapResponse.Status.SUCCESS) {
      return LdapTestResponse.builder().status(Status.SUCCESS).message(response.getMessage()).build();
    }
    return LdapTestResponse.builder().status(Status.FAILURE).message(response.getMessage()).build();
  }

  @Override
  public LdapResponse authenticate(LdapSettings settings, EncryptedDataDetail settingsEncryptedDataDetail,
      String username, EncryptedDataDetail passwordEncryptedDataDetail) {
    settings.decryptFields(settingsEncryptedDataDetail, encryptionService);
    String password = new String(encryptionService.getDecryptedValue(passwordEncryptedDataDetail, false));
    LdapHelper helper = new LdapHelper(settings.getConnectionSettings());
    return helper.authenticate(settings, username, password);
  }

  @VisibleForTesting
  LdapGroupResponse buildLdapGroupResponse(LdapEntry group, LdapGroupConfig settings) {
    Set<String> availableAttrs = Sets.newHashSet(group.getAttributeNames());
    String name = group.getAttribute(settings.getNameAttr()).getStringValue();
    String description = StringUtils.EMPTY;
    String descriptionAttr = settings.getDescriptionAttr();

    if (availableAttrs.contains(descriptionAttr)) {
      description = group.getAttribute(settings.getDescriptionAttr()).getStringValue();
    }

    int totalMembers = Integer.parseInt(group.getAttribute(LdapConstants.GROUP_SIZE_ATTR).getStringValue());
    boolean selectable = true;
    String message = "";

    return LdapGroupResponse.builder()
        .dn(group.getDn())
        .name(name)
        .description(description)
        .totalMembers(totalMembers)
        .selectable(selectable)
        .message(message)
        .build();
  }

  private LdapUserResponse buildLdapUserResponse(LdapEntry user, LdapUserConfig userConfig) {
    String name;
    String email = null;
    LdapAttribute ldapEmailAttribute = user.getAttribute(userConfig.getEmailAttr());
    if (ldapEmailAttribute != null) {
      email = ldapEmailAttribute.getStringValue();
    } else {
      log.error(
          "UserConfig email attribute = {} is missing for LdapEntry user object = {}", userConfig.getEmailAttr(), user);
    }

    if (Arrays.asList(user.getAttributeNames()).contains(userConfig.getDisplayNameAttr())) {
      if (user.getAttribute(userConfig.getDisplayNameAttr()) != null) {
        name = user.getAttribute(userConfig.getDisplayNameAttr()).getStringValue();
      } else {
        // This case didn't happen till now, but adding due to infer checks
        name = null;
      }
    } else {
      name = email;
    }

    String externalUserId = "";
    log.info("LDAPIterator: user with email {} ldap entry is {}", email, user);
    if (isNotEmpty(user.getAttributeNames())
        && Arrays.asList(user.getAttributeNames()).contains(userConfig.getUidAttr())
        && user.getAttribute(userConfig.getUidAttr()) != null) {
      log.info("LDAP user with email {} user uid set as {}", email,
          user.getAttribute(userConfig.getUidAttr()).getStringValue());
      if (StringUtils.isNotEmpty(user.getAttribute(userConfig.getUidAttr()).getStringValue())) {
        externalUserId = user.getAttribute(userConfig.getUidAttr()).getStringValue().toLowerCase();
      }
    }

    if (isNotEmpty(user.getAttributeNames())
        && Arrays.asList(user.getAttributeNames()).contains(userConfig.getSamAccountNameAttr())
        && user.getAttribute(userConfig.getSamAccountNameAttr()) != null) {
      log.info("LDAP user with email {} samAccountName set as {}", email,
          user.getAttribute(userConfig.getSamAccountNameAttr()).getStringValue());
      if (StringUtils.isNotEmpty(user.getAttribute(userConfig.getSamAccountNameAttr()).getStringValue())) {
        externalUserId = user.getAttribute(userConfig.getSamAccountNameAttr()).getStringValue().toLowerCase();
      }
    }

    log.info("LDAP user response with name {} and email {} and userId {}", name, email, externalUserId);
    return LdapUserResponse.builder().dn(user.getDn()).name(name).email(email).userId(externalUserId).build();
  }

  @Override
  public Collection<LdapGroupResponse> searchGroupsByName(
      LdapSettings settings, EncryptedDataDetail encryptedDataDetail, String nameQuery) {
    settings.decryptFields(encryptedDataDetail, encryptionService);
    LdapHelper helper = new LdapHelper(settings.getConnectionSettings());
    try {
      List<LdapListGroupsResponse> ldapListGroupsResponses = helper.searchGroupsByName(settings, nameQuery);
      return createLdapGroupResponse(helper, ldapListGroupsResponses, settings);
    } catch (LdapException e) {
      throw new LdapDelegateException(e.getResultCode().toString(), e);
    }
  }

  private Collection<LdapGroupResponse> createLdapGroupResponse(LdapHelper helper,
      List<LdapListGroupsResponse> ldapListGroupsResponses, LdapSettings ldapSettings) throws LdapException {
    Collection<LdapGroupResponse> ldapGroupResponse = new ArrayList<>();
    for (LdapListGroupsResponse ldapListGroupsResponse : ldapListGroupsResponses) {
      if (LdapResponse.Status.SUCCESS == ldapListGroupsResponse.getLdapResponse().getStatus()) {
        helper.populateGroupSize(ldapListGroupsResponse.getSearchResult(), ldapSettings);
        Collection<LdapEntry> entries = ldapListGroupsResponse.getSearchResult().getEntries();
        for (LdapEntry entry : entries) {
          ldapGroupResponse.add(buildLdapGroupResponse(entry, ldapListGroupsResponse.getLdapGroupConfig()));
        }
      }
    }
    return ldapGroupResponse;
  }

  @Override
  public LdapGroupResponse fetchGroupByDn(LdapSettings settings, EncryptedDataDetail encryptedDataDetail, String dn) {
    settings.decryptFields(encryptedDataDetail, encryptionService);
    LdapHelper helper = new LdapHelper(settings.getConnectionSettings());
    LdapGroupResponse groupResponse;
    try {
      LdapListGroupsResponse listGroupsResponse = helper.getGroupByDn(settings, dn);

      if (listGroupsResponse == null || listGroupsResponse.getLdapResponse() == null
          || LdapResponse.Status.SUCCESS != listGroupsResponse.getLdapResponse().getStatus()) {
        log.error("LDAP : The call to fetch the group failed for ldapSettingsId {} and accountId {}",
            settings.getUuid(), settings.getAccountId());
        return null;
      }

      SearchResult groups = listGroupsResponse.getSearchResult();
      helper.populateGroupSize(groups, settings);

      // If there are no entries in the group.
      LdapEntry group = groups.getEntries().isEmpty() ? null : groups.getEntries().iterator().next();
      if (null == group) {
        log.info("No entries found in group");
        return null;
      }

      groupResponse = buildLdapGroupResponse(group, listGroupsResponse.getLdapGroupConfig());
      if (!groupResponse.isSelectable()) {
        return groupResponse;
      }

      List<LdapGetUsersResponse> ldapGetUsersResponses = helper.listGroupUsers(settings, Collections.singletonList(dn));

      Collection<LdapUserResponse> userResponses =
          ldapGetUsersResponses.stream()
              .map(ldapGetUsersResponse
                  -> ldapGetUsersResponse.getSearchResult()
                         .getEntries()
                         .stream()
                         .map(user -> buildLdapUserResponse(user, ldapGetUsersResponse.getLdapUserConfig()))
                         .collect(Collectors.toList()))
              .flatMap(Collection::stream)
              .collect(Collectors.toList());
      log.info("LDAP : Users set in Group response {}", userResponses);
      groupResponse.setUsers(userResponses);
      return groupResponse;
    } catch (LdapException e) {
      if (e.getResultCode() == ResultCode.NO_SUCH_OBJECT) {
        log.error("Ldap [{}] received while fetching group by dn: [{}] for Ldap Name: [{}]",
            e.getResultCode().toString(), dn, settings.getDisplayName());
        return null;
      }
      throw new LdapDelegateException(e.getResultCode().toString(), e);
    }
  }
}