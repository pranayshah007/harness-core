/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.harness.beans;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.EmbeddedUser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RecasterAlias("io.harness.steps.approval.step.harness.beans.EmbeddedUserDTO")
public class EmbeddedUserDTO {
  private String name;
  private String email;

  public static EmbeddedUserDTO fromEmbeddedUser(EmbeddedUser user) {
    if (user == null) {
      return null;
    }
    return EmbeddedUserDTO.builder().name(user.getName()).email(user.getEmail()).build();
  }

  public static EmbeddedUser toEmbeddedUser(EmbeddedUserDTO user) {
    if (user == null) {
      return null;
    }
    return EmbeddedUser.builder().name(user.getName()).email(user.getEmail()).build();
  }
}
