package io.harness.ngsettings.settings;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngsettings.entities.SettingConfiguration;

import java.util.Set;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.PL)
@Value
@Builder
public class SettingsConfig {
  @NotEmpty String name;
  int version;
  @NotNull Set<SettingConfiguration> settings;
}
