/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.graphql.schema.mutation.cloudProvider;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.mutation.QLMutationInput;
import software.wings.graphql.schema.mutation.cloudProvider.aws.QLUpdateAwsCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.k8s.QLUpdateK8sCloudProviderInput;
import software.wings.graphql.schema.type.QLCloudProviderType;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.Scope;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Scope(PermissionAttribute.ResourceType.SETTING)
@JsonIgnoreProperties(ignoreUnknown = true)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class QLUpdateCloudProviderInput implements QLMutationInput {
  private String clientMutationId;

  private String cloudProviderId;
  private QLCloudProviderType cloudProviderType;
  private QLUpdatePcfCloudProviderInput pcfCloudProvider;
  private QLUpdateSpotInstCloudProviderInput spotInstCloudProvider;
  private QLUpdateGcpCloudProviderInput gcpCloudProvider;
  private QLUpdateK8sCloudProviderInput k8sCloudProvider;
  private QLUpdatePhysicalDataCenterCloudProviderInput physicalDataCenterCloudProvider;
  private QLUpdateAzureCloudProviderInput azureCloudProvider;
  private QLUpdateAwsCloudProviderInput awsCloudProvider;
  private QLUpdateRancherCloudProviderInput rancherCloudProvider;
}
