package io.harness.ngsettings.settings;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngsettings.entities.SettingConfiguration;
import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Set;

@OwnedBy(HarnessTeam.PL)
@Value
@Builder
public class SettingsConfig {
    @NotEmpty String name;
    int version;
    @NotNull Set<SettingConfiguration> settings;
}
