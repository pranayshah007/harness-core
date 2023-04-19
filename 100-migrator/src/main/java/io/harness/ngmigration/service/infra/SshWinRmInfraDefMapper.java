/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.infra;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngmigration.service.infra.InfraDefMapperUtils.getExpression;
import static io.harness.ngmigration.service.infra.InfraDefMapperUtils.getValueFromExpression;
import static io.harness.yaml.infra.HostConnectionTypeKind.HOSTNAME;
import static io.harness.yaml.infra.HostConnectionTypeKind.PRIVATE_IP;
import static io.harness.yaml.infra.HostConnectionTypeKind.PUBLIC_IP;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;

import io.harness.cdng.elastigroup.ElastigroupConfiguration;
import io.harness.cdng.infra.beans.AwsInstanceFilter;
import io.harness.cdng.infra.beans.host.AllHostsFilter;
import io.harness.cdng.infra.beans.host.HostFilter;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.infra.yaml.PdcInfrastructure.PdcInfrastructureBuilder;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure.SshWinRmAwsInfrastructureBuilder;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.api.DeploymentType;
import software.wings.beans.AwsInstanceFilter.AwsInstanceFilterKeys;
import software.wings.beans.AwsInstanceFilter.Tag;
import software.wings.beans.AzureTag;
import software.wings.infra.AwsInstanceInfrastructure;
import software.wings.infra.AwsInstanceInfrastructure.AwsInstanceInfrastructureKeys;
import software.wings.infra.AzureInstanceInfrastructure;
import software.wings.infra.InfraMappingInfrastructureProvider;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.PhysicalInfra;
import software.wings.infra.PhysicalInfraWinrm;
import software.wings.ngmigration.CgEntityId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang3.StringUtils;

public class SshWinRmInfraDefMapper implements InfraDefMapper {
  @Override
  public List<String> getConnectorIds(InfrastructureDefinition infrastructureDefinition) {
    List<String> connectorIds = new ArrayList<>();
    switch (infrastructureDefinition.getCloudProviderType()) {
      case AWS:
        AwsInstanceInfrastructure awsInfra = (AwsInstanceInfrastructure) infrastructureDefinition.getInfrastructure();
        connectorIds.add(awsInfra.getCloudProviderId());
        if (StringUtils.isNotBlank(awsInfra.getHostConnectionAttrs())) {
          connectorIds.add(awsInfra.getHostConnectionAttrs());
        }
        break;
      case AZURE:
        AzureInstanceInfrastructure azureInfra =
            (AzureInstanceInfrastructure) infrastructureDefinition.getInfrastructure();
        connectorIds.add(azureInfra.getCloudProviderId());
        if (StringUtils.isNotBlank(azureInfra.getHostConnectionAttrs())) {
          connectorIds.add(azureInfra.getHostConnectionAttrs());
        }
        break;
      case PHYSICAL_DATA_CENTER:
        if (DeploymentType.WINRM.equals(infrastructureDefinition.getDeploymentType())) {
          PhysicalInfraWinrm pdcInfra = (PhysicalInfraWinrm) infrastructureDefinition.getInfrastructure();
          if (StringUtils.isNotBlank(pdcInfra.getWinRmConnectionAttributes())) {
            connectorIds.add(pdcInfra.getWinRmConnectionAttributes());
          }
        } else {
          PhysicalInfra pdcInfra = (PhysicalInfra) infrastructureDefinition.getInfrastructure();
          if (StringUtils.isNotBlank(pdcInfra.getHostConnectionAttrs())) {
            connectorIds.add(pdcInfra.getHostConnectionAttrs());
          }
        }
        break;
      default:
        throw new InvalidRequestException("Unsupported Infra for K8s deployment");
    }
    return connectorIds;
  }

