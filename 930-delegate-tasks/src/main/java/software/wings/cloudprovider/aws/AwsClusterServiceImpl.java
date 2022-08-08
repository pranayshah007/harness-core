/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.cloudprovider.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.utils.EcsConvention.getRevisionFromServiceName;
import static software.wings.utils.EcsConvention.getServiceNamePrefixFromServiceName;

import static java.lang.String.format;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.container.ContainerInfo;
import io.harness.exception.WingsException;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.SettingAttribute;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.cloudprovider.ClusterConfiguration;

import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.CreateServiceRequest;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RunTaskRequest;
import com.amazonaws.services.ecs.model.RunTaskResult;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by anubhaw on 12/29/16.
 */
@Singleton
@Slf4j
@TargetModule(HarnessModule._960_API_SERVICES)
@OwnedBy(CDP)
public class AwsClusterServiceImpl implements AwsClusterService {
  @Inject private EcsContainerService ecsContainerService;

  @Override
  public void createCluster(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, ClusterConfiguration clusterConfiguration,
      LogCallback logCallback) {
    if (!(clusterConfiguration instanceof AwsClusterConfiguration)) {
      throw new WingsException("Unexpected type of cluster configuration");
    }

    AwsClusterConfiguration awsClusterConfiguration = (AwsClusterConfiguration) clusterConfiguration;

    // Provision cloud infra nodes
    Map<String, Object> params = new HashMap<>();
    params.put("availabilityZones", awsClusterConfiguration.getAvailabilityZones());
    params.put("vpcZoneIdentifiers", awsClusterConfiguration.getVpcZoneIdentifiers());
    params.put("clusterName", clusterConfiguration.getName());
    params.put("autoScalingGroupName", ((AwsClusterConfiguration) clusterConfiguration).getAutoScalingGroupName());

    ecsContainerService.provisionNodes(region, cloudProviderSetting, encryptedDataDetails,
        awsClusterConfiguration.getSize(), awsClusterConfiguration.getLauncherConfiguration(), params, logCallback);

    log.info("Successfully created cluster and provisioned desired number of nodes");
  }

  @Override
  public List<ContainerInfo> resizeCluster(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, String clusterName, String serviceName, int previousCount,
      int desiredCount, int serviceSteadyStateTimeout, ExecutionLogCallback executionLogCallback,
      boolean timeoutErrorSupported) {
    if (previousCount != desiredCount) {
      executionLogCallback.saveExecutionLog(format("Resize service [%s] in cluster [%s] from %s to %s instances",
          serviceName, clusterName, previousCount, desiredCount));
    } else {
      executionLogCallback.saveExecutionLog(
          format("Service [%s] in cluster [%s] stays at %s instances", serviceName, clusterName, previousCount));
    }
    return ecsContainerService.provisionTasks(region, cloudProviderSetting, encryptedDataDetails, clusterName,
        serviceName, previousCount, desiredCount, serviceSteadyStateTimeout, executionLogCallback,
        timeoutErrorSupported);
  }

  @Override
  public void deleteService(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, String clusterName, String serviceName) {
    ecsContainerService.deleteService(region, cloudProviderSetting, encryptedDataDetails, clusterName, serviceName);
    log.info("Successfully deleted service {}", serviceName);
  }

  @Override
  public List<Service> getServices(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, String clusterName) {
    return ecsContainerService.getServices(region, cloudProviderSetting, encryptedDataDetails, clusterName);
  }

  @Override
  public List<Service> getServices(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, String clusterName, String serviceNamePrefix) {
    return ecsContainerService.getServices(
        region, cloudProviderSetting, encryptedDataDetails, clusterName, serviceNamePrefix);
  }

  @Override
  public TargetGroup getTargetGroup(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, String targetGroupArn) {
    return ecsContainerService.getTargetGroup(region, cloudProviderSetting, encryptedDataDetails, targetGroupArn);
  }

  @Override
  public void createService(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, CreateServiceRequest clusterConfiguration) {
    ecsContainerService.createService(region, cloudProviderSetting, encryptedDataDetails, clusterConfiguration);
  }

  @Override
  public TaskDefinition createTask(String region, SettingAttribute settingAttribute,
      List<EncryptedDataDetail> encryptedDataDetails, RegisterTaskDefinitionRequest registerTaskDefinitionRequest) {
    return ecsContainerService.createTask(
        region, settingAttribute, encryptedDataDetails, registerTaskDefinitionRequest);
  }

  @Override
  public RunTaskResult triggerRunTask(String region, SettingAttribute settingAttribute,
      List<EncryptedDataDetail> encryptedDataDetails, RunTaskRequest runTaskRequest) {
    return ecsContainerService.triggerRunTask(region, settingAttribute, encryptedDataDetails, runTaskRequest);
  }

  @Override
  public LinkedHashMap<String, Integer> getActiveServiceCounts(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, String clusterName, String containerServiceName) {
    String serviceNamePrefix = getServiceNamePrefixFromServiceName(containerServiceName);
    return getActiveServiceCountsByServiceNamePrefix(
        region, cloudProviderSetting, encryptedDataDetails, clusterName, serviceNamePrefix);
  }

  @Override
  public LinkedHashMap<String, Integer> getActiveServiceCountsByServiceNamePrefix(String region,
      SettingAttribute cloudProviderSetting, List<EncryptedDataDetail> encryptedDataDetails, String clusterName,
      String serviceNamePrefix) {
    LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
    List<Service> activeOldServices =
        getServices(region, cloudProviderSetting, encryptedDataDetails, clusterName, serviceNamePrefix)
            .stream()
            .filter(service
                -> getServiceNamePrefixFromServiceName(service.getServiceName()).equals(serviceNamePrefix)
                    && service.getDesiredCount() > 0)
            .sorted(comparingInt(service -> getRevisionFromServiceName(service.getServiceName())))
            .collect(toList());
    activeOldServices.forEach(service -> result.put(service.getServiceName(), service.getDesiredCount()));
    return result;
  }

  @Override
  public Map<String, String> getActiveServiceImages(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, String clusterName, String containerServiceName,
      String imagePrefix) {
    Map<String, String> result = new HashMap<>();
    String serviceNamePrefix = getServiceNamePrefixFromServiceName(containerServiceName);
    List<Service> activeOldServices =
        getServices(region, cloudProviderSetting, encryptedDataDetails, clusterName, serviceNamePrefix)
            .stream()
            .filter(service -> service.getServiceName().startsWith(serviceNamePrefix) && service.getDesiredCount() > 0)
            .sorted(comparingInt(service -> getRevisionFromServiceName(service.getServiceName())))
            .collect(toList());
    activeOldServices.forEach(service
        -> result.put(service.getServiceName(),
            ecsContainerService
                .getTaskDefinitionFromService(region, cloudProviderSetting, encryptedDataDetails, service)
                .getContainerDefinitions()
                .stream()
                .map(ContainerDefinition::getImage)
                .filter(image -> image.startsWith(imagePrefix + ":"))
                .findFirst()
                .orElse("none")));
    return result;
  }

  @Override
  public Optional<Service> getService(String region, SettingAttribute settingAttribute,
      List<EncryptedDataDetail> encryptedDataDetails, String clusterName, String serviceName) {
    return ecsContainerService.getService(region, settingAttribute, encryptedDataDetails, clusterName, serviceName);
  }
}
