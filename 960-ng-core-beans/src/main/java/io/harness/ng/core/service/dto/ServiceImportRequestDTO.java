package io.harness.ng.core.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.harness.annotations.dev.OwnedBy;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import static io.harness.annotations.dev.HarnessTeam.CDC;

@OwnedBy(CDC)
@Value
@Builder
@Hidden
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("ServiceImportRequest")
@Schema(name = "ServiceImportRequest",
        description = "Contains basic information required to be linked with imported Service YAML")
public class ServiceImportRequestDTO {
    @Schema(description = "Expected Name of the Service to be imported") String serviceName;
    @Schema(description = "Expected Description of the Service to be imported") String serviceDescription;
}