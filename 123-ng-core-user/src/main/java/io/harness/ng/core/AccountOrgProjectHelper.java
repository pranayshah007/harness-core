package io.harness.ng.core;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;

@OwnedBy(PL)
public interface AccountOrgProjectHelper {
  String getBaseUrl(String accountIdentifier);

  String getGatewayBaseUrl(String accountIdentifier);

  String getAccountName(String accountIdentifier);

  String getResourceScopeName(Scope scope);

  String getProjectName(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  String getOrgName(String accountIdentifier, String orgIdentifier);

  String getVanityUrl(String accountIdentifier);
}
