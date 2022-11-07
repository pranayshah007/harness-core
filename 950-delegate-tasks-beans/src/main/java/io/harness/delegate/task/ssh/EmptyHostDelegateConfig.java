package io.harness.delegate.task.ssh;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class EmptyHostDelegateConfig implements SshInfraDelegateConfig {
  Set<String> hosts;
  List<EncryptedDataDetail> encryptionDataDetails;
  SSHKeySpecDTO sshKeySpecDto;
}
