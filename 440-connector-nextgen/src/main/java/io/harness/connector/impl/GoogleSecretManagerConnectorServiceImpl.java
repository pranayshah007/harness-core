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
