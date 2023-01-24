package io.harness.delegate.task.googlefunctionbeans;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.Expression;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class GoogleCloudStorageArtifactConfig implements GoogleFunctionArtifactConfig {
  @NonFinal @Expression(ALLOW_SECRETS) String bucket;
  @NonFinal @Expression(ALLOW_SECRETS) String filePath;
}
