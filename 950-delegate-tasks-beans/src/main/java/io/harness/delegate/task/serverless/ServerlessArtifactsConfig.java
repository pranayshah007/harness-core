package io.harness.delegate.task.serverless;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionReflectionUtils.NestedAnnotationResolver;

import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class ServerlessArtifactsConfig implements NestedAnnotationResolver {
  @NonFinal @Expression(ALLOW_SECRETS) ServerlessArtifactConfig primary;
  @NonFinal @Expression(ALLOW_SECRETS) Map<String, ServerlessArtifactConfig> sidecars;
}