/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.beans.ScopeLevel;

import javax.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.glassfish.jersey.process.internal.RequestScoped;

//@Getter
//@Setter
@RequestScoped
//@ManagedBean
public class ScopeInfo {
  @NotEmpty String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String uniqueId;
  ScopeLevel scopeType;
  public ScopeInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String uniqueId, ScopeLevel scopeType) {
    this.accountIdentifier = accountIdentifier;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.uniqueId = uniqueId;
    this.scopeType = scopeType;
  }
  public ScopeInfo() {}
}
