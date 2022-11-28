package io.harness.ng.core.migration.serviceenvmigrationv2.dto;

import io.harness.annotations.dev.OwnedBy;
import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.NotNull;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@OwnedBy(CDP)
@Value
@Builder
public class TemplateObject {
    @NotNull String templateRef;
    @NotNull String versionLabel;
}
