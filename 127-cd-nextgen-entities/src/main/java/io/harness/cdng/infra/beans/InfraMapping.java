/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.beans;

import io.harness.annotations.StoreIn;
import io.harness.cdng.infra.yaml.AsgInfrastructure;
import io.harness.cdng.infra.yaml.AwsSamInfrastructure;
import io.harness.cdng.infra.yaml.AzureWebAppInfrastructure;
import io.harness.cdng.infra.yaml.CustomDeploymentInfrastructure;
import io.harness.cdng.infra.yaml.EcsInfrastructure;
import io.harness.cdng.infra.yaml.ElastigroupInfrastructure;
import io.harness.cdng.infra.yaml.GoogleFunctionsInfrastructure;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.cdng.infra.yaml.K8sAwsInfrastructure;
import io.harness.cdng.infra.yaml.K8sGcpInfrastructure;
import io.harness.cdng.infra.yaml.K8sRancherInfrastructure;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.infra.yaml.ServerlessAwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.cdng.infra.yaml.TanzuApplicationServiceInfrastructure;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.morphia.annotations.Entity;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = K8SDirectInfrastructure.class, name = "kubernetes-direct")
  , @JsonSubTypes.Type(value = K8sGcpInfrastructure.class, name = "kubernetes-gcp"),
      @JsonSubTypes.Type(value = PdcInfrastructure.class, name = "pdc"),
      @JsonSubTypes.Type(value = SshWinRmAzureInfrastructure.class, name = "ssh-winrm-azure"),
      @JsonSubTypes.Type(value = SshWinRmAwsInfrastructure.class, name = "ssh-winrm-aws"),
      @JsonSubTypes.Type(value = ServerlessAwsLambdaInfrastructure.class, name = "serverless-aws-lambda"),
      @JsonSubTypes.Type(value = AzureWebAppInfrastructure.class, name = "azure-webapp"),
      @JsonSubTypes.Type(value = EcsInfrastructure.class, name = "ECS"),
      @JsonSubTypes.Type(value = ElastigroupInfrastructure.class, name = "Elastigroup"),
      @JsonSubTypes.Type(value = CustomDeploymentInfrastructure.class, name = "custom-deployment"),
      @JsonSubTypes.Type(value = TanzuApplicationServiceInfrastructure.class, name = "tanzu-application-service"),
      @JsonSubTypes.Type(value = AsgInfrastructure.class, name = "Asg"),
      @JsonSubTypes.Type(value = GoogleFunctionsInfrastructure.class, name = "GoogleCloudFunctions"),
      @JsonSubTypes.Type(value = AwsSamInfrastructure.class, name = "AWS_SAM"),
      @JsonSubTypes.Type(value = K8sAwsInfrastructure.class, name = "kubernetes-aws"),
      @JsonSubTypes.Type(value = K8sRancherInfrastructure.class, name = "kubernetes-rancher")
})
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "infrastructureMapping")
public interface InfraMapping extends PersistentEntity, UuidAware, Outcome {
  void setUuid(String uuid);
  void setAccountId(String accountId);
}
