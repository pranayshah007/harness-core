/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.k8s.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.AwsConfig;
import software.wings.beans.GcpConfig;
import software.wings.settings.SettingValue;

import java.util.HashSet;
import java.util.Set;
import lombok.NonNull;

@OwnedBy(CDP)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
public interface ManifestAwareTaskParams {
  K8sDelegateManifestConfig getK8sDelegateManifestConfig();

  @NonNull
  default Set<String> getDelegateSelectorsFromConfigs(K8sDelegateManifestConfig k8sDelegateManifestConfig) {
    Set<String> delegateSelectors = new HashSet<>();

    if (k8sDelegateManifestConfig != null) {
      if (k8sDelegateManifestConfig.getGitConfig() != null
          && isNotEmpty(k8sDelegateManifestConfig.getGitConfig().getDelegateSelectors())) {
        delegateSelectors.addAll(k8sDelegateManifestConfig.getGitConfig().getDelegateSelectors());
      }
      if (k8sDelegateManifestConfig.getHelmChartConfigParams() != null) {
        SettingValue connectorConfig = k8sDelegateManifestConfig.getHelmChartConfigParams().getConnectorConfig();
        if (connectorConfig != null) {
          if (connectorConfig instanceof AwsConfig) {
            AwsConfig awsConfig = (AwsConfig) connectorConfig;
            if (isNotEmpty(awsConfig.getTag())) {
              delegateSelectors.add(awsConfig.getTag());
            }
          } else if (connectorConfig instanceof GcpConfig) {
            GcpConfig gcpConfig = (GcpConfig) connectorConfig;
            if (isNotEmpty(gcpConfig.getDelegateSelector())) {
              delegateSelectors.addAll(new HashSet<>(gcpConfig.getDelegateSelectors()));
            }
          }
        }
      }
    }
    return delegateSelectors;
  }
}
