package io.harness.delegate.task.azure.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.Expression;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
public class JenkinsAzureArtifactRequestDetails implements AzureArtifactRequestDetails {
  @Expression(ALLOW_SECRETS) String build;
  @Expression(ALLOW_SECRETS) String jobName;
  @Expression(ALLOW_SECRETS) String artifactPath;
  private String identifier;

  @Override
  public String getArtifactName() {
    return artifactPath;
  }
}
