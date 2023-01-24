package io.harness.delegate.task.googlefunctionbeans;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.expression.Expression;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class GcpGoogleFunctionInfraConfig implements GoogleFunctionInfraConfig {
  GcpConnectorDTO gcpConnectorDTO;
  List<EncryptedDataDetail> encryptionDataDetails;
  @NonFinal @Expression(ALLOW_SECRETS) String region;
  @NonFinal @Expression(ALLOW_SECRETS) String project;
  String infraStructureKey;
}
