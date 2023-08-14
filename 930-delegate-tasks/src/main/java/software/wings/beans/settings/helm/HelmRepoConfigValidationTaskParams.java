/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.settings.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.delegatetasks.delegatecapability.CapabilityHelper;
import software.wings.settings.SettingValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
public class HelmRepoConfigValidationTaskParams implements TaskParameters, ExecutionCapabilityDemander {
  private String accountId;
  private String appId;
  private HelmRepoConfig helmRepoConfig;
  private List<EncryptedDataDetail> encryptedDataDetails;
  private String repoDisplayName;

  private SettingValue connectorConfig;
  private List<EncryptedDataDetail> connectorEncryptedDataDetails;
  private Set<String> delegateSelectors;
  private boolean useLatestChartMuseumVersion;
  private boolean useOCIHelmRepo;
  private boolean useNewHelmBinary;
  private boolean useCache;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    if (isNotEmpty(delegateSelectors)) {
      executionCapabilities.add(SelectorCapability.builder().selectors(delegateSelectors).build());
    }
    executionCapabilities.addAll(
        CapabilityHelper.generateDelegateCapabilities(helmRepoConfig, encryptedDataDetails, maskingEvaluator));
    return executionCapabilities;
  }
}
