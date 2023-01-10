/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.infra;

import static software.wings.beans.InfrastructureType.PHYSICAL_INFRA_WINRM;
import static software.wings.beans.PhysicalInfrastructureMappingWinRm.Builder.aPhysicalInfrastructureMappingWinRm;

import software.wings.api.CloudProviderType;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.PhysicalInfrastructureMappingWinRm;
import software.wings.beans.infrastructure.Host;
import software.wings.service.impl.yaml.handler.InfraDefinition.CloudProviderInfrastructureYaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import dev.morphia.annotations.Transient;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonTypeName("PHYSICAL_DATA_CENTER_WINRM")
@Data
@Builder
public class PhysicalInfraWinrm implements PhysicalDataCenterInfra, InfraMappingInfrastructureProvider,
                                           FieldKeyValMapProvider, WinRmBasedInfrastructure {
  private String cloudProviderId;
  private List<String> hostNames;
  private List<Host> hosts;
  private String loadBalancerId;
  @Transient private String loadBalancerName;
  private String winRmConnectionAttributes;

  @Override
  public InfrastructureMapping getInfraMapping() {
    return aPhysicalInfrastructureMappingWinRm()
        .withComputeProviderSettingId(cloudProviderId)
        .withHostNames(hostNames)
        .withLoadBalancerId(loadBalancerId)
        .withLoadBalancerName(loadBalancerName)
        .withWinRmConnectionAttributes(winRmConnectionAttributes)
        .withInfraMappingType(InfrastructureMappingType.PHYSICAL_DATA_CENTER_WINRM.name())
        .build();
  }

  @Override
  public Class<PhysicalInfrastructureMappingWinRm> getMappingClass() {
    return PhysicalInfrastructureMappingWinRm.class;
  }

  @Override
  public String getInfrastructureType() {
    return PHYSICAL_INFRA_WINRM;
  }

  @Override
  public CloudProviderType getCloudProviderType() {
    return CloudProviderType.PHYSICAL_DATA_CENTER;
  }

  public String getWinRmConnectionAttributes() {
    return winRmConnectionAttributes;
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName(PHYSICAL_INFRA_WINRM)
  public static final class Yaml extends CloudProviderInfrastructureYaml {
    private String cloudProviderName;
    private List<String> hostNames;
    private List<Host> hosts;
    private String loadBalancerName;
    private String winRmConnectionAttributesName;

    @Builder
    public Yaml(String type, String cloudProviderName, List<String> hostNames, List<Host> hosts,
        String loadBalancerName, String winRmConnectionAttributesName) {
      super(type);
      setCloudProviderName(cloudProviderName);
      setHostNames(hostNames);
      setHosts(hosts);
      setLoadBalancerName(loadBalancerName);
      setWinRmConnectionAttributesName(winRmConnectionAttributesName);
    }

    public Yaml() {
      super(PHYSICAL_INFRA_WINRM);
    }
  }
}
