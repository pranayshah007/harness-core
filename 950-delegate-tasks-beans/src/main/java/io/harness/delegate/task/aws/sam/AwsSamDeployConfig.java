/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.sam;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.serverless.ServerlessDeployConfig;
import lombok.Builder;
import lombok.Data;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@OwnedBy(CDP)
@Data
@Builder
public class AwsSamDeployConfig {
  String stackName;
  String deployCommandOptions;
  Integer samCliPollDelay;
}
