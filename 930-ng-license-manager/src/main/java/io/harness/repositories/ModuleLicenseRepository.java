/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.ModuleType;
import io.harness.annotation.HarnessRepo;
import io.harness.licensing.entities.modules.ModuleLicense;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@HarnessRepo
@Transactional
public interface ModuleLicenseRepository extends CrudRepository<ModuleLicense, String> {
  List<ModuleLicense> findByAccountIdentifierAndModuleType(String accountIdentifier, ModuleType moduleType);
  List<ModuleLicense> findByAccountIdentifier(String accountIdentifier);
  List<ModuleLicense> findByModuleTypeAndExpiryTimeGreaterThanEqual(ModuleType moduleType, long expiryTime);
  long deleteAllByAccountIdentifier(String accountIdentifier);
}
