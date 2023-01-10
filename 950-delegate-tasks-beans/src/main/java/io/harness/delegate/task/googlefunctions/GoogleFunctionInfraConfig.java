package io.harness.delegate.task.googlefunctions;

import io.harness.annotations.dev.OwnedBy;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@OwnedBy(CDP)
public interface GoogleFunctionInfraConfig {
    List<EncryptedDataDetail> getEncryptionDataDetails();
}
