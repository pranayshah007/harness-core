/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.model;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.service.impl.aws.model.AwsLambdaRequest.AwsLambdaRequestType.EXECUTE_LAMBDA_FUNCTION;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.api.AwsLambdaExecutionData;
import software.wings.beans.AwsConfig;
import software.wings.beans.LambdaTestEvent;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
@OwnedBy(CDP)
public class AwsLambdaExecuteFunctionRequest extends AwsLambdaRequest {
  private String functionName;
  private String qualifier;
  private String payload;
  private AwsLambdaExecutionData awsLambdaExecutionData;
  private LambdaTestEvent lambdaTestEvent;

  @Builder
  public AwsLambdaExecuteFunctionRequest(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String functionName, String qualifier, String logType, String payload,
      AwsLambdaExecutionData awsLambdaExecutionData, LambdaTestEvent lambdaTestEvent) {
    super(awsConfig, encryptionDetails, EXECUTE_LAMBDA_FUNCTION, region);
    this.functionName = functionName;
    this.qualifier = qualifier;
    this.payload = payload;
    this.awsLambdaExecutionData = awsLambdaExecutionData;
    this.lambdaTestEvent = lambdaTestEvent;
  }
}
