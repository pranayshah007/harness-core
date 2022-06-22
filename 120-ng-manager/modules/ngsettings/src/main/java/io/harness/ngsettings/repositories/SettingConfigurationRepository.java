package io.harness.ngsettings.repositories;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.setting.SettingCategory;
import io.harness.ngsettings.entities.SettingConfiguration;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.PagingAndSortingRepository;

@OwnedBy(PL)
@HarnessRepo
public interface SettingConfigurationRepository extends PagingAndSortingRepository {
  List<SettingConfiguration> findAllByCategoryAndAllowedScopes(SettingCategory category, String scope);
  Optional<SettingConfiguration> findAByCategoryAndIdentifier(SettingCategory category, String identifier);
}
