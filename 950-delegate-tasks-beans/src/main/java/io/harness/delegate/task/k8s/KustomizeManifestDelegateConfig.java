/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.expression.Expression;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class KustomizeManifestDelegateConfig implements ManifestDelegateConfig {
  StoreDelegateConfig storeDelegateConfig;
  String pluginPath;
  String kustomizeDirPath;
  String kustomizeYamlFolderPath;
  @Expression(ALLOW_SECRETS) Map<String, String> commandFlags;

  @Override
  public ManifestType getManifestType() {
    return ManifestType.KUSTOMIZE;
  }
}
