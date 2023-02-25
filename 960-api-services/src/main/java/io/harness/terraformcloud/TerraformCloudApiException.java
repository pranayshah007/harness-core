/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.terraformcloud;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.CDP)
@Data
@EqualsAndHashCode(callSuper = true)
public class TerraformCloudApiException extends RuntimeException {
  private int statusCode;
  public TerraformCloudApiException(String message, int statusCode) {
    super(message);
    this.statusCode = statusCode;
  }

  public TerraformCloudApiException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