  @Override
  public ServiceDefinitionType getServiceDefinition(InfrastructureDefinition infrastructureDefinition) {
    return infrastructureDefinition.getDeploymentType() == DeploymentType.SSH ? ServiceDefinitionType.SSH
                                                                              : ServiceDefinitionType.WINRM;
  }

  @Override
  public InfrastructureType getInfrastructureType(InfrastructureDefinition infrastructureDefinition) {
    switch (infrastructureDefinition.getCloudProviderType()) {
      case AWS:
        return InfrastructureType.SSH_WINRM_AWS;
      case AZURE:
        return InfrastructureType.SSH_WINRM_AZURE;
      case PHYSICAL_DATA_CENTER:
        return InfrastructureType.PDC;
      default:
        throw new InvalidRequestException("Unsupported Infra for K8s deployment");
    }
  }

  @Override
  public Infrastructure getSpec(MigrationContext migrationContext, InfrastructureDefinition infrastructureDefinition,
      List<ElastigroupConfiguration> elastigroupConfiguration) {
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    switch (infrastructureDefinition.getCloudProviderType()) {
      case AWS:
        return getAwsSshInfra(migratedEntities, infrastructureDefinition.getInfrastructure(),
            infrastructureDefinition.getProvisionerId());
      case AZURE:
        return getAzureSshInfra(migratedEntities, infrastructureDefinition.getInfrastructure());
      case PHYSICAL_DATA_CENTER:
        return getPdcSshInfra(migratedEntities, infrastructureDefinition.getInfrastructure(),
            infrastructureDefinition.getDeploymentType(), infrastructureDefinition.getProvisionerId());
      default:
        throw new InvalidRequestException("Unsupported Infra for ssh deployment");
    }
  }

