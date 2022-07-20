/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.ngsettings.spring;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.entities.Setting;
import io.harness.repositories.ngsettings.custom.SettingRepositoryCustom;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.PagingAndSortingRepository;

@OwnedBy(PL)
@HarnessRepo
public interface SettingRepository extends PagingAndSortingRepository<Setting, String>, SettingRepositoryCustom {
  List<Setting> findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndCategory(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, SettingCategory category);
  List<Setting> findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndCategoryAndGroup(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, SettingCategory category, String group);
  Optional<Setting> findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier);
  List<Setting> findByIdentifier(String identifier);
  void deleteByAccountIdentifierAndIdentifier(String accountIdentifier, String identifier);
  void deleteByAccountIdentifierAndOrgIdentifierAndIdentifier(
      String accountIdentifier, String orgIdentifier, String identifier);
}
