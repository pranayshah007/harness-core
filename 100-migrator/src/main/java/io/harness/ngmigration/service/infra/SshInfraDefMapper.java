/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.infra;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;

import io.harness.cdng.infra.beans.AwsInstanceFilter;
import io.harness.cdng.infra.beans.host.AllHostsFilter;
import io.harness.cdng.infra.beans.host.HostFilter;
import io.harness.cdng.infra.beans.host.HostNamesFilter;
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
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.AwsInstanceFilter.Tag;
import software.wings.beans.AzureTag;
import software.wings.beans.infrastructure.Host;
import software.wings.infra.AwsInstanceInfrastructure;
import software.wings.infra.AzureInstanceInfrastructure;
import software.wings.infra.InfraMappingInfrastructureProvider;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.PhysicalInfra;
import software.wings.ngmigration.CgEntityId;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class SshInfraDefMapper implements InfraDefMapper {
  private static final String PUBLIC_IP = "PublicIP";
  private static final String HOSTNAME = "Hostname";

  @Override
  public List<String> getConnectorIds(InfrastructureDefinition infrastructureDefinition) {
    switch (infrastructureDefinition.getCloudProviderType()) {
      case AWS:
        AwsInstanceInfrastructure awsInfra = (AwsInstanceInfrastructure) infrastructureDefinition.getInfrastructure();
        if (StringUtils.isNotBlank(awsInfra.getHostConnectionAttrs())) {
          return Collections.singletonList(awsInfra.getHostConnectionAttrs());
        }
        return Collections.emptyList();
      case AZURE:
        AzureInstanceInfrastructure azureInfra =
            (AzureInstanceInfrastructure) infrastructureDefinition.getInfrastructure();
        if (StringUtils.isNotBlank(azureInfra.getHostConnectionAttrs())) {
          return Collections.singletonList(azureInfra.getHostConnectionAttrs());
        }
        return Collections.emptyList();
      case PHYSICAL_DATA_CENTER:
        PhysicalInfra pdcInfra = (PhysicalInfra) infrastructureDefinition.getInfrastructure();
        if (StringUtils.isNotBlank(pdcInfra.getHostConnectionAttrs())) {
          return Collections.singletonList(pdcInfra.getHostConnectionAttrs());
        }
        return Collections.emptyList();
      default:
        throw new InvalidRequestException("Unsupported Infra for K8s deployment");
    }
  }

  @Override
  public ServiceDefinitionType getServiceDefinition(InfrastructureDefinition infrastructureDefinition) {
    return ServiceDefinitionType.SSH;
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
  public Infrastructure getSpec(
      InfrastructureDefinition infrastructureDefinition, Map<CgEntityId, NGYamlFile> migratedEntities) {
    switch (infrastructureDefinition.getCloudProviderType()) {
      case AWS:
        return getAwsSshInfra(migratedEntities, infrastructureDefinition.getInfrastructure());
      case AZURE:
        return getAzureSshInfra(migratedEntities, infrastructureDefinition.getInfrastructure());
      case PHYSICAL_DATA_CENTER:
        return getPdcSshInfra(infrastructureDefinition.getInfrastructure());
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
        .hostConnectionType(ParameterField.createValueField(azureInfra.isUsePublicDns() ? PUBLIC_IP : HOSTNAME))
        .build();
  }

  private Infrastructure getAwsSshInfra(
      Map<CgEntityId, NGYamlFile> migratedEntities, InfraMappingInfrastructureProvider infrastructure) {
    AwsInstanceInfrastructure awsInfra = (AwsInstanceInfrastructure) infrastructure;
    NgEntityDetail connectorDetail =
        migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(awsInfra.getCloudProviderId()).build())
            .getNgEntityDetail();
    SshWinRmAwsInfrastructureBuilder builder = SshWinRmAwsInfrastructure.builder();
    if (awsInfra.getAwsInstanceFilter() != null) {
      AwsInstanceFilter awsInstanceFilter =
          AwsInstanceFilter.builder()
              .vpcs(awsInfra.getAwsInstanceFilter().getVpcIds())
              .tags(ParameterField.createValueField(getAwsTagsMap(awsInfra.getAwsInstanceFilter().getTags())))
              .build();
      builder.awsInstanceFilter(awsInstanceFilter);
    }
    return builder
        .credentialsRef(ParameterField.createValueField(
            MigratorUtility.getSecretRef(migratedEntities, awsInfra.getHostConnectionAttrs(), CONNECTOR)
                .toSecretRefStringValue()))
        .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connectorDetail)))
        .region(ParameterField.createValueField(awsInfra.getRegion()))
        .hostConnectionType(ParameterField.createValueField(awsInfra.isUsePublicDns() ? PUBLIC_IP : HOSTNAME))
        .build();
  }

  private Infrastructure getPdcSshInfra(InfraMappingInfrastructureProvider infrastructure) {
    PhysicalInfra pdcInfra = (PhysicalInfra) infrastructure;
    PdcInfrastructureBuilder builder = PdcInfrastructure.builder();
    if (isNotEmpty(pdcInfra.getHosts())) {
      builder.hosts(ParameterField.createValueField(getPdcSShHosts(pdcInfra.getHosts())));
    } else {
      if (isNotEmpty(pdcInfra.getHostNames())) {
        builder.hostFilter(
            HostFilter.builder()
                .spec(HostNamesFilter.builder().value(ParameterField.createValueField(pdcInfra.getHostNames())).build())
                .type(HostFilterType.HOST_NAMES)
                .build());
      } else {
        builder.hostFilter(
            HostFilter.builder().spec(AllHostsFilter.builder().build()).type(HostFilterType.ALL).build());
      }
      // TODO: “Host Object Array Path”, “Host Attributes” are not yet supported in NG. It is in progress.
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

  private List<String> getPdcSShHosts(List<Host> hosts) {
    if (hosts == null) {
      return Collections.emptyList();
    }
    return hosts.stream().map(Host::getHostName).collect(Collectors.toList());
  }
}
