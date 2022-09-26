package io.harness.artifacts.azuremachineimage.service;

import io.harness.azure.model.AzureConfig;

import java.util.List;
import java.util.Map;

public interface AzureMachineImageRegistryService {
  List<Map<String, String>> getResourceGroups(AzureConfig azureConfig, String subscriptionId);
}
