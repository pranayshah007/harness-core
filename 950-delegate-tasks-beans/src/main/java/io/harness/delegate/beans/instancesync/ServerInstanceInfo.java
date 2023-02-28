/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.instancesync;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.info.AsgServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AwsLambdaServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AwsSshWinrmServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AzureSshWinrmServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AzureWebAppServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.CustomDeploymentServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.EcsServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.GoogleFunctionServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.K8sServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.NativeHelmServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.PdcServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.ServerlessAwsLambdaServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.SpotServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.TasServerInstanceInfo;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Extend this class and create deployment specific server instance structs that will
 * contain details with respect to the logical instance entity on the server
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = K8sServerInstanceInfo.class, name = "K8sServerInstanceInfo")
  , @JsonSubTypes.Type(value = NativeHelmServerInstanceInfo.class, name = "NativeHelmServerInstanceInfo"),
      @JsonSubTypes.Type(
          value = ServerlessAwsLambdaServerInstanceInfo.class, name = "ServerlessAwsLambdaServerInstanceInfo"),
      @JsonSubTypes.Type(value = AzureWebAppServerInstanceInfo.class, name = "AzureWebAppServerInstanceInfo"),
      @JsonSubTypes.Type(value = PdcServerInstanceInfo.class, name = "PdcServerInstanceInfo"),
      @JsonSubTypes.Type(value = EcsServerInstanceInfo.class, name = "EcsServerInstanceInfo"),
      @JsonSubTypes.Type(value = AzureSshWinrmServerInstanceInfo.class, name = "AzureSshWinrmServerInstanceInfo"),
      @JsonSubTypes.Type(value = AwsSshWinrmServerInstanceInfo.class, name = "AwsSshWinrmServerInstanceInfo"),
      @JsonSubTypes.Type(value = CustomDeploymentServerInstanceInfo.class, name = "CustomDeploymentServerInstanceInfo"),
      @JsonSubTypes.Type(value = SpotServerInstanceInfo.class, name = "SpotServerInstanceInfo"),
      @JsonSubTypes.Type(value = TasServerInstanceInfo.class, name = "TasServerInstanceInfo"),
      @JsonSubTypes.Type(value = AsgServerInstanceInfo.class, name = "AsgServerInstanceInfo"),
      @JsonSubTypes.Type(
          value = GoogleFunctionServerInstanceInfo.class, name = "GoogleCloudFunctionsServerInstanceInfo"),
      @JsonSubTypes.Type(value = AwsLambdaServerInstanceInfo.class, name = "AwsLambdaServerInstanceInfo")
})
@OwnedBy(HarnessTeam.DX)
public abstract class ServerInstanceInfo {}
