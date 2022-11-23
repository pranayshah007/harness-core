package io.harness.cdng.tas.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;

@OwnedBy(HarnessTeam.CDP)
public interface TasResourceService {
  List<String> listOrganizationsForTas(
      String connectorRef, String accountIdentifier, String orgIdentifier, String projectIdentifier);

  List<String> listSpacesForTas(String connectorRef, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String organization);
}
