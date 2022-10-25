/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.infra;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.common.Expression.DISALLOW_SECRETS;

import static software.wings.beans.InfrastructureType.PCF_INFRASTRUCTURE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.expression.common.Expression;

import software.wings.annotation.IncludeFieldMap;
import software.wings.api.CloudProviderType;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.PcfInfrastructureMapping;
import software.wings.service.impl.yaml.handler.InfraDefinition.CloudProviderInfrastructureYaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonTypeName("PCF_PCF")
@Data
@Builder
@OwnedBy(CDP)
@TargetModule(HarnessModule._957_CG_BEANS)
public class PcfInfraStructure implements InfraMappingInfrastructureProvider, FieldKeyValMapProvider {
  private String cloudProviderId;
  @Expression(DISALLOW_SECRETS) @IncludeFieldMap private String organization;
  @Expression(DISALLOW_SECRETS) @IncludeFieldMap private String space;
  private List<String> tempRouteMap;
  private List<String> routeMaps;

  @Override
  public InfrastructureMapping getInfraMapping() {
    return PcfInfrastructureMapping.builder()
        .computeProviderSettingId(cloudProviderId)
        .organization(organization)
        .space(space)
        .tempRouteMap(tempRouteMap)
        .routeMaps(routeMaps)
        .infraMappingType(InfrastructureMappingType.PCF_PCF.name())
        .build();
  }

  @Override
  public Class<PcfInfrastructureMapping> getMappingClass() {
    return PcfInfrastructureMapping.class;
  }

  @Override
  public CloudProviderType getCloudProviderType() {
    return CloudProviderType.PCF;
  }

  @Override
  public String getInfrastructureType() {
    return PCF_INFRASTRUCTURE;
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName(PCF_INFRASTRUCTURE)
  public static final class Yaml extends CloudProviderInfrastructureYaml {
    private String cloudProviderName;
    private String organization;
    private String space;
    private List<String> tempRouteMap;
    private List<String> routeMaps;

    @Builder
    public Yaml(String type, String cloudProviderName, String organization, String space, List<String> tempRouteMap,
        List<String> routeMaps) {
      super(type);
      setCloudProviderName(cloudProviderName);
      setOrganization(organization);
      setSpace(space);
      setTempRouteMap(tempRouteMap);
      setRouteMaps(routeMaps);
    }

    public Yaml() {
      super(PCF_INFRASTRUCTURE);
    }
  }
}
