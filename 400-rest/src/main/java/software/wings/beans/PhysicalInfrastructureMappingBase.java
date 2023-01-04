/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.infrastructure.Host;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.base.MoreObjects;
import dev.morphia.annotations.Transient;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "PhysicalInfrastructureMappingBaseKeys")
@OwnedBy(CDP)
@TargetModule(_957_CG_BEANS)
public abstract class PhysicalInfrastructureMappingBase extends InfrastructureMapping {
  @Attributes(title = "Host Names", required = true) private List<String> hostNames;
  private List<Host> hosts;
  @Attributes(title = "Load Balancer") private String loadBalancerId;
  @Transient @SchemaIgnore private String loadBalancerName;

  public PhysicalInfrastructureMappingBase(InfrastructureMappingType infrastructureMappingType) {
    super(infrastructureMappingType.name());
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static class Yaml extends YamlWithComputeProvider {
    private List<String> hostNames;
    private List<Host> hosts;
    private String loadBalancer;

    public Yaml(String type, String harnessApiVersion, String computeProviderType, String serviceName,
        String infraMappingType, String deploymentType, String computeProviderName, String name, List<String> hostNames,
        String loadBalancer, List<Host> hosts, Map<String, Object> blueprints) {
      super(type, harnessApiVersion, computeProviderType, serviceName, infraMappingType, deploymentType,
          computeProviderName, blueprints);
      this.hostNames = hostNames;
      this.loadBalancer = loadBalancer;
      this.hosts = hosts;
    }
  }

  public List<String> getHostNames() {
    return hostNames;
  }

  public void setHostNames(List<String> hostNames) {
    this.hostNames = hostNames;
  }

  @Override
  public String getHostConnectionAttrs() {
    return null;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    if (!super.equals(obj)) {
      return false;
    }
    final PhysicalInfrastructureMappingBase other = (PhysicalInfrastructureMappingBase) obj;
    return Objects.equals(this.hostNames, other.hostNames);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("hostNames", hostNames).toString();
  }

  public String getLoadBalancerId() {
    return loadBalancerId;
  }

  public void setLoadBalancerId(String loadBalancerId) {
    this.loadBalancerId = loadBalancerId;
  }

  public String getLoadBalancerName() {
    return loadBalancerName;
  }

  public void setLoadBalancerName(String loadBalancerName) {
    this.loadBalancerName = loadBalancerName;
  }

  public List<Host> hosts() {
    return hosts;
  }

  public void hosts(List<Host> hosts) {
    this.hosts = hosts;
  }
}
