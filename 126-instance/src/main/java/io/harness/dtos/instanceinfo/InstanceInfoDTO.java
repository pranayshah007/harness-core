/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dtos.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@OwnedBy(HarnessTeam.DX)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
  @JsonSubTypes.Type(value = K8sInstanceInfoDTO.class, name = "K8s")
  , @JsonSubTypes.Type(value = NativeHelmInstanceInfoDTO.class, name = "NativeHelm"),
      @JsonSubTypes.Type(value = ReferenceInstanceInfoDTO.class, name = "Reference"),
      @JsonSubTypes.Type(value = ServerlessAwsLambdaInstanceInfoDTO.class, name = "ServerlessAwsLambda"),
      @JsonSubTypes.Type(value = GitOpsInstanceInfoDTO.class, name = "GitOps"),
      @JsonSubTypes.Type(value = AzureWebAppInstanceInfoDTO.class, name = "AzureWebApp"),
      @JsonSubTypes.Type(value = PdcInstanceInfoDTO.class, name = "Pdc"),
      @JsonSubTypes.Type(value = AzureSshWinrmInstanceInfoDTO.class, name = "AzureSshWinrm"),
      @JsonSubTypes.Type(value = AwsSshWinrmInstanceInfoDTO.class, name = "AwsSshWinrm"),
      @JsonSubTypes.Type(value = CustomDeploymentInstanceInfoDTO.class, name = "CustomDeployment"),
      @JsonSubTypes.Type(value = EcsInstanceInfoDTO.class, name = "ECS"),
      @JsonSubTypes.Type(value = TasInstanceInfoDTO.class, name = "TAS"),
      @JsonSubTypes.Type(value = SpotInstanceInfoDTO.class, name = "Elastigroup"),
      @JsonSubTypes.Type(value = AsgInstanceInfoDTO.class, name = "Asg"),
      @JsonSubTypes.Type(value = GoogleFunctionInstanceInfoDTO.class, name = "GoogleCloudFunction"),
      @JsonSubTypes.Type(value = AwsLambdaInstanceInfoDTO.class, name = "AwsLambda")
})
public abstract class InstanceInfoDTO {
  // Create combination of fields that identifies any related instance uniquely
  public abstract String prepareInstanceKey();

  // Create combination of fields that can be used to identify corresponding deployment info details
  // The key should be same as instance handler key of the corresponding deployment info
  public abstract String prepareInstanceSyncHandlerKey();

  // Get name of instance on the server as per the deployment type
  public abstract String getPodName();

  public abstract String getType();
}
