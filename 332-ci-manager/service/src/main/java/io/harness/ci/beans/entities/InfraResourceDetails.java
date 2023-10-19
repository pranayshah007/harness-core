/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.beans.entities;

import io.harness.annotation.RecasterAlias;
import io.harness.pms.contracts.ambiance.Ambiance;

import lombok.Builder;
import lombok.Data;
import lombok.Value;

@Data
@Value
@Builder
@RecasterAlias("io.harness.app.beans.entities.InfraResourceDetails")
public class InfraResourceDetails implements ResourceDetails {
  Ambiance ambiance;
  Type type = Type.INFRA;

  @Override
  public Type getType() {
    return type;
  }
}
