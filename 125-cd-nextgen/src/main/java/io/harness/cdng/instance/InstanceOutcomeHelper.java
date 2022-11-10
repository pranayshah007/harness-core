/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.instance;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.CustomDeploymentInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAwsInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAzureInfrastructureOutcome;
import io.harness.cdng.instance.outcome.HostOutcome;
import io.harness.cdng.instance.outcome.InstanceOutcome;
import io.harness.cdng.instance.outcome.InstancesOutcome;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.azure.response.AzureHostResponse;
import io.harness.delegate.beans.azure.response.AzureHostsResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListEC2InstancesTaskResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.steps.OutputExpressionConstants;
import io.harness.yaml.infra.HostConnectionTypeKind;

import software.wings.service.impl.aws.model.AwsEC2Instance;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class InstanceOutcomeHelper {
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  public InstancesOutcome saveAndGetInstancesOutcome(Ambiance ambiance, InfrastructureOutcome infrastructureOutcome,
      DelegateResponseData responseData, Set<String> hostNames) {
    InstancesOutcome instancesOutcome = buildInstancesOutcome(infrastructureOutcome, responseData, hostNames);
    executionSweepingOutputService.consume(
        ambiance, OutputExpressionConstants.INSTANCES, instancesOutcome, StepCategory.STAGE.name());

    return instancesOutcome;
  }

  public InstancesOutcome saveAndGetInstancesOutcome(
      Ambiance ambiance, InfrastructureOutcome infrastructureOutcome, Set<String> hostNames) {
    InstancesOutcome instancesOutcome = buildInstancesOutcome(infrastructureOutcome, null, hostNames);
    executionSweepingOutputService.consume(
        ambiance, OutputExpressionConstants.INSTANCES, instancesOutcome, StepCategory.STAGE.name());

    return instancesOutcome;
  }

  public String mapToHostNameBasedOnHostConnectionTypeAWS(
      InfrastructureOutcome infrastructureOutcome, AwsEC2Instance awsEC2Instance) {
    if (!(infrastructureOutcome instanceof SshWinRmAwsInfrastructureOutcome)) {
      throw new InvalidRequestException(
          format("Not supported infrastructure kind : [%s]", infrastructureOutcome.getKind()));
    }
    final String hostConnectionType =
        ((SshWinRmAwsInfrastructureOutcome) infrastructureOutcome).getHostConnectionType();

    if (HostConnectionTypeKind.PUBLIC_IP.equals(hostConnectionType) && isNotEmpty(awsEC2Instance.getPublicIp())) {
      return awsEC2Instance.getPublicIp();
    } else if (HostConnectionTypeKind.PRIVATE_IP.equals(hostConnectionType)
        && isNotEmpty(awsEC2Instance.getPrivateIp())) {
      return awsEC2Instance.getPrivateIp();
    } else {
      return awsEC2Instance.getHostname();
    }
  }

  public String mapToHostNameBasedOnHostConnectionTypeAzure(
      InfrastructureOutcome infrastructureOutcome, AzureHostResponse azureHostResponse) {
    if (!(infrastructureOutcome instanceof SshWinRmAzureInfrastructureOutcome)) {
      throw new InvalidRequestException(
          format("Not supported infrastructure kind : [%s]", infrastructureOutcome.getKind()));
    }
    final String hostConnectionType =
        ((SshWinRmAzureInfrastructureOutcome) infrastructureOutcome).getHostConnectionType();

    // We achieve backward compatibility here new manager + old delegate.
    // We resolve host connection type logic on old delegate and populate address.
    return azureHostResponse.getAddress();
  }

  private InstancesOutcome buildInstancesOutcome(
      InfrastructureOutcome infrastructureOutcome, @NotNull DelegateResponseData responseData, Set<String> hostNames) {
    List<InstanceOutcome> instanceOutcomes;
    if (infrastructureOutcome instanceof SshWinRmAzureInfrastructureOutcome) {
      if (!(responseData instanceof AzureHostsResponse)) {
        throw new InvalidArgumentsException(
            format("Invalid delegate response data for SshWinRmAzureInfrastructureOutcome, response data class: %s",
                responseData.getClass()));
      }
      AzureHostsResponse azureHostsResponse = (AzureHostsResponse) responseData;
      List<AzureHostResponse> azureHosts = azureHostsResponse.getHosts();
      instanceOutcomes = hostNames.stream()
                             .map(hostName -> {
                               AzureHostResponse azureInstance =
                                   getAzureHostInstanceByHostName(infrastructureOutcome, azureHosts, hostName);
                               return InstanceOutcome.builder()
                                   .name(hostName)
                                   .hostName(hostName)
                                   .host(HostOutcome.builder()
                                             .hostName(azureInstance.getHostName())
                                             .privateIp(azureInstance.getPrivateIp())
                                             .publicIp(azureInstance.getPublicIp())
                                             .build())
                                   .build();
                             })
                             .collect(Collectors.toList());
    } else if (infrastructureOutcome instanceof SshWinRmAwsInfrastructureOutcome) {
      if (!(responseData instanceof AwsListEC2InstancesTaskResponse)) {
        throw new InvalidArgumentsException(
            format("Invalid delegate response data for SshWinRmAwsInfrastructureOutcome, response data class: %s",
                responseData.getClass()));
      }
      AwsListEC2InstancesTaskResponse awsListEC2InstancesTaskResponse = (AwsListEC2InstancesTaskResponse) responseData;
      List<AwsEC2Instance> awsEC2Instances = awsListEC2InstancesTaskResponse.getInstances();
      instanceOutcomes = hostNames.stream()
                             .map(hostName -> {
                               AwsEC2Instance awsEC2Instance =
                                   getAwsEC2InstanceByHostName(infrastructureOutcome, awsEC2Instances, hostName);
                               return InstanceOutcome.builder()
                                   .name(hostName)
                                   .hostName(hostName)
                                   .host(HostOutcome.builder()
                                             .hostName(awsEC2Instance.getHostname())
                                             .privateIp(awsEC2Instance.getPrivateIp())
                                             .publicIp(awsEC2Instance.getPublicIp())
                                             .build())
                                   .build();
                             })
                             .collect(Collectors.toList());
    } else if (infrastructureOutcome instanceof PdcInfrastructureOutcome) {
      instanceOutcomes = hostNames.stream()
                             .map(hostName
                                 -> InstanceOutcome.builder()
                                        .name(hostName)
                                        .hostName(hostName)
                                        .host(HostOutcome.builder().hostName(hostName).build())
                                        .build())
                             .collect(Collectors.toList());
    } else if (infrastructureOutcome instanceof CustomDeploymentInfrastructureOutcome) {
      instanceOutcomes = Collections.emptyList();
    } else {
      throw new InvalidArgumentsException(
          format("Unsupported instance outcome kind for building instances outcome, infrastructureOutcomeKind: %s",
              infrastructureOutcome.getKind()));
    }

    return InstancesOutcome.builder().instances(instanceOutcomes).build();
  }

  private AwsEC2Instance getAwsEC2InstanceByHostName(
      InfrastructureOutcome infrastructureOutcome, List<AwsEC2Instance> awsEC2Instances, String hostName) {
    if (isEmpty(hostName)) {
      throw new InvalidArgumentsException("Host name cannot be null or empty");
    }

    return awsEC2Instances.stream()
        .filter(Objects::nonNull)
        .filter(awsEC2Instance
            -> hostName.equals(mapToHostNameBasedOnHostConnectionTypeAWS(infrastructureOutcome, awsEC2Instance)))
        .findFirst()
        .orElseThrow(
            () -> new InvalidRequestException(format("Not found AWS EC2 instance for hostName: %s", hostName)));
  }

  private AzureHostResponse getAzureHostInstanceByHostName(
      InfrastructureOutcome infrastructureOutcome, List<AzureHostResponse> azureInstances, String hostName) {
    if (isEmpty(hostName)) {
      throw new InvalidArgumentsException("Host name cannot be null or empty");
    }

    return azureInstances.stream()
        .filter(Objects::nonNull)
        .filter(azureHostResponse
            -> hostName.equals(mapToHostNameBasedOnHostConnectionTypeAzure(infrastructureOutcome, azureHostResponse)))
        .findFirst()
        .orElseThrow(() -> new InvalidRequestException(format("Not found Azure instance for hostName: %s", hostName)));
  }
}
