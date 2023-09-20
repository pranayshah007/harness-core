/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage.pojos;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import java.util.StringJoiner;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@OwnedBy(CDP)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "ActiveServiceBaseField")
public class ActiveServiceBase {
  String identifier;
  String orgIdentifier;
  String projectIdentifier;
  long instanceCount;
  long lastDeployed;
  String instanceType;

  public String toSQlRow() {
    // the order of properties is important because of constant table in query,
    // see io.harness.cdng.usage.CDLicenseUsageDAL.FETCH_ACTIVE_SERVICES_NAME_ORG_AND_PROJECT_NAME_QUERY
    return new StringJoiner(",", "(", ")")
        .add("'" + this.orgIdentifier + "'")
        .add("'" + this.projectIdentifier + "'")
        .add("'" + this.identifier + "'")
        .add(Long.toString(this.lastDeployed))
        .add(Long.toString(this.instanceCount))
        .toString();
  }
}
