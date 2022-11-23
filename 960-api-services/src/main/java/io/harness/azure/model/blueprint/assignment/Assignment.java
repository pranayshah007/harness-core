/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.model.blueprint.assignment;

import io.harness.azure.model.blueprint.ParameterValue;

import com.azure.resourcemanager.network.models.ManagedServiceIdentity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Assignment {
  private String id;
  private String location;
  private String name;
  private String type;
  private ManagedServiceIdentity identity;
  private Properties properties;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Properties {
    private String blueprintId;
    private String description;
    private String displayName;
    private String scope;
    private AssignmentLockSettings locks;
    private Map<String, ParameterValue> parameters;
    private AssignmentProvisioningState provisioningState;
    private Map<String, ResourceGroupValue> resourceGroups;
    private AssignmentStatus status;
  }
}
