/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitops.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Objects;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Application {
  String identifier;
  String name;
  String agentIdentifier;
  String url;
  String healthStatus;
  String syncStatus;
  String syncMessage;
  String revision;
  boolean isAutoSyncEnabled;

  // TODO add account, org, project ids here
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Application)) {
      return false;
    }
    Application application = (Application) o;
    return Objects.equals(getName(), application.getName())
        && Objects.equals(getAgentIdentifier(), application.getAgentIdentifier());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getAgentIdentifier());
  }

  @Override
  public String toString() {
    return "Application{"
        + "name='" + name + '\'' + ", agentIdentifier='" + agentIdentifier + '\'' + '}';
  }

  public String getSyncError() {
    return "Application{"
        + "name: '" + name + '\'' + ", agentIdentifier: '" + agentIdentifier + '\'' + ", errorMessage: '" + syncMessage
        + '\'' + '}';
  }
}
