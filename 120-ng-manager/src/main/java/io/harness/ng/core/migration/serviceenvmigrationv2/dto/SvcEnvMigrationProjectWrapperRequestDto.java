package io.harness.ng.core.migration.serviceenvmigrationv2.dto;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDP)
@Value
@Builder
public class SvcEnvMigrationProjectWrapperRequestDto {
  @NotNull String orgIdentifier;
  @NotNull String projectIdentifier;
  @NotNull @Schema(description = "infra identifier format") String infraIdentifierFormat;
  boolean isUpdatePipeline;
  Map<String, TemplateObject> templateMap;
  List<String> skipServices;
  List<String> skipInfras;
  List<String> skipPipelines;
}
