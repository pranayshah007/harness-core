package io.harness.ngsettings.repositories;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.setting.SettingCategory;
import io.harness.ngsettings.entities.Setting;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.PagingAndSortingRepository;

@OwnedBy(PL)
@HarnessRepo
public interface SettingsRepository extends PagingAndSortingRepository<Setting, String> {
  List<Setting> findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndCategory(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, SettingCategory category);
  Optional<Setting> findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndCategoryAndIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, SettingCategory category,
      String identifier);
}