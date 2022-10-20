package io.harness.ngmigration.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.CDC)
@Data
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("TEMPLATE")
public class TemplateFilter extends Filter {
    @Parameter(description = "All services from Application to import") private String appId;
}
