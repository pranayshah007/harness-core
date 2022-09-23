package io.harness.delegate.task.ssh.artifact;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionReflectionUtils;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class AwsS3ArtifactDelegateConfig
    implements SshWinRmArtifactDelegateConfig, ExpressionReflectionUtils.NestedAnnotationResolver {
  @NonFinal @Expression(ALLOW_SECRETS) String artifactPath;
  @NonFinal @Expression(ALLOW_SECRETS) String bucketName;
  String identifier;
  AwsConnectorDTO awsConnector;
  String region;
  List<EncryptedDataDetail> encryptionDetails;
  boolean certValidationRequired;
  String accountId;

  @Override
  public SshWinRmArtifactType getArtifactType() {
    return SshWinRmArtifactType.AWS_S3;
  }
}
