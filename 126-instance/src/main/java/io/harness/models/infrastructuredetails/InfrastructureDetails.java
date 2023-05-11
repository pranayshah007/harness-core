/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.models.infrastructuredetails;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@OwnedBy(HarnessTeam.DX)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
  @JsonSubTypes.Type(value = K8sInfrastructureDetails.class, name = "K8S")
  , @JsonSubTypes.Type(value = ServerlessAwsLambdaInfrastructureDetails.class, name = "ServerlessAwsLambda"),
      @JsonSubTypes.Type(value = AzureWebAppInfrastructureDetails.class, name = "AzureWebApp"),
      @JsonSubTypes.Type(value = SshWinrmInfrastructureDetails.class, name = "SshWinrm"),
      @JsonSubTypes.Type(value = EcsInfrastructureDetails.class, name = "ECS"),
      @JsonSubTypes.Type(value = TasInfrastructureDetails.class, name = "TAS"),
      @JsonSubTypes.Type(value = SpotInfrastructureDetails.class, name = "Spot"),
      @JsonSubTypes.Type(value = AsgInfrastructureDetails.class, name = "Asg"),
      @JsonSubTypes.Type(value = GoogleFunctionInfrastructureDetails.class, name = "GoogleCloudFunction"),
      @JsonSubTypes.Type(value = AwsLambdaInfrastructureDetails.class, name = "AwsLambda"),
      @JsonSubTypes.Type(value = AwsSamInfrastructureDetails.class, name = "AWS_SAM")
})
public abstract class InfrastructureDetails {}
