package io.harness.ng.core.service.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
@Hidden
@Schema(name = "ServiceImportSaveResponse",
        description = "Contains the Service details for the given Service ID and version")
public class ServiceImportSaveResponse {
    String serviceIdentifier;
}