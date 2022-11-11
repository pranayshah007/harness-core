package io.harness.cdng.artifact.resources.AzureMachineImage.service;

import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.AzureMachineImage.dtos.AzureMachineImageResourceGroupDto;

import java.util.List;

public interface AzureMachineImageResourceService {
  List<AzureMachineImageResourceGroupDto> listResourceGroups(IdentifierRef AzureMachineImageConnectorRef,
      String cloudProviderId, String subscriptionId, String orgIdentifier, String projectIdentifier);
}
