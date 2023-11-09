/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.rancher;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.ConnectorValidationResult;

import java.util.List;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
public interface RancherConnectionHelperService {
  ConnectorValidationResult testRancherConnection(String rancherUrl, String bearerToken);
  List<String> listClusters(String rancherUrl, String bearerToken, Map<String, String> pageRequestParams);
  String generateKubeconfig(String rancherUrl, String bearerToken, String clusterName);
  void deleteKubeconfigToken(String rancherUrl, String bearerToken, String tokenId);
}
