/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.scim;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true, includeFieldNames = true)
public final class PatchRequest extends ScimBaseResource {
  @JsonProperty(value = "Operations", required = true) private List<PatchOperation> operations;
  @JsonProperty(value = "schemas", required = true) private Set<String> schemas;

  @JsonCreator
  public PatchRequest(@JsonProperty(value = "Operations", required = true) final List<PatchOperation> operations) {
    this.operations = Collections.unmodifiableList(operations);
  }
}
