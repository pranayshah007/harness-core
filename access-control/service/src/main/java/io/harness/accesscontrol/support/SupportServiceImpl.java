/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.support;

import io.harness.accesscontrol.support.persistence.SupportPreferenceDao;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.user.UserMetadata;
import io.harness.remote.client.CGRestUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Singleton
@ValidateOnExecution
@Slf4j
public class SupportServiceImpl implements SupportService {
  private final SupportPreferenceDao supportPreferenceDao;
  private final AccountClient accountClient;

  @Inject
  public SupportServiceImpl(
      SupportPreferenceDao supportPreferenceDao, @Named("PRIVILEGED") AccountClient accountClient) {
    this.supportPreferenceDao = supportPreferenceDao;
    this.accountClient = accountClient;
  }

  @Override
  public SupportPreference fetchSupportPreference(String accountIdentifier) {
    try {
      Optional<SupportPreference> supportPreferenceOpt = supportPreferenceDao.get(accountIdentifier);
      return supportPreferenceOpt.orElseGet(() -> syncSupportPreferenceFromRemote(accountIdentifier));
    } catch (Exception e) {
      log.error("Support Preference couldn't be synced due to error, returning support as false", e);
      return SupportPreference.builder().accountIdentifier(accountIdentifier).isSupportEnabled(false).build();
    }
  }

  @Override
  public SupportPreference syncSupportPreferenceFromRemote(String accountIdentifier) {
    Boolean isHarnessSupportEnabled =
        CGRestUtils.getResponse(accountClient.checkIfHarnessSupportEnabledForAccount(accountIdentifier),
            "Could not find the support configuration with the given accountIdentifier");
    SupportPreference supportPreference = SupportPreference.builder()
                                              .accountIdentifier(accountIdentifier)
                                              .isSupportEnabled(Boolean.TRUE.equals(isHarnessSupportEnabled))
                                              .build();
    return supportPreferenceDao.upsert(supportPreference);
  }

  @Override
  public Optional<SupportPreference> deleteSupportPreferenceIfPresent(String accountIdentifier) {
    return supportPreferenceDao.deleteIfPresent(accountIdentifier);
  }

  @Override
  public Set<String> fetchSupportUsers() {
    Collection<UserMetadata> supportUsers = CGRestUtils.getResponse(accountClient.listAllHarnessSupportUsers(),
        "Could not find the support configuration with the given accountIdentifier");
    return supportUsers.stream().map(UserMetadata::getId).collect(Collectors.toSet());
  }
}
