/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.rancher;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorValidationResult;

import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CDP)
public interface RancherConnectionHelperService {
  ConnectorValidationResult testRancherConnection(String rancherUrl, String bearerToken);
  List<String> listClusters(String rancherUrl, String bearerToken, Map<String, String> pageRequestParams);
}
