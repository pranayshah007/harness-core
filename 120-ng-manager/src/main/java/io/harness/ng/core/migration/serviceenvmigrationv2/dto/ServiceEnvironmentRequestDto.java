package io.harness.ng.core.migration.serviceenvmigrationv2.dto;

import io.harness.annotations.dev.OwnedBy;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@OwnedBy(CDP)
@Value
@Builder
public class ServiceEnvironmentRequestDto {
    @NotNull String orgIdentifier;
    @NotNull String projectIdentifier;
    @NotNull String pipelineIdentifier;
    @NotNull @Schema(description = "infra identifier format") String infraIdentifierFormat;
    boolean isUpdatePipeline;
    Map<String, TemplateObject> templateMap;
    List<String> skipServices;
    List<String> skipInfras;
}
