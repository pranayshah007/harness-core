/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.validation.capabilities;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.CapabilityType;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.SSHExecutionCredential;
import software.wings.beans.SSHVaultConfig;
import software.wings.beans.dto.SettingAttribute;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class SSHHostValidationCapability implements ExecutionCapability {
  @NotNull BasicValidationInfo validationInfo;
  @NotNull private SettingAttribute hostConnectionAttributes;
  @NotNull private SettingAttribute bastionConnectionAttributes;
  private List<EncryptedDataDetail> hostConnectionCredentials;
  private List<EncryptedDataDetail> bastionConnectionCredentials;
  private SSHExecutionCredential sshExecutionCredential;
  private SSHVaultConfig sshVaultConfig;
  private Map<String, String> envVariables = new HashMap<>();
  @Builder.Default private final CapabilityType capabilityType = CapabilityType.SSH_HOST_CONNECTION;

  @Override
  public EvaluationMode evaluationMode() {
    return EvaluationMode.AGENT;
  }

  @Override
  public String fetchCapabilityBasis() {
    if (validationInfo.isExecuteOnDelegate()) {
      return "localhost";
    }
    return validationInfo.getPublicDns();
  }

  @Override
  public Duration getMaxValidityPeriod() {
    return Duration.ofHours(6);
  }

  @Override
  public Duration getPeriodUntilNextValidation() {
    return Duration.ofHours(4);
  }

  @Override
  public String getCapabilityToString() {
    return isNotEmpty(validationInfo.getPublicDns())
        ? String.format("Capability reach on host : %s ", validationInfo.getPublicDns())
        : null;
  }

  /**
   * Error message to show mostly in delegate selection log if none of the delegates passed the validation check
   */
  @Override
  public String getCapabilityValidationError() {
    // Delegate(s) unable to connect to {criteria}, make sure to provide the connectivity with the
    // following delegates : [h1,h2]
    return isNotEmpty(fetchCapabilityBasis())
        ? String.format(
            "Delegate(s) unable to connect to %s, make sure the following delegates has connectivity with valid credentials",
            fetchCapabilityBasis())
        : ExecutionCapability.super.getCapabilityValidationError();
  }
}
