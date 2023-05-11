/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitops.models;

import io.harness.utils.IdentifierRefHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
public class Cluster {
  String identifier;
  String agentIdentifier;
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;

  Map<String, String> tags;

  @JsonProperty("cluster") ClusterInternal clusterInternal;

  public Cluster(String identifier, String name) {
    this.identifier = identifier;
    this.clusterInternal = new ClusterInternal(name);
  }

  public String name() {
    return clusterInternal.getName();
  }

  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class ClusterInternal {
    @Getter String name;
  }

  public String fetchRef() {
    return IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }
}
