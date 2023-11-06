/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.servicediscovery.client.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.utils.IdentifierRefHelper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CHAOS)
public class DiscoveredServiceResponse {
  String id;
  String agentID;
  String type;
  String version;
  Spec spec;

  @Data
  @Builder
  public static class Spec {
    HarnessServiceIdentity harnessServiceIdentity;
    HarnessEnvironmentIdentity harnessEnvironmentIdentity;

    @Data
    @Builder
    public static class HarnessServiceIdentity {
      String identifier;
      String accountIdentifier;
      String organizationIdentifier;
      String projectIdentifier;
      @JsonIgnore
      public String getFullyQualifiedIdentifier() {
        IdentifierRef serviceIdentifierRef = IdentifierRefHelper.getIdentifierRefWithScope(
            accountIdentifier, organizationIdentifier, projectIdentifier, identifier);
        return serviceIdentifierRef.buildScopedIdentifier();
      }
    }

    @Data
    @Builder
    public static class HarnessEnvironmentIdentity {
      String identifier;
      String accountIdentifier;
      String organizationIdentifier;
      String projectIdentifier;
      @JsonIgnore
      public String getFullyQualifiedIdentifier() {
        IdentifierRef envIdentifierRef = IdentifierRefHelper.getIdentifierRefWithScope(
            accountIdentifier, organizationIdentifier, projectIdentifier, identifier);
        return envIdentifierRef.buildScopedIdentifier();
      }
    }
  }
}
