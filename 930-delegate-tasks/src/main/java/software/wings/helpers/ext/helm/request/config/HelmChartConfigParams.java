/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.helm.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.expression.ExpressionEvaluator;
import io.harness.k8s.model.HelmVersion;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.settings.helm.HelmRepoConfig;
import software.wings.delegatetasks.delegatecapability.CapabilityHelper;
import software.wings.settings.SettingValue;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
@OwnedBy(CDP)
public class HelmChartConfigParams implements ExecutionCapabilityDemander {
  private HelmRepoConfig helmRepoConfig;
  private List<EncryptedDataDetail> encryptedDataDetails;
  private String repoDisplayName;
  private String repoName;

  private SettingValue connectorConfig;
  private List<EncryptedDataDetail> connectorEncryptedDataDetails;

  private String chartName;
  private String chartVersion;
  private String chartUrl;
  private String basePath;

  private HelmVersion helmVersion;
  private boolean useLatestChartMuseumVersion;
  private boolean useRepoFlags;
  private boolean checkIncorrectChartVersion;
  private boolean bypassHelmFetch;
  private boolean deleteRepoCacheDir;
  private boolean useCache;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return CapabilityHelper.generateDelegateCapabilities(helmRepoConfig, encryptedDataDetails, maskingEvaluator);
  }
}