package io.harness.delegate.task.ecs;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.expression.Expression;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class EcsInfraConfig {
  AwsConnectorDTO awsConnectorDTO;
  List<EncryptedDataDetail> encryptionDataDetails;
  EcsInfraType ecsInfraType;
  @NonFinal @Expression(ALLOW_SECRETS) String region;
  @NonFinal @Expression(ALLOW_SECRETS) String cluster;
  String infraStructureKey;
}
