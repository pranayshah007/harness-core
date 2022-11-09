package io.harness.delegate.task.elastigroup.response;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class SpotInstConfig {
  SpotConnectorDTO spotConnectorDTO;
  List<EncryptedDataDetail> encryptionDataDetails;
}
