/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.exception.utils.ExceptionUtils;
import io.harness.ff.FeatureFlagService;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.api.CloudProviderType;
import software.wings.api.DeploymentType;
import software.wings.beans.Account;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.AwsInstanceFilter.AwsInstanceFilterKeys;
import software.wings.beans.AwsLambdaInfraStructureMapping;
import software.wings.beans.AzureInfrastructureMapping;
import software.wings.beans.AzureKubernetesInfrastructureMapping;
import software.wings.beans.BlueprintProperty;
import software.wings.beans.CodeDeployInfrastructureMapping;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.GcpKubernetesInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMapping.InfrastructureMappingKeys;
import software.wings.beans.InfrastructureMappingBlueprint;
import software.wings.beans.InfrastructureMappingBlueprint.NodeFilteringType;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.InfrastructureProvisioner.InfrastructureProvisionerKeys;
import software.wings.beans.NameValuePair;
import software.wings.beans.PcfInfrastructureMapping;
import software.wings.beans.PhysicalInfrastructureMapping;
import software.wings.beans.PhysicalInfrastructureMappingWinRm;
import software.wings.dl.WingsPersistence;
import software.wings.infra.AwsAmiInfrastructure;
import software.wings.infra.AwsAmiInfrastructure.AwsAmiInfrastructureKeys;
import software.wings.infra.AwsEcsInfrastructure;
import software.wings.infra.AwsEcsInfrastructure.AwsEcsInfrastructureKeys;
import software.wings.infra.AwsInstanceInfrastructure;
import software.wings.infra.AwsInstanceInfrastructure.AwsInstanceInfrastructureKeys;
import software.wings.infra.AwsLambdaInfrastructure;
import software.wings.infra.AwsLambdaInfrastructure.AwsLambdaInfrastructureKeys;
import software.wings.infra.AzureInstanceInfrastructure;
import software.wings.infra.AzureKubernetesService;
import software.wings.infra.CodeDeployInfrastructure;
import software.wings.infra.DirectKubernetesInfrastructure;
import software.wings.infra.GoogleKubernetesEngine;
import software.wings.infra.InfraMappingInfrastructureProvider;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.InfrastructureDefinition.InfrastructureDefinitionBuilder;
import software.wings.infra.PcfInfraStructure;
import software.wings.infra.PhysicalInfra;
import software.wings.infra.PhysicalInfraWinrm;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.yaml.YamlGitService;

