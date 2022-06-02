/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.serverless.model.AwsLambdaFunctionDetails;

@OwnedBy(CDP)
public interface AwsLambdaHelperServiceDelegateNG {
  AwsLambdaFunctionDetails getAwsLambdaFunctionDetails(
      AwsInternalConfig awsInternalConfig, String function, String region);
}
