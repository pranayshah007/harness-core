/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.model;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.service.impl.aws.model.AwsLambdaRequest.AwsLambdaRequestType.EXECUTE_LAMBDA_WF;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.artifact.ArtifactFile;
import software.wings.beans.artifact.ArtifactStreamAttributes;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
@OwnedBy(CDP)
public class AwsLambdaExecuteWfRequest extends AwsLambdaRequest {
  private String accountId;
  private String appId;
  private String activityId;
  private String commandName;
  private List<AwsLambdaFunctionParams> functionParams;
  private String roleArn;
  private List<String> evaluatedAliases;
  private Map<String, String> serviceVariables;
  private List<ArtifactFile> artifactFiles;
  private ArtifactStreamAttributes artifactStreamAttributes;
  private AwsLambdaVpcConfig lambdaVpcConfig;

  @Builder
  public AwsLambdaExecuteWfRequest(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      List<AwsLambdaFunctionParams> functionParams, String roleArn, List<String> evaluatedAliases,
      Map<String, String> serviceVariables, AwsLambdaVpcConfig lambdaVpcConfig, String accountId, String appId,
      String activityId, List<ArtifactFile> artifactFiles, ArtifactStreamAttributes artifactStreamAttributes,
      String commandName) {
    super(awsConfig, encryptionDetails, EXECUTE_LAMBDA_WF, region);
    this.accountId = accountId;
    this.appId = appId;
    this.activityId = activityId;
    this.commandName = commandName;
    this.functionParams = functionParams;
    this.roleArn = roleArn;
    this.evaluatedAliases = evaluatedAliases;
    this.serviceVariables = serviceVariables;
    this.artifactFiles = artifactFiles;
    this.artifactStreamAttributes = artifactStreamAttributes;
    this.lambdaVpcConfig = lambdaVpcConfig;
  }
}
