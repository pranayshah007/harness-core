/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.K8sGlobalConfigService;
import io.harness.k8s.model.HelmVersion;

@OwnedBy(HarnessTeam.CDP)
public class K8sGlobalConfigServiceUnsupported implements K8sGlobalConfigService {
  private static final String UNSUPPORTED_OPERATION_MSG = "K8sGlobalConfigService not available in manager";

  @Override
  public String getKubectlPath(boolean useNewKubectlVersion) {
    throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_MSG);
  }

  @Override
  public String getGoTemplateClientPath() {
    throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_MSG);
  }

  @Override
  public String getHelmPath(HelmVersion helmVersion) {
    throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_MSG);
  }

  @Override
  public String getChartMuseumPath(boolean useLatestVersion) {
    throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_MSG);
  }

  @Override
  public String getOcPath() {
    throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_MSG);
  }

  @Override
  public String getKustomizePath(boolean useLatestVersion) {
    throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_MSG);
  }
}
