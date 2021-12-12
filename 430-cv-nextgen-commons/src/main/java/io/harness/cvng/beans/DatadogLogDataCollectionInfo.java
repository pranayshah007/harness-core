package io.harness.cvng.beans;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.beans.datadog.DatadogLogDefinition;
import io.harness.delegate.beans.connector.datadog.DatadogConnectorDTO;
import io.harness.delegate.beans.cvng.datadog.DatadogUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Builder
@EqualsAndHashCode(callSuper = true)
public class DatadogLogDataCollectionInfo extends LogDataCollectionInfo<DatadogConnectorDTO> {
  public static final long LOG_MAX_LIMIT = 500;
  DatadogLogDefinition logDefinition;

  @Override
  public Map<String, Object> getDslEnvVariables(DatadogConnectorDTO connectorConfigDTO) {
    Map<String, Object> dslEnvVariables = new HashMap<>();
    dslEnvVariables.put("query", logDefinition.getQuery());
    String indexesParam = null;
    if (isNotEmpty(logDefinition.getIndexes())) {
      indexesParam = String.join(",", logDefinition.getIndexes());
    }
    dslEnvVariables.put("indexes", indexesParam);
    dslEnvVariables.put("limit", LOG_MAX_LIMIT);
    dslEnvVariables.put("serviceInstanceIdentifier", logDefinition.getServiceInstanceIdentifier());
    return dslEnvVariables;
  }

  @Override
  public String getBaseUrl(DatadogConnectorDTO connectorConfigDTO) {
    return connectorConfigDTO.getUrl();
  }

  @Override
  public Map<String, String> collectionHeaders(DatadogConnectorDTO connectorConfigDTO) {
    return DatadogUtils.collectionHeaders(connectorConfigDTO);
  }

  @Override
  public Map<String, String> collectionParams(DatadogConnectorDTO connectorConfigDTO) {
    return Collections.emptyMap();
  }
}
