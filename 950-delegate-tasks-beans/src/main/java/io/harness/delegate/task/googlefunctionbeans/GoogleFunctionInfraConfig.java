package io.harness.delegate.task.googlefunctionbeans;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;

@OwnedBy(CDP)
public interface GoogleFunctionInfraConfig {
  List<EncryptedDataDetail> getEncryptionDataDetails();
}