import com.amazonaws.services.ecs.model.LaunchType;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class InfraMappingToDefinitionMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;
  @Inject private AppService appService;
  @Inject private EnvironmentService environmentService;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private SetInfraDefinitionTriggers setInfraDefinitionTriggers;
  @Inject private SetInfraDefinitionPipelines setInfraDefinitionPipelines;
  @Inject private SetInfraDefinitionWorkflows setInfraDefinitionWorkflows;
  @Inject private MigrateDelegateScopesToInfraDefinition migrateDelegateScopesToInfraDefinition;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private YamlGitService yamlGitService;

  private final String DEBUG_LINE = " INFRA_MAPPING_MIGRATION: ";
  // Accounts Migrated "jDOmhrFmSOGZJ1C91UC_hg", "SAsyUUHTTImuYSZ35HPDvw", "-oSRX0KNRni3wCdyoesp8Q",
  // "Evq3-KwvQfK5wE8UoR1Xcw", "-czOfo4UTPumhprgLZkDYg","GsntpegQTsii4JSf_mIpIQ",
  // "DyzFTB8WQdeLLi3hMnvZyg","l8h5XdMlSG6boWd99Tg-2g", "q-smn59lTqO_m-i_Sgs6sw", "ctRbnV9aTXisj2xkIRquYQ",
  // "0LRUeE0IR8ax08KOXrMv3A","XtqjhVchTfOwuNqXiSzxdQ", "i3p84Q6oTXaN7JvCNLQJRA","UtTa95tnQqWxGByLkXlp6Q",
  // "wXdRHOtoSuK1Qdi6QWnGgA"

  // Accounts Excluded Previously "2aB3xZkET1aWYCidfxPurw", "bwBVO7N0RmKltRhTjk101A", "en9EZJ2gTCS6WeY9x-XRfg",
  //          "55563ed1-bea1-456a-943d-f28bc8fb141d", "lU1_N50mRcur3e6OO2_9sg", "x2Ynq8DDwjotzB9sw6X9nl"

  private final Set<String> accountIdsToIncluded = new HashSet<>();

  @Override
  public void migrate() {
    for (String accountId : accountIdsToIncluded) {
      Account account = accountService.get(accountId);

      log.info(StringUtils.join(DEBUG_LINE, "Starting Infra Definition migration for accountId:", accountId));
      List<String> appIds = appService.getAppIdsByAccountId(accountId);

      Map<String, InfrastructureProvisioner> infrastructureProvisionerMap = new HashMap<>();

      for (String appId : appIds) {
        log.info(StringUtils.join(DEBUG_LINE, "Starting migration for appId ", appId));

        List<String> envIds = environmentService.getEnvIdsByApp(appId);

        infrastructureProvisionerMap.clear();
        try (HIterator<InfrastructureProvisioner> infrastructureProvisionerHIterator =
                 new HIterator<>(wingsPersistence.createQuery(InfrastructureProvisioner.class)
                                     .field(InfrastructureProvisionerKeys.appId)
                                     .equal(appId)
                                     .fetch())) {
          for (InfrastructureProvisioner provisioner : infrastructureProvisionerHIterator) {
            infrastructureProvisionerMap.put(provisioner.getUuid(), provisioner);
          }
        }
        for (String envId : envIds) {
          log.info(StringUtils.join(DEBUG_LINE, "Starting migration for envId ", envId));

          try (HIterator<InfrastructureMapping> infrastructureMappingHIterator =
                   new HIterator<>(wingsPersistence.createQuery(InfrastructureMapping.class)
                                       .field(InfrastructureMappingKeys.appId)
                                       .equal(appId)
                                       .field(InfrastructureMappingKeys.envId)
                                       .equal(envId)
                                       .fetch())) {
            for (InfrastructureMapping infrastructureMapping : infrastructureMappingHIterator) {
              log.info(StringUtils.join(
                  DEBUG_LINE, "Starting migration for inframappingId ", infrastructureMapping.getUuid()));

              // If infradefinitionId is already set, then no need to migrate
              if (isEmpty(infrastructureMapping.getInfrastructureDefinitionId())) {
                Optional<InfrastructureDefinition> newInfraDefinition = createInfraDefinition(
                    infrastructureMapping, infrastructureProvisionerMap.get(infrastructureMapping.getProvisionerId()));

                newInfraDefinition.ifPresent(def -> {
                  try {
                    InfrastructureDefinition savedDefinition = infrastructureDefinitionService.save(def, true);
                    setInfraDefinitionId(savedDefinition.getUuid(), infrastructureMapping);
                    log.info(StringUtils.join(DEBUG_LINE,
                        format("Migrated infra mapping %s to infra definition %s", infrastructureMapping.getUuid(),
                            savedDefinition.getUuid())));
                  } catch (Exception ex) {
                    log.error(StringUtils.join(
                        DEBUG_LINE, ExceptionUtils.getMessage(ex), " inframapping ", infrastructureMapping.getUuid()));
                  }
                });
              } else {
                log.info(StringUtils.join(DEBUG_LINE, "skipping infra mapping ", infrastructureMapping.getUuid(),
                    " since infra definition is set to ", infrastructureMapping.getInfrastructureDefinitionId()));
              }
            }
          } catch (IllegalStateException ex) {
            log.error(StringUtils.join(DEBUG_LINE,
                format(" Infra Mapping in env %s has more than 1 provisioners referenced ", envId), ex.getMessage()));
          } catch (Exception ex) {
            log.error(StringUtils.join(
                DEBUG_LINE, format("Error migrating env %s of app %s", envId, appId), ex.getMessage()));
          }

          log.info(StringUtils.join(DEBUG_LINE, "Finished migration for envId ", envId));
        }

        log.info(StringUtils.join(DEBUG_LINE, "Finished migration for appId ", appId));
      }
      setInfraDefinitionTriggers.migrate(account);
      setInfraDefinitionPipelines.migrate(account);
      setInfraDefinitionWorkflows.migrate(account);
      setInfraDefinitionTriggers.migrate(account);
      migrateDelegateScopesToInfraDefinition.migrate(account);

      log.info(StringUtils.join(DEBUG_LINE, "Finished Infra mapping migration for accountId ", accountId));

      log.info("Enabling feature flag for accountId : [{}]", accountId);
      log.info("Enabled feature flag for accountId : [{}]", accountId);

      yamlGitService.asyncFullSyncForEntireAccount(accountId);
    }
  }

  private Optional<InfrastructureDefinition> createInfraDefinition(
      InfrastructureMapping src, InfrastructureProvisioner prov) {
    try {
      InfrastructureDefinitionBuilder definitionBuilder =
          InfrastructureDefinition.builder()
              .createdAt(src.getCreatedAt())
              .createdBy(src.getCreatedBy())
              .lastUpdatedBy(src.getLastUpdatedBy())
              .lastUpdatedAt(src.getLastUpdatedAt())
              .name(src.getName())
              .appId(src.getAppId())
              .envId(src.getEnvId())
              .deploymentType(DeploymentType.valueOf(src.getDeploymentType()))
              .cloudProviderType(CloudProviderType.valueOf(src.getComputeProviderType()))
              .provisionerId(src.getProvisionerId());

      if (isNotEmpty(src.getServiceId())) {
        definitionBuilder.scopedToServices(Arrays.asList(src.getServiceId()));
      } else {
        log.error(
            StringUtils.join(DEBUG_LINE, format("No service linked to Inframapping %s, continuing..", src.getUuid())));
      }

      List<InfrastructureMappingBlueprint> infrastructureMappingBlueprints =
          (prov == null) ? Collections.EMPTY_LIST : prov.getMappingBlueprints();

      if (prov != null) {
        log.info(StringUtils.join(DEBUG_LINE,
            format("found %s mapping blueprints for infra provisioner %s", infrastructureMappingBlueprints.size(),
                prov.getUuid())));
      }

      Map<String, String> fieldNameChanges = new HashMap<>();
      InfraMappingInfrastructureProvider infrastructure;
      if (src instanceof AwsInfrastructureMapping) {
        AwsInfrastructureMapping awsSrc = (AwsInfrastructureMapping) src;

        fieldNameChanges.clear();
        fieldNameChanges.put("vpcs", AwsInstanceFilterKeys.vpcIds);
        fieldNameChanges.put("autoScalingGroup", AwsInstanceInfrastructureKeys.autoScalingGroupName);

        infrastructure =
            AwsInstanceInfrastructure.builder()
                .cloudProviderId(awsSrc.getComputeProviderSettingId())
                .region(awsSrc.getRegion())
                .hostConnectionAttrs(awsSrc.getHostConnectionAttrs())
                .loadBalancerId(awsSrc.getLoadBalancerId())
                .loadBalancerName(awsSrc.getLoadBalancerName())
                .usePublicDns(awsSrc.isUsePublicDns())
                .hostConnectionType(awsSrc.getHostConnectionType())
                .awsInstanceFilter(awsSrc.getAwsInstanceFilter())
                .autoScalingGroupName(awsSrc.getAutoScalingGroupName())
                .desiredCapacity(awsSrc.getDesiredCapacity())
                .setDesiredCapacity(awsSrc.isSetDesiredCapacity())
                .hostNameConvention(awsSrc.getHostNameConvention())
                .provisionInstances(isProvisionInstances(awsSrc, infrastructureMappingBlueprints))
                .expressions(getExpressionsFromBluePrints(src, infrastructureMappingBlueprints, fieldNameChanges))
                .build();
      } else if (src instanceof AwsAmiInfrastructureMapping) {
        AwsAmiInfrastructureMapping awsAmiSrc = (AwsAmiInfrastructureMapping) src;

        fieldNameChanges.clear();
        fieldNameChanges.put("baseAsg", AwsAmiInfrastructureKeys.autoScalingGroupName);
        fieldNameChanges.put("classicLbs", AwsAmiInfrastructureKeys.classicLoadBalancers);
        fieldNameChanges.put("targetGroups", AwsAmiInfrastructureKeys.targetGroupArns);
        fieldNameChanges.put("stageClassicLbs", AwsAmiInfrastructureKeys.stageClassicLoadBalancers);
        fieldNameChanges.put("stageTargetGroups", AwsAmiInfrastructureKeys.stageTargetGroupArns);

        infrastructure =
            AwsAmiInfrastructure.builder()
                .cloudProviderId(awsAmiSrc.getComputeProviderSettingId())
                .region(awsAmiSrc.getRegion())
                .autoScalingGroupName(awsAmiSrc.getAutoScalingGroupName())
                .classicLoadBalancers(awsAmiSrc.getClassicLoadBalancers())
                .targetGroupArns(awsAmiSrc.getTargetGroupArns())
                .hostNameConvention(awsAmiSrc.getHostNameConvention())
                .stageClassicLoadBalancers(awsAmiSrc.getStageClassicLoadBalancers())
                .stageTargetGroupArns(awsAmiSrc.getStageTargetGroupArns())
                .expressions(getExpressionsFromBluePrints(src, infrastructureMappingBlueprints, fieldNameChanges))
                .build();
      } else if (src instanceof AwsLambdaInfraStructureMapping) {
        AwsLambdaInfraStructureMapping awsLambdaSrc = (AwsLambdaInfraStructureMapping) src;

        fieldNameChanges.clear();
        fieldNameChanges.put("securityGroups", AwsLambdaInfrastructureKeys.securityGroupIds);

        infrastructure =
            AwsLambdaInfrastructure.builder()
                .cloudProviderId(awsLambdaSrc.getComputeProviderSettingId())
                .region(awsLambdaSrc.getRegion())
                .vpcId(awsLambdaSrc.getVpcId())
                .subnetIds(awsLambdaSrc.getSubnetIds())
                .securityGroupIds(awsLambdaSrc.getSecurityGroupIds())
                .role(awsLambdaSrc.getRole())
                .expressions(getExpressionsFromBluePrints(src, infrastructureMappingBlueprints, fieldNameChanges))
                .build();
      } else if (src instanceof CodeDeployInfrastructureMapping) {
        CodeDeployInfrastructureMapping codeDeploySrc = (CodeDeployInfrastructureMapping) src;

        fieldNameChanges.clear();

        infrastructure = CodeDeployInfrastructure.builder()
                             .cloudProviderId(codeDeploySrc.getComputeProviderSettingId())
                             .region(codeDeploySrc.getRegion())
                             .applicationName(codeDeploySrc.getApplicationName())
                             .deploymentConfig(codeDeploySrc.getDeploymentConfig())
                             .deploymentGroup(codeDeploySrc.getDeploymentGroup())
                             .hostNameConvention(codeDeploySrc.getHostNameConvention())
                             .build();
      } else if (src instanceof EcsInfrastructureMapping) {
        EcsInfrastructureMapping ecsSrc = (EcsInfrastructureMapping) src;

        fieldNameChanges.clear();
        fieldNameChanges.put("ecsCluster", AwsEcsInfrastructureKeys.clusterName);
        fieldNameChanges.put("ecsVpc", AwsEcsInfrastructureKeys.vpcId);
        fieldNameChanges.put("ecsSubnets", AwsEcsInfrastructureKeys.subnetIds);
        fieldNameChanges.put("ecsSgs", AwsEcsInfrastructureKeys.securityGroupIds);
        fieldNameChanges.put("ecsTaskExecutionRole", AwsEcsInfrastructureKeys.executionRole);

        infrastructure =
            AwsEcsInfrastructure.builder()
                .cloudProviderId(ecsSrc.getComputeProviderSettingId())
                .region(ecsSrc.getRegion())
                .vpcId(ecsSrc.getVpcId())
                .subnetIds(ecsSrc.getSubnetIds())
                .securityGroupIds(ecsSrc.getSecurityGroupIds())
                .assignPublicIp(ecsSrc.isAssignPublicIp())
                .executionRole(ecsSrc.getExecutionRole())
                .launchType(getLaunchType(ecsSrc, infrastructureMappingBlueprints))
                .clusterName(ecsSrc.getClusterName())
                .expressions(getExpressionsFromBluePrints(src, infrastructureMappingBlueprints, fieldNameChanges))
                .build();
      } else if (src instanceof GcpKubernetesInfrastructureMapping) {
        GcpKubernetesInfrastructureMapping gcpSrc = (GcpKubernetesInfrastructureMapping) src;

        fieldNameChanges.clear();

        infrastructure =
            GoogleKubernetesEngine.builder()
                .cloudProviderId(gcpSrc.getComputeProviderSettingId())
                .clusterName(gcpSrc.getClusterName())
                .namespace(gcpSrc.getNamespace())
                .releaseName(gcpSrc.getReleaseName())
                .expressions(getExpressionsFromBluePrints(src, infrastructureMappingBlueprints, fieldNameChanges))
                .build();
      } else if (src instanceof DirectKubernetesInfrastructureMapping) {
        DirectKubernetesInfrastructureMapping directK8sSrc = (DirectKubernetesInfrastructureMapping) src;

        fieldNameChanges.clear();

        infrastructure = DirectKubernetesInfrastructure.builder()
                             .cloudProviderId(directK8sSrc.getComputeProviderSettingId())
                             .clusterName(directK8sSrc.getClusterName())
                             .namespace(directK8sSrc.getNamespace())
                             .releaseName(directK8sSrc.getReleaseName())
                             .build();
      } else if (src instanceof AzureInfrastructureMapping) {
        AzureInfrastructureMapping azureSrc = (AzureInfrastructureMapping) src;

        fieldNameChanges.clear();

        infrastructure = AzureInstanceInfrastructure.builder()
                             .cloudProviderId(azureSrc.getComputeProviderSettingId())
                             .subscriptionId(azureSrc.getSubscriptionId())
                             .resourceGroup(azureSrc.getResourceGroup())
                             .tags(azureSrc.getTags())
                             .hostConnectionAttrs(azureSrc.getHostConnectionAttrs())
                             .winRmConnectionAttributes(azureSrc.getWinRmConnectionAttributes())
                             .usePublicDns(azureSrc.isUsePublicDns())
                             .build();
      } else if (src instanceof AzureKubernetesInfrastructureMapping) {
        AzureKubernetesInfrastructureMapping azureK8sSrc = (AzureKubernetesInfrastructureMapping) src;

        fieldNameChanges.clear();

        infrastructure = AzureKubernetesService.builder()
                             .cloudProviderId(azureK8sSrc.getComputeProviderSettingId())
                             .clusterName(azureK8sSrc.getClusterName())
                             .namespace(azureK8sSrc.getNamespace())
                             .subscriptionId(azureK8sSrc.getSubscriptionId())
                             .resourceGroup(azureK8sSrc.getResourceGroup())
                             .releaseName(azureK8sSrc.getReleaseName())
                             .build();
      } else if (src instanceof PcfInfrastructureMapping) {
        PcfInfrastructureMapping pcfSrc = (PcfInfrastructureMapping) src;

        fieldNameChanges.clear();

        infrastructure = PcfInfraStructure.builder()
                             .cloudProviderId(pcfSrc.getComputeProviderSettingId())
                             .organization(pcfSrc.getOrganization())
                             .space(pcfSrc.getSpace())
                             .tempRouteMap(pcfSrc.getTempRouteMap())
                             .routeMaps(pcfSrc.getRouteMaps())
                             .build();
      } else if (src instanceof PhysicalInfrastructureMapping) {
        PhysicalInfrastructureMapping physicalSrc = (PhysicalInfrastructureMapping) src;

        fieldNameChanges.clear();
        fieldNameChanges.put("Hostname", PhysicalInfra.hostname);

        infrastructure =
            PhysicalInfra.builder()
                .cloudProviderId(physicalSrc.getComputeProviderSettingId())
                .hostNames(physicalSrc.getHostNames())
                .hosts(physicalSrc.hosts())
                .loadBalancerId(physicalSrc.getLoadBalancerId())
                .loadBalancerName(physicalSrc.getLoadBalancerName())
                .hostConnectionAttrs(physicalSrc.getHostConnectionAttrs())
                .expressions(getExpressionsFromBluePrints(src, infrastructureMappingBlueprints, fieldNameChanges))
                .build();
      } else if (src instanceof PhysicalInfrastructureMappingWinRm) {
        PhysicalInfrastructureMappingWinRm physicalWinRmSrc = (PhysicalInfrastructureMappingWinRm) src;

        fieldNameChanges.clear();

        infrastructure = PhysicalInfraWinrm.builder()
                             .cloudProviderId(physicalWinRmSrc.getComputeProviderSettingId())
                             .hostNames(physicalWinRmSrc.getHostNames())
                             .hosts(physicalWinRmSrc.hosts())
                             .loadBalancerId(physicalWinRmSrc.getLoadBalancerId())
                             .loadBalancerName(physicalWinRmSrc.getLoadBalancerName())
                             .winRmConnectionAttributes(physicalWinRmSrc.getWinRmConnectionAttributes())
                             .build();
      } else {
        infrastructure = null;
        log.error(StringUtils.join(DEBUG_LINE, " Unknown type for infra mapping %s ", src.getUuid()));
      }
      return Optional.of(definitionBuilder.infrastructure(infrastructure).build());
    } catch (Exception ex) {
      log.error(StringUtils.join(DEBUG_LINE, ExceptionUtils.getMessage(ex),
          " Could not create infradefinition for inframapping ", src.getUuid()));
      return Optional.empty();
    }
  }

  private boolean isProvisionInstances(
      AwsInfrastructureMapping awsInfrastructureMapping, List<InfrastructureMappingBlueprint> blueprints) {
    if (CollectionUtils.isEmpty(blueprints)) {
      return awsInfrastructureMapping.isProvisionInstances();
    }
    final String serviceId = awsInfrastructureMapping.getServiceId();
    final List<InfrastructureMappingBlueprint> blueprintList =
        blueprints.stream()
            .filter(blueprint -> serviceId.equals(blueprint.getServiceId()))
            .collect(Collectors.toList());
    if (blueprintList.isEmpty()) {
      log.info(StringUtils.join(DEBUG_LINE,
          format("No service mapping for serviceId %s found "
                  + "with provisioner %s linked to infraMapping",
              awsInfrastructureMapping.getServiceId(), awsInfrastructureMapping.getProvisionerId(),
              awsInfrastructureMapping.getUuid())));
    } else if (blueprintList.size() == 1) {
      InfrastructureMappingBlueprint blueprint = blueprintList.get(0);
      if (NodeFilteringType.AWS_AUTOSCALING_GROUP == blueprint.getNodeFilteringType()) {
        return true;
      } else if (NodeFilteringType.AWS_INSTANCE_FILTER == blueprint.getNodeFilteringType()) {
        return false;
      } else {
        log.error(StringUtils.join(DEBUG_LINE,
            "Unknown node filtering type for "
                + "inframapping ",
            awsInfrastructureMapping.getUuid(), " ", blueprint.getNodeFilteringType()));
      }
    } else {
      log.error(StringUtils.join(DEBUG_LINE,
          format("Provisioner %s has more than 1 service "
                  + "mappings "
                  + "for 1 service %s",
              awsInfrastructureMapping.getProvisionerId(), awsInfrastructureMapping.getServiceId())));
    }
    return awsInfrastructureMapping.isProvisionInstances();
  }

  private String getLaunchType(
      EcsInfrastructureMapping ecsInfrastructureMapping, List<InfrastructureMappingBlueprint> blueprints) {
    if (CollectionUtils.isEmpty(blueprints)) {
      return ecsInfrastructureMapping.getLaunchType();
    }
    final String serviceId = ecsInfrastructureMapping.getServiceId();
    final List<InfrastructureMappingBlueprint> blueprintList =
        blueprints.stream()
            .filter(blueprint -> serviceId.equals(blueprint.getServiceId()))
            .collect(Collectors.toList());
    if (blueprintList.isEmpty()) {
      log.info(StringUtils.join(DEBUG_LINE,
          format("No service mapping for serviceId %s found "
                  + "with provisioner %s linked to infraMapping",
              ecsInfrastructureMapping.getServiceId(), ecsInfrastructureMapping.getProvisionerId(),
              ecsInfrastructureMapping.getUuid())));
    } else if (blueprintList.size() == 1) {
      InfrastructureMappingBlueprint blueprint = blueprintList.get(0);
      if (NodeFilteringType.AWS_ECS_EC2 == blueprint.getNodeFilteringType()) {
        return LaunchType.EC2.toString();
      } else if (NodeFilteringType.AWS_ECS_FARGATE == blueprint.getNodeFilteringType()) {
        return LaunchType.FARGATE.toString();
      } else {
        log.error(StringUtils.join(DEBUG_LINE,
            "Unknown node filtering type for infra mapping "
                + "%s ",
            ecsInfrastructureMapping.getUuid(), " ", blueprint.getNodeFilteringType()));
      }
    } else {
      log.error(StringUtils.join(DEBUG_LINE,
          format("Provisioner %s has more than 1 service "
                  + "mappings "
                  + "for 1 service %s",
              ecsInfrastructureMapping.getProvisionerId(), ecsInfrastructureMapping.getServiceId())));
    }
    return ecsInfrastructureMapping.getLaunchType();
  }

  private Map<String, String> getExpressionsFromBluePrints(InfrastructureMapping infrastructureMapping,
      List<InfrastructureMappingBlueprint> blueprints, Map<String, String> fieldNameChanges) {
    if (CollectionUtils.isEmpty(blueprints)) {
      return Collections.EMPTY_MAP;
    }

    final List<List<BlueprintProperty>> bluePrintPropertiesList =
        blueprints.stream()
            .filter(blueprint -> isNotEmpty(blueprint.getProperties()))
            .filter(blueprint -> blueprint.getServiceId().equals(infrastructureMapping.getServiceId()))
            .filter(blueprint
                -> blueprint.infrastructureMappingType().name().equals(infrastructureMapping.getInfraMappingType()))
            .map(InfrastructureMappingBlueprint::getProperties)
            .collect(Collectors.toList());

    if (bluePrintPropertiesList.isEmpty()) {
      log.info(StringUtils.join(DEBUG_LINE,
          format("No service mapping for serviceId %s found "
                  + "with provisioner %s linked to infraMapping",
              infrastructureMapping.getServiceId(), infrastructureMapping.getProvisionerId(),
              infrastructureMapping.getUuid())));
    } else if (bluePrintPropertiesList.size() > 1) {
      log.error(StringUtils.join(DEBUG_LINE,
          format("Provisioner %s has more than 1 service "
                  + "mappings "
                  + "for 1 service %s",
              infrastructureMapping.getProvisionerId(), infrastructureMapping.getServiceId())));
    } else {
      List<BlueprintProperty> blueprintProperties = bluePrintPropertiesList.get(0);

      return blueprintProperties.stream()
          .map(blueprintProperty -> {
            List<NameValuePair> fields =
                isEmpty(blueprintProperty.getFields()) ? new ArrayList<>() : blueprintProperty.getFields();
            String name = fieldNameChanges.getOrDefault(blueprintProperty.getName(), blueprintProperty.getName());
            fields.add(NameValuePair.builder().name(name).value(blueprintProperty.getValue()).build());
            fields.forEach(nameValuePair
                -> nameValuePair.setName(
                    fieldNameChanges.getOrDefault(nameValuePair.getName(), nameValuePair.getName())));
            return fields;
          })
          .flatMap(Collection::stream)
          .filter(nameValuePair -> isNotEmpty(nameValuePair.getValue()) && isNotEmpty(nameValuePair.getName()))
          .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue, (value1, value2) -> {
            log.info(StringUtils.join(DEBUG_LINE,
                " Found duplicate value for keys in "
                    + "provisioner for infra mapping ",
                infrastructureMapping.getProvisionerId()));
            return value1;
          }));
    }
    return Collections.EMPTY_MAP;
  }

  private void setInfraDefinitionId(String infraDefinitionId, InfrastructureMapping infrastructureMapping) {
    Map<String, Object> toUpdate = new HashMap<>();
    toUpdate.put(InfrastructureMappingKeys.infrastructureDefinitionId, infraDefinitionId);
    try {
      wingsPersistence.updateFields(InfrastructureMapping.class, infrastructureMapping.getUuid(), toUpdate);
    } catch (Exception ex) {
      log.error(StringUtils.join(DEBUG_LINE,
          "Could not set infradefinition Id for infra "
              + "mapping ",
          infrastructureMapping.getUuid()));
    }
  }
}
