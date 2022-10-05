package io.harness.cdng.service.steps;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;

import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
@TypeAlias("serviceSweepingOutput")
@JsonTypeName("serviceSweepingOutput")
@RecasterAlias("io.harness.cdng.service.steps.ServiceSweepingOutput")
public class ServiceSweepingOutput implements ExecutionSweepingOutput {
  // Maps to NGServiceConfig
  @NotNull String finalServiceYaml;
}
