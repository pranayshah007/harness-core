package io.harness.delegate.task.pcf.response;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.pcfconnector.PcfConnectorDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class PcfInfraConfig {
  String organization;
  String space;
  PcfConnectorDTO pcfConnectorDTO;
  List<EncryptedDataDetail> encryptionDataDetails;
}
