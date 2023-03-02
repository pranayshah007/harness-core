/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.lambda.beans;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.aws.lambda.AwsLambdaArtifactConfig;
import io.harness.expression.Expression;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("awsLambdaPrepareRollbackOutcome")
@JsonTypeName("awsLambdaPrepareRollbackOutcome")
@RecasterAlias("io.harness.cdng.aws.lambda.beans.AwsLambdaPrepareRollbackOutcome")
public class AwsLambdaPrepareRollbackOutcome implements Outcome, ExecutionSweepingOutput {
  @NonFinal @Expression(ALLOW_SECRETS) String awsLambdaDeployManifestContent;
  @NonFinal @Expression(ALLOW_SECRETS) String functionName;
  @NonFinal @Expression(ALLOW_SECRETS) String qualifier;
  @NonFinal @Expression(ALLOW_SECRETS) AwsLambdaArtifactConfig awsLambdaArtifactConfig;
  boolean firstDeployment;
  String functionCode;
  String functionConfiguration;
}
