package io.harness.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.pms.sdk.core.governance.ExpandedValue;
import io.harness.serializer.JsonUtils;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Builder
@Slf4j
public class ConnectorRefExpandedValue implements ExpandedValue {
  ConnectorDTO connectorDTO;

  @Override
  public String getKey() {
    return ExpansionKeysConstants.CONNECTOR_EXPANSION_KEY;
  }

  @Override
  public String toJson() {
    return JsonUtils.asJson(connectorDTO.getConnectorInfo());
  }
}
