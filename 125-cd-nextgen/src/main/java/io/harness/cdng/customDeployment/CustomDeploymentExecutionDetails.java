package io.harness.cdng.customDeployment;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.configfile.ConfigFileOutcome;
import io.harness.cdng.execution.ExecutionDetails;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@OwnedBy(CDP)
@Data
@Builder
@FieldNameConstants(innerTypeName = "CustomDeploymentExecutionDetailsKeys")
@JsonTypeName("CustomDeploymentExecutionDetails")
public class CustomDeploymentExecutionDetails implements ExecutionDetails {
  private List<ArtifactOutcome> artifactsOutcome;
  private Map<String, ConfigFileOutcome> configFilesOutcome;
}
