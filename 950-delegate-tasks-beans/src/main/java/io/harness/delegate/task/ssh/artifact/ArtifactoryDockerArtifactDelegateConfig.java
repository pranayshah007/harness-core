package io.harness.delegate.task.ssh.artifact;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class ArtifactoryDockerArtifactDelegateConfig
    implements SkipCopyArtifactDelegateConfig, NestedAnnotationResolver {
  @NonFinal @Expression(ALLOW_SECRETS) String repositoryName;
  @NonFinal @Expression(ALLOW_SECRETS) String artifactPath;
  @NonFinal @Expression(ALLOW_SECRETS) String artifactDirectory;
  String repositoryFormat;
  String identifier;
  ConnectorInfoDTO connectorDTO;
  List<EncryptedDataDetail> encryptedDataDetails;
  String tag;
  String image;

  @Override
  public SshWinRmArtifactType getArtifactType() {
    return SshWinRmArtifactType.ARTIFACTORY;
  }
}
