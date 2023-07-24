/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.deploymentmetadata;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.k8s.ServiceSpecType;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@JsonTypeInfo(use = NAME, property = "deploymentType", include = EXTERNAL_PROPERTY, visible = true)
@JsonSubTypes({
  @Type(value = KubernetesDeploymentMetaData.class, name = ServiceSpecType.KUBERNETES)
  , @Type(value = SshDeploymentMetaData.class, name = ServiceSpecType.SSH),
      @Type(value = EcsDeploymentMetaData.class, name = ServiceSpecType.ECS),
      @Type(value = NativeHelmDeploymentMetaData.class, name = ServiceSpecType.NATIVE_HELM),
      @Type(value = TasDeploymentMetaData.class, name = ServiceSpecType.TAS),
      @Type(value = ServerlessAwsLambdaDeploymentMetaData.class, name = ServiceSpecType.SERVERLESS_AWS_LAMBDA),
      @Type(value = WinrmDeploymentMetaData.class, name = ServiceSpecType.WINRM),
      @Type(value = AzureWebAppDeploymentMetaData.class, name = ServiceSpecType.AZURE_WEBAPP),
      @Type(value = CustomDeploymentMetaData.class, name = ServiceSpecType.CUSTOM_DEPLOYMENT),
      @Type(value = ElastiGroupDeploymentMetaData.class, name = ServiceSpecType.ELASTIGROUP),
      @Type(value = AsgDeploymentMetaData.class, name = ServiceSpecType.ASG),
      @Type(value = GoogleCloudFunctionDeploymentMetaData.class, name = ServiceSpecType.GOOGLE_CLOUD_FUNCTIONS),
      @Type(value = AwsLambdaDeploymentMetaData.class, name = ServiceSpecType.AWS_LAMBDA),
      @Type(value = AwsSamDeploymentMetaData.class, name = ServiceSpecType.AWS_SAM)
})
public interface DeploymentMetaData {}