  private Infrastructure getAzureSshInfra(
      Map<CgEntityId, NGYamlFile> migratedEntities, InfraMappingInfrastructureProvider infrastructure) {
    AzureInstanceInfrastructure azureInfra = (AzureInstanceInfrastructure) infrastructure;
    NgEntityDetail connectorDetail =
        migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(azureInfra.getCloudProviderId()).build())
            .getNgEntityDetail();
    return SshWinRmAzureInfrastructure.builder()
        .credentialsRef(ParameterField.createValueField(
            MigratorUtility.getSecretRef(migratedEntities, azureInfra.getHostConnectionAttrs(), CONNECTOR)
                .toSecretRefStringValue()))
        .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connectorDetail)))
        .subscriptionId(ParameterField.createValueField(azureInfra.getSubscriptionId()))
        .resourceGroup(ParameterField.createValueField(azureInfra.getResourceGroup()))
        .tags(ParameterField.createValueField(getAzureTagsMap(azureInfra.getTags())))
        .hostConnectionType(ParameterField.createValueField(
            azureInfra.isUsePublicDns() ? PUBLIC_IP : (azureInfra.isUsePrivateIp() ? PRIVATE_IP : HOSTNAME)))
        .build();
  }

  private Infrastructure getAwsSshInfra(Map<CgEntityId, NGYamlFile> migratedEntities,
      InfraMappingInfrastructureProvider infrastructure, String provisionerId) {
    AwsInstanceInfrastructure awsInfra = (AwsInstanceInfrastructure) infrastructure;
    NgEntityDetail connectorDetail =
        migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(awsInfra.getCloudProviderId()).build())
            .getNgEntityDetail();
    SshWinRmAwsInfrastructureBuilder builder = SshWinRmAwsInfrastructure.builder();

    AwsInstanceFilter awsInstanceFilter = AwsInstanceFilter.builder().build();

    if (awsInfra.getAwsInstanceFilter() != null) {
      awsInstanceFilter.setTags(getTags(awsInfra, provisionerId));
    }

    builder.awsInstanceFilter(awsInstanceFilter);
    return builder
        .credentialsRef(ParameterField.createValueField(
            MigratorUtility.getSecretRef(migratedEntities, awsInfra.getHostConnectionAttrs(), CONNECTOR)
                .toSecretRefStringValue()))
        .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connectorDetail)))
        .region(getExpression(
            awsInfra.getExpressions(), AwsInstanceInfrastructureKeys.region, awsInfra.getRegion(), provisionerId))
        .hostConnectionType(ParameterField.createValueField(awsInfra.isUsePublicDns() ? PUBLIC_IP : PRIVATE_IP))
        .build();
  }

  private ParameterField<Map<String, String>> getTags(AwsInstanceInfrastructure awsInfra, String provisionerId) {
    if (provisionerId != null && isNotEmpty(awsInfra.getExpressions())
        && awsInfra.getExpressions().containsKey(AwsInstanceFilterKeys.tags)) {
      return ParameterField.createExpressionField(
          true, awsInfra.getExpressions().get(AwsInstanceFilterKeys.tags), null, false);
    } else {
      return ParameterField.createValueField(getAwsTagsMap(awsInfra.getAwsInstanceFilter().getTags()));
    }
  }

  private Infrastructure getPdcSshInfra(Map<CgEntityId, NGYamlFile> migratedEntities,
      InfraMappingInfrastructureProvider infrastructure, @NotNull DeploymentType deploymentType, String provisionerId) {
    PdcInfrastructureBuilder builder = PdcInfrastructure.builder();

    if (deploymentType.equals(DeploymentType.WINRM)) {
      PhysicalInfraWinrm pdcInfra = (PhysicalInfraWinrm) infrastructure;
      builder.credentialsRef(ParameterField.createValueField(
          MigratorUtility.getSecretRef(migratedEntities, pdcInfra.getWinRmConnectionAttributes(), CONNECTOR)
              .toSecretRefStringValue()));
      if (isNotEmpty(pdcInfra.getHostNames())) {
        builder.hosts(ParameterField.createValueField(pdcInfra.getHostNames()));
      }
    } else {
      PhysicalInfra pdcInfra = (PhysicalInfra) infrastructure;
      builder.credentialsRef(ParameterField.createValueField(
          MigratorUtility.getSecretRef(migratedEntities, pdcInfra.getHostConnectionAttrs(), CONNECTOR)
              .toSecretRefStringValue()));
      builder.hostFilter(HostFilter.builder().type(HostFilterType.ALL).spec(AllHostsFilter.builder().build()).build());

      if (isEmpty(provisionerId)) {
        if (isNotEmpty(pdcInfra.getHostNames())) {
          builder.hosts(ParameterField.createValueField(pdcInfra.getHostNames()));
        }
      } else {
        builder.hostArrayPath(
            getExpression(pdcInfra.getExpressions(), PhysicalInfra.hostArrayPath, null, provisionerId));
        builder.provisioner(ParameterField.createValueField(provisionerId));
        Map<String, String> hostAttrs = new HashMap<>();
        hostAttrs.put(PhysicalInfra.hostname,
            getValueFromExpression(pdcInfra.getExpressions(), PhysicalInfra.hostname, null, provisionerId));

        Map<String, String> expressions = pdcInfra.getExpressions();
        if (isNotEmpty(expressions)) {
          expressions.forEach((k, v) -> {
            if (!PhysicalInfra.hostname.equals(k) && !PhysicalInfra.hostArrayPath.equals(k)) {
              hostAttrs.put(k, v);
            }
          });
        }
        builder.hostAttributes(ParameterField.createValueField(hostAttrs));
      }
    }
    return builder.build();
  }

  private Map<String, String> getAwsTagsMap(List<Tag> tags) {
    if (tags == null) {
      return Collections.emptyMap();
    }
    return tags.stream().collect(Collectors.toMap(Tag::getKey, Tag::getValue));
  }

  private Map<String, String> getAzureTagsMap(List<AzureTag> tags) {
    if (tags == null) {
      return Collections.emptyMap();
    }
    return tags.stream().collect(Collectors.toMap(AzureTag::getKey, AzureTag::getValue));
  }
}
