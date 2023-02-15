/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.CDP)
@Data
public class AwsCliCredentialConfig {
  private String provider;
  private String accessKey;
  private String secretKey;

  @Builder
  public AwsCliCredentialConfig(String provider, String accessKey, String secretKey) {
    this.provider = provider;
    this.accessKey = accessKey;
    this.secretKey = secretKey;
  }
}
