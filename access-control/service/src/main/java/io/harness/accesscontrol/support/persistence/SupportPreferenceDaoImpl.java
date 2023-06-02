/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.support.persistence;

import static io.harness.accesscontrol.support.persistence.SupportPreferenceDBOMapper.fromDBO;
import static io.harness.accesscontrol.support.persistence.SupportPreferenceDBOMapper.toDBO;

import io.harness.accesscontrol.support.SupportPreference;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import javax.validation.executable.ValidateOnExecution;

@OwnedBy(HarnessTeam.PL)
@ValidateOnExecution
@Singleton
public class SupportPreferenceDaoImpl implements SupportPreferenceDao {
  private final SupportPreferenceRepository repository;

  @Inject
  public SupportPreferenceDaoImpl(SupportPreferenceRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<SupportPreference> get(String accountIdentifier) {
    Optional<SupportPreferenceDBO> savedPreferenceOpt = repository.findByAccountIdentifier(accountIdentifier);
    return savedPreferenceOpt.flatMap(supportPreferenceDBO -> Optional.of(fromDBO(supportPreferenceDBO)));
  }

  @Override
  public SupportPreference upsert(SupportPreference supportPreference) {
    SupportPreferenceDBO currentPreference = toDBO(supportPreference);
    Optional<SupportPreferenceDBO> savedPreferenceOpt =
        repository.findByAccountIdentifier(supportPreference.getAccountIdentifier());
    if (savedPreferenceOpt.isPresent()) {
      SupportPreferenceDBO savedPreference = savedPreferenceOpt.get();
      if (currentPreference.equals(savedPreference)) {
        return fromDBO(savedPreference);
      }
      currentPreference.setId(savedPreference.getId());
      currentPreference.setCreatedAt(savedPreference.getCreatedAt());
      currentPreference.setLastModifiedAt(savedPreference.getLastModifiedAt());
      currentPreference.setNextReconciliationIterationAt(savedPreference.getNextReconciliationIterationAt());
      currentPreference.setVersion(savedPreference.getVersion());
    }
    return fromDBO(repository.save(currentPreference));
  }

  @Override
  public Optional<SupportPreference> deleteIfPresent(String accountIdentifier) {
    Optional<SupportPreferenceDBO> optionalSupportPreferenceDBO =
        repository.deleteByAccountIdentifier(accountIdentifier);
    return optionalSupportPreferenceDBO.map(SupportPreferenceDBOMapper::fromDBO);
  }
}
