package software.wings.instancesyncv2.handler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;

import static software.wings.instancesyncv2.CgInstanceSyncServiceV2.AUTO_SCALE;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.delegate.Capability;
import io.harness.exception.InvalidRequestException;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesyncv2.CgDeploymentReleaseDetails;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams;
import io.harness.perpetualtask.instancesyncv2.DirectK8sInstanceSyncTaskDetails;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;

import software.wings.api.AwsAutoScalingGroupDeploymentInfo;
import software.wings.api.ContainerDeploymentInfoWithLabels;
import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.DeploymentType;
import software.wings.api.K8sDeploymentInfo;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.info.ContainerInfo;
import software.wings.beans.infrastructure.instance.info.Ec2InstanceInfo;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.dl.WingsMongoPersistence;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.instancesyncv2.model.CgK8sReleaseIdentifier;
import software.wings.instancesyncv2.model.CgReleaseIdentifiers;
import software.wings.instancesyncv2.model.InstanceSyncTaskDetails;
import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.instance.InstanceUtils;
import software.wings.service.impl.instance.sync.ContainerSync;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.aws.manager.AwsAsgHelperServiceManager;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingVariableTypes;

import com.amazonaws.services.ec2.model.Filter;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.groovy.util.Maps;

@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class AwsSshInstanceSyncV2HandlerCg implements CgInstanceSyncV2Handler {
  private final ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  private final AwsAsgHelperServiceManager awsAsgHelperServiceManager;

  private final InfrastructureMappingService infrastructureMappingService;
  private final AwsUtils awsUtils;
  private final SettingsService settingsService;
  private final KryoSerializer kryoSerializer;
  private final SecretManager secretManager;
  private final InstanceUtils instanceUtil;
  private final ServiceResourceService serviceResourceService;
  private final EnvironmentService environmentService;
  private final AppService appService;
  private final WingsMongoPersistence wingsPersistence;
  private final ContainerSync containerSync;
  @Override
  public PerpetualTaskExecutionBundle fetchInfraConnectorDetails(SettingAttribute cloudProvider) {
    if (!SettingVariableTypes.AWS.name().equals(cloudProvider.getValue().getType())) {
      log.error("Passed cloud provider is not of type AWS. Type passed: [{}]", cloudProvider.getValue().getType());
      throw new InvalidRequestException("Cloud Provider not of type AWS");
    }

    PerpetualTaskExecutionBundle.Builder builder =
        PerpetualTaskExecutionBundle.newBuilder()
            .setTaskParams(
                Any.pack(CgInstanceSyncTaskParams.newBuilder()
                             .setAccountId(cloudProvider.getAccountId())
                             .setCloudProviderType(cloudProvider.getValue().getType())
                             .setCloudProviderDetails(ByteString.copyFrom(kryoSerializer.asBytes(cloudProvider)))
                             .build()))
            .putAllSetupAbstractions(Maps.of(NG, "false", OWNER, cloudProvider.getAccountId()));
    cloudProvider.getValue().fetchRequiredExecutionCapabilities(null).forEach(executionCapability
        -> builder
               .addCapabilities(
                   Capability.newBuilder()
                       .setKryoCapability(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(executionCapability)))
                       .build())
               .build());
    return builder.build();
  }

  @Override
  public InstanceSyncTaskDetails prepareTaskDetails(
      DeploymentSummary deploymentSummary, String cloudProviderId, String perpetualTaskId) {
    return InstanceSyncTaskDetails.builder()
        .accountId(deploymentSummary.getAccountId())
        .appId(deploymentSummary.getAppId())
        .perpetualTaskId(perpetualTaskId)
        .cloudProviderId(cloudProviderId)
        .infraMappingId(deploymentSummary.getInfraMappingId())
        .lastSuccessfulRun(0L)
        .releaseIdentifiers(buildReleaseIdentifiers(deploymentSummary.getDeploymentInfo()))
        .build();
  }

  @Override
  public Set<CgReleaseIdentifiers> buildReleaseIdentifiers(DeploymentInfo deploymentInfo) {
    return null;
  }

  @Override
  public Set<CgReleaseIdentifiers> mergeReleaseIdentifiers(
      Set<CgReleaseIdentifiers> releaseIdentifiers, Set<CgReleaseIdentifiers> buildReleaseIdentifiers) {
    return null;
  }

  @Override
  public List<CgDeploymentReleaseDetails> getDeploymentReleaseDetails(InstanceSyncTaskDetails taskDetails) {
    InfrastructureMapping infraMapping =
        infrastructureMappingService.get(taskDetails.getAppId(), taskDetails.getInfraMappingId());

    if (!(infraMapping instanceof AwsInfrastructureMapping)) {
      log.error("Unsupported infrastructure mapping being tracked here: [{}]. InfraMappingType found: [{}]",
          taskDetails, infraMapping.getClass().getName());
      if (Objects.isNull(taskDetails.getReleaseIdentifiers())) {
        return Collections.emptyList();
      }
    }
    if (Objects.isNull(taskDetails.getReleaseIdentifiers())) {
      return Collections.emptyList();
    }
    AwsInfrastructureMapping containerInfraMapping = (AwsInfrastructureMapping) infraMapping;
    SettingAttribute awsCloudProvider = settingsService.get(infraMapping.getComputeProviderSettingId());
    DeploymentType deploymentType =
        serviceResourceService.getDeploymentType(infraMapping, null, infraMapping.getServiceId());
    List<Filter> filters =
        awsUtils.getFilters(deploymentType, ((AwsInfrastructureMapping) infraMapping).getAwsInstanceFilter());
    AwsConfig awsConfig = (AwsConfig) awsCloudProvider.getValue();

    List<CgDeploymentReleaseDetails> releaseDetails = new ArrayList<>();
    taskDetails.getReleaseIdentifiers()
        .parallelStream()
        .filter(releaseIdentifier -> releaseIdentifier instanceof CgK8sReleaseIdentifier)
        .map(releaseIdentifier -> (CgK8sReleaseIdentifier) releaseIdentifier)
        .forEach(releaseIdentifier
            -> releaseDetails.addAll(
                releaseIdentifier.getNamespaces()
                    .parallelStream()
                    .map(namespace
                        -> CgDeploymentReleaseDetails.newBuilder()
                               .setTaskDetailsId(taskDetails.getUuid())
                               .setInfraMappingId(taskDetails.getInfraMappingId())
                               .setInfraMappingType(infraMapping.getInfraMappingType())
                               .setReleaseDetails(
                                   Any.pack(AwsSshInstanceSyncTaskDetails.newBuilder()
                                                .setRegion(containerInfraMapping.getRegion())
                                                .setAwsConfig(ByteString.copyFrom(kryoSerializer.asBytes(awsConfig)))
                                                .setFilter(ByteString.copyFrom(kryoSerializer.asBytes(filters)))
                                                .setEncryptedData(ByteString.copyFrom(kryoSerializer.asBytes(
                                                    secretManager.getEncryptionDetails(awsConfig))))
                                                .build()))
                               .build())
                    .collect(Collectors.toList())));

    return releaseDetails;
  }

  @Override
  public boolean isDeploymentInfoTypeSupported(Class<? extends DeploymentInfo> deploymentInfoClazz) {
    return deploymentInfoClazz.equals(AwsAutoScalingGroupDeploymentInfo.class);
  }

  @Override
  public List<Instance> difference(List<Instance> list1, List<Instance> list2) {
    Map<String, Instance> instanceKeyMapList1 = getInstanceKeyMap(list1);
    Map<String, Instance> instanceKeyMapList2 = getInstanceKeyMap(list2);

    Sets.SetView<String> difference = Sets.difference(instanceKeyMapList1.keySet(), instanceKeyMapList2.keySet());

    return difference.parallelStream().map(instanceKeyMapList1::get).collect(Collectors.toList());
  }

  private Map<String, Instance> getInstanceKeyMap(List<Instance> instanceList) {
    Map<String, Instance> instanceKeyMap = new HashMap<>();
    for (Instance instance : instanceList) {
      Ec2InstanceInfo ec2InstanceInfo = (Ec2InstanceInfo) instance.getInstanceInfo();
      if (Objects.nonNull(ec2InstanceInfo.getEc2Instance().getInstanceId())) {
        instanceKeyMap.put(ec2InstanceInfo.getEc2Instance().getInstanceId(), instance);
      }
    }
    return instanceKeyMap;
  }

  @Override
  public List<Instance> getDeployedInstances(DeploymentSummary deploymentSummary) {
    return null;
  }

  @Override
  public List<Instance> getDeployedInstances(List<InstanceInfo> instanceInfos, Instance lastDiscoveredInstance) {
    if (CollectionUtils.isEmpty(instanceInfos) || Objects.isNull(lastDiscoveredInstance)) {
      return Collections.emptyList();
    }

    DeploymentSummary deploymentSummary = generateDeploymentSummaryFromInstance(lastDiscoveredInstance);

    if (lastDiscoveredInstance.getInstanceInfo() instanceof Ec2InstanceInfo) {
      Ec2InstanceInfo instanceInfo = (Ec2InstanceInfo) lastDiscoveredInstance.getInstanceInfo();
      deploymentSummary.setDeploymentInfo(AwsAutoScalingGroupDeploymentInfo.builder().build());
      InfrastructureMapping infrastructureMapping =
          infrastructureMappingService.get(deploymentSummary.getAppId(), deploymentSummary.getInfraMappingId());
      return getEc2InstancesFromAutoScalingGroup(deploymentSummary, infrastructureMapping,
          instanceInfos.parallelStream().map(K8sPodInfo.class ::cast).collect(Collectors.toList()));
    } else if (lastDiscoveredInstance.getInstanceInfo() instanceof KubernetesContainerInfo) {
      KubernetesContainerInfo containerInfo = (KubernetesContainerInfo) lastDiscoveredInstance.getInstanceInfo();
      deploymentSummary.setDeploymentInfo(ContainerDeploymentInfoWithLabels.builder()
                                              .namespace(containerInfo.getNamespace())
                                              .helmChartInfo(containerInfo.getHelmChartInfo())
                                              .build());
      InfrastructureMapping infrastructureMapping =
          infrastructureMappingService.get(deploymentSummary.getAppId(), deploymentSummary.getInfraMappingId());
      ContainerInfrastructureMapping containerInfraMapping = (ContainerInfrastructureMapping) infrastructureMapping;
      return getInstancesForContainerPods(deploymentSummary, containerInfraMapping,
          instanceInfos.parallelStream().map(ContainerInfo.class ::cast).collect(Collectors.toList()));
    }

    log.error("Unknown instance info type: [{}] found. Doing nothing.",
        lastDiscoveredInstance.getInstanceInfo().getClass().getName());
    return Collections.emptyList();

    return null;
  }

  private DeploymentSummary generateDeploymentSummaryFromInstance(Instance instance) {
    DeploymentSummary deploymentSummary = DeploymentSummary.builder().build();
    deploymentSummary.setAppId(instance.getAppId());
    deploymentSummary.setAccountId(instance.getAccountId());
    deploymentSummary.setInfraMappingId(instance.getInfraMappingId());
    deploymentSummary.setWorkflowExecutionId(instance.getLastWorkflowExecutionId());
    deploymentSummary.setWorkflowExecutionName(instance.getLastWorkflowExecutionName());
    deploymentSummary.setWorkflowId(instance.getLastWorkflowExecutionId());

    deploymentSummary.setArtifactId(instance.getLastArtifactId());
    deploymentSummary.setArtifactName(instance.getLastArtifactName());
    deploymentSummary.setArtifactStreamId(instance.getLastArtifactStreamId());
    deploymentSummary.setArtifactSourceName(instance.getLastArtifactSourceName());
    deploymentSummary.setArtifactBuildNum(instance.getLastArtifactBuildNum());

    deploymentSummary.setPipelineExecutionId(instance.getLastPipelineExecutionId());
    deploymentSummary.setPipelineExecutionName(instance.getLastPipelineExecutionName());

    // Commented this out, so we can distinguish between autoscales instances and instances we deployed
    deploymentSummary.setDeployedById(AUTO_SCALE);
    deploymentSummary.setDeployedByName(AUTO_SCALE);
    deploymentSummary.setDeployedAt(System.currentTimeMillis());
    deploymentSummary.setArtifactBuildNum(instance.getLastArtifactBuildNum());

    return deploymentSummary;
  }
  @Override
  public List<Instance> instancesToUpdate(List<Instance> instances, List<Instance> instancesInDb) {
    Map<String, Instance> instancesKeyMap = getInstanceKeyMap(instances);
    Map<String, Instance> instancesInDbKeyMap = getInstanceKeyMap(instancesInDb);

    Sets.SetView<String> intersection = Sets.intersection(instancesKeyMap.keySet(), instancesInDbKeyMap.keySet());
    List<Instance> instancesToUpdate = new ArrayList<>();
    for (String instanceKey : intersection) {
      if (!instancesKeyMap.get(instanceKey)
               .getInstanceInfo()
               .equals(instancesInDbKeyMap.get(instanceKey).getInstanceInfo())) {
        Instance instance = instancesInDbKeyMap.get(instanceKey);
        instance.setInstanceInfo(instancesKeyMap.get(instanceKey).getInstanceInfo());
        instancesToUpdate.add(instance);
      }
    }

    return instancesToUpdate;
  }
  protected List<com.amazonaws.services.ec2.model.Instance> getEc2InstancesFromAutoScalingGroup(String region,
      String autoScalingGroupName, AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String appId) {
    return awsAsgHelperServiceManager.listAutoScalingGroupInstances(
        awsConfig, encryptionDetails, region, autoScalingGroupName, appId);
  }
}
