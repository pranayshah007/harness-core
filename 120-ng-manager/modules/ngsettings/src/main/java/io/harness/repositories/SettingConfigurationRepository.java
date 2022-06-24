/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.entities.SettingConfiguration;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.PagingAndSortingRepository;

@OwnedBy(PL)
@HarnessRepo
public interface SettingConfigurationRepository extends PagingAndSortingRepository<SettingConfiguration, String> {
  List<SettingConfiguration> findByCategoryAndAllowedScopesIn(SettingCategory category, List<String> scopes);
  List<SettingConfiguration> findByAllowedScopesInAndCategory(List<String> scopes, SettingCategory category);
  Optional<SettingConfiguration> findByCategoryAndIdentifier(SettingCategory category, String identifier);
  List<SettingConfiguration> findByCategoryAndAllowedValuesIn(SettingCategory category, List<Integer> values);
  List<SettingConfiguration> findByAllowedScopesIn(List<String> scopes);
  List<SettingConfiguration> findByAllowedValuesIn(List<Integer> values);
  List<SettingConfiguration> findByAllowedValuesIn(SettingCategory category, List<Integer> values);
}
