package io.harness.cdng.infra.yaml;

import io.harness.pms.yaml.ParameterField;

public interface AzureInfrastructure {
  ParameterField<String> getSubscriptionId();
  ParameterField<String> getResourceGroup();
}
