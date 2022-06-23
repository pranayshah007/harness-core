package io.harness.delegate.task.serverless;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.Expression;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.Map;

import static io.harness.expression.Expression.ALLOW_SECRETS;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class ServerlessArtifactsConfig {
    @NonFinal @Expression(ALLOW_SECRETS) ServerlessArtifactConfig primary;
    @NonFinal @Expression(ALLOW_SECRETS) Map<String, ServerlessArtifactConfig> sidecars;
}