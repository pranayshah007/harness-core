/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapointsdata.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.idp.v1.model.KubernetesConfig;

import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public interface KubernetesDataPointsService {
  Map<String, Object> getDataPointDataValues(String accountIdentifier, KubernetesConfig dataSourceDataPointInfo);
}
