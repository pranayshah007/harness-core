/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import io.harness.connector.services.GoogleSecretManagerConnectorService;
import io.harness.helpers.ext.gcp.GcpRegion;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GoogleSecretManagerConnectorServiceImpl implements GoogleSecretManagerConnectorService {
  @Override
  public List<String> getGcpRegions() {
    return Arrays.stream(GcpRegion.values()).map(GcpRegion::getName).collect(Collectors.toList());
  }
}
