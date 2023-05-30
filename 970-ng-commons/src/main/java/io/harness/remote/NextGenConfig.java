/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.remote;

import io.harness.secret.ConfigSecret;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NextGenConfig {
  @ConfigSecret String managerServiceSecret;
  @ConfigSecret String userVerificationSecret;
  @ConfigSecret String ngManagerServiceSecret;
  @ConfigSecret String pipelineServiceSecret;
  @ConfigSecret String jwtAuthSecret;
  @ConfigSecret String jwtIdentityServiceSecret;
  @ConfigSecret String ciManagerSecret;
  @ConfigSecret String ceNextGenServiceSecret;
  @ConfigSecret String ffServiceSecret;
  @ConfigSecret String templateServiceSecret;
  @ConfigSecret String cvngServiceSecret;
  @ConfigSecret String chaosServiceSecret;
  @ConfigSecret String serviceDiscoveryServiceSecret;
}
