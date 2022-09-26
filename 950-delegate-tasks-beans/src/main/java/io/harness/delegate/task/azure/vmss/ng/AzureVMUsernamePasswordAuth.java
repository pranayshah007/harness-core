package io.harness.delegate.task.azure.vmss.ng;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretReference;
import io.harness.expression.Expression;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
@OwnedBy(CDP)
public class AzureVMUsernamePasswordAuth implements AzureVMAuthentication {
  @Expression(ALLOW_SECRETS) private String username;
  @SecretReference private SecretRefData usernameRef;
  @NotNull @SecretReference private SecretRefData passwordRef;
  private List<EncryptedDataDetail> encryptionDetails;

  @Override
  public AzureVMAuthType getType() {
    return AzureVMAuthType.USERNAME_PASSWORD;
  }
}
