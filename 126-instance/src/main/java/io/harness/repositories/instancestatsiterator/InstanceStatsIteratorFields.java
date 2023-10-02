/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.instancestatsiterator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDP)
public enum InstanceStatsIteratorFields {
  ACCOUNTID("ACCOUNTID"),
  SERVICEID("SERVICEID"),
  REPORTEDAT("REPORTEDAT"),
  ;

  private String fieldName;

  InstanceStatsIteratorFields(String fieldName) {
    this.fieldName = fieldName;
  }

  public String fieldName() {
    return this.fieldName;
  }
}
