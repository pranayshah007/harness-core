package io.harness.beans.environment;

import io.harness.delegate.beans.ci.pod.EnvVariableEnum;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public final class ConnectorConversionInfo {
  private String connectorRef;
  private Map<EnvVariableEnum, String> envToSecretsMap;
}
