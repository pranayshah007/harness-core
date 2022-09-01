package io.harness.polling.mapper.artifact;

import io.harness.polling.bean.PollingInfo;
import io.harness.polling.bean.artifact.GARrtifactInfo;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.mapper.PollingInfoBuilder;

public class GarArtifactInfoBuilder implements PollingInfoBuilder {
  @Override
  public PollingInfo toPollingInfo(PollingPayloadData pollingPayloadData) {
    return GARrtifactInfo.builder()
        .connectorRef(pollingPayloadData.getConnectorRef())
        .version(pollingPayloadData)
        .build();
  }
}
