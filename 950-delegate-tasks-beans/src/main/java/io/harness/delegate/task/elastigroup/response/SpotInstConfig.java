package io.harness.delegate.task.elastigroup.response;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretReference;
import io.harness.security.encryption.EncryptedDataDetail;
import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.NotNull;
import java.util.List;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class SpotInstConfig {
    SpotConnectorDTO spotConnectorDTO;
    List<EncryptedDataDetail> encryptionDataDetails;
}
