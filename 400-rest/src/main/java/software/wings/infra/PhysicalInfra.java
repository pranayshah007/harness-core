/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.infra;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.beans.InfrastructureType.PHYSICAL_INFRA;
import static software.wings.beans.PhysicalInfrastructureMapping.Builder.aPhysicalInfrastructureMapping;

import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.api.CloudProviderType;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.PhysicalInfrastructureMapping;
import software.wings.beans.infrastructure.Host;
import software.wings.service.impl.yaml.handler.InfraDefinition.CloudProviderInfrastructureYaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import dev.morphia.annotations.Transient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonTypeName("PHYSICAL_DATA_CENTER_SSH")
@Data
@Builder
public class PhysicalInfra implements PhysicalDataCenterInfra, InfraMappingInfrastructureProvider,
                                      FieldKeyValMapProvider, SshBasedInfrastructure, ProvisionerAware {
  public static final String hostArrayPath = "hostArrayPath";
  public static final String hostname = "hostname";

  private String cloudProviderId;
  private List<String> hostNames;
  private List<Host> hosts;
  private String loadBalancerId;
  @Transient private String loadBalancerName;
  private String hostConnectionAttrs;
  private Map<String, String> expressions;

  @Override
  public InfrastructureMapping getInfraMapping() {
    return aPhysicalInfrastructureMapping()
        .withComputeProviderSettingId(cloudProviderId)
        .withHosts(hosts)
        .withHostNames(hostNames)
        .withLoadBalancerId(loadBalancerId)
        .withLoadBalancerName(loadBalancerName)
        .withHostConnectionAttrs(hostConnectionAttrs)
        .withInfraMappingType(InfrastructureMappingType.PHYSICAL_DATA_CENTER_SSH.name())
        .build();
  }

  @Override
  public Class<PhysicalInfrastructureMapping> getMappingClass() {
    return PhysicalInfrastructureMapping.class;
  }

  @Override
  public String getInfrastructureType() {
    return PHYSICAL_INFRA;
  }

  @Override
  public CloudProviderType getCloudProviderType() {
    return CloudProviderType.PHYSICAL_DATA_CENTER;
  }

  @Override
  public Set<String> getSupportedExpressions() {
    // Can contain custom fields
    return null;
  }

  @Override
  public void applyExpressions(
      Map<String, Object> resolvedExpressions, String appId, String envId, String infraDefinitionId) {
    if (isEmpty(resolvedExpressions)) {
      throw new InvalidRequestException("Infra Provisioner Mapping inputs can't be empty");
    }
    List<Map<String, Object>> hostList = (List<Map<String, Object>>) resolvedExpressions.get("hostArrayPath");
    if (hostList == null) {
      throw new InvalidRequestException("Host array path not found");
    }
    List<Host> hosts = new ArrayList<>();

    for (Map<String, Object> hostAttributes : hostList) {
      Host host = Host.Builder.aHost()
                      .withAppId(appId)
                      .withEnvId(envId)
                      .withHostConnAttr(getHostConnectionAttrs())
                      .withInfraDefinitionId(infraDefinitionId)
                      .withProperties(new HashMap<>())
                      .build();

      for (Entry<String, Object> entry : hostAttributes.entrySet()) {
        switch (entry.getKey()) {
          case "hostname":
            host.setHostName((String) hostAttributes.get(entry.getKey()));
            host.setPublicDns((String) hostAttributes.get(entry.getKey()));
            break;
          default:
            host.getProperties().put(entry.getKey(), hostAttributes.get(entry.getKey()));
        }
      }
      if (isEmpty(host.getPublicDns())) {
        throw new InvalidRequestException("Hostname can't be empty", WingsException.USER);
      }
      hosts.add(host);
    }
    if (isEmpty(hosts)) {
      throw new InvalidRequestException("Host list can't be empty", WingsException.USER);
    }
    setHosts(hosts);
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName(PHYSICAL_INFRA)
  public static final class Yaml extends CloudProviderInfrastructureYaml {
    private String cloudProviderName;
    private List<String> hostNames;
    private List<Host> hosts;
    private String loadBalancerName;
    private String hostConnectionAttrsName;
    private Map<String, String> expressions;

    @Builder
    public Yaml(String type, String cloudProviderName, List<String> hostNames, List<Host> hosts,
        String loadBalancerName, String hostConnectionAttrsName, Map<String, String> expressions) {
      super(type);
      setCloudProviderName(cloudProviderName);
      setHostNames(hostNames);
      setHosts(hosts);
      setLoadBalancerName(loadBalancerName);
      setHostConnectionAttrsName(hostConnectionAttrsName);
      setExpressions(expressions);
    }

    public Yaml() {
      super(PHYSICAL_INFRA);
    }
  }
}
