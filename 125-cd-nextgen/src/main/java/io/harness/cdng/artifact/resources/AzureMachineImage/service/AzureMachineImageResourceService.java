package io.harness.cdng.artifact.resources.AzureMachineImage.service;

import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.AzureMachineImage.dtos.AzureBuildsDTO;

public interface AzureMachineImageResourceService {
  AzureBuildsDTO getBuilds(IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier,
      String subscriptionId, String resourceGroup, String galleryName, String imageDefinition);
}
