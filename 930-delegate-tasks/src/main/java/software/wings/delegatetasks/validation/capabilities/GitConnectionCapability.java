/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.validation.capabilities;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.CapabilityType;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.GitConfig;
import software.wings.beans.dto.SettingAttribute;

import java.time.Duration;
import java.util.List;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;

/**
 * Adding Setting attribute here is wrong but people used a variety of git URL's and we need to have an appropriate
 * parser to extract the hosts and check connectivity.
 *
 * Following the old model here.
 */
@Value
@Builder
@TargetModule(HarnessModule._955_DELEGATE_BEANS)
public class GitConnectionCapability implements ExecutionCapability {
  @Override
  public EvaluationMode evaluationMode() {
    return EvaluationMode.AGENT;
  }
  GitConfig gitConfig;
  @ToString.Exclude SettingAttribute settingAttribute;
  List<EncryptedDataDetail> encryptedDataDetails;
  CapabilityType capabilityType = CapabilityType.GIT_CONNECTION;

  @Override
  public String fetchCapabilityBasis() {
    return "GIT:" + gitConfig.getRepoUrl();
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
    return isNotEmpty(gitConfig.getRepoUrl()) ? String.format("Capability reach url : %s ", gitConfig.getRepoUrl())
                                              : null;
  }

  /**
   * Error message to show mostly in delegate selection log if none of the delegates passed the validation check
   */
  @Override
  public String getCapabilityValidationError() {
    // Delegate(s) unable to connect to git gitConfig.getRepoUrl(), make sure to provide the connectivity with the
    // following delegates : [h1,h2]
    return isNotEmpty(fetchCapabilityBasis())
        ? String.format(
            "Delegate(s) unable to connect to %s, make sure to provide the connectivity with the following delegates",
            fetchCapabilityBasis())
        : ExecutionCapability.super.getCapabilityValidationError();
  }
}
