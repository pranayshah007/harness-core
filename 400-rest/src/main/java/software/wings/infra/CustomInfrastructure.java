/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.infra;

import static io.harness.expression.common.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.expression.common.Expression;

import software.wings.annotation.IncludeFieldMap;
import software.wings.api.CloudProviderType;
import software.wings.beans.CustomInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.InfrastructureType;
import software.wings.beans.NameValuePair;
import software.wings.service.impl.yaml.handler.InfraDefinition.CloudProviderInfrastructureYaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@JsonTypeName(InfrastructureType.CUSTOM_INFRASTRUCTURE)
@OwnedBy(HarnessTeam.CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class CustomInfrastructure implements InfraMappingInfrastructureProvider, FieldKeyValMapProvider {
  public static final String DUMMY_CLOUD_PROVIDER = "DUMMY_CLOUD_PROVIDER";

  @IncludeFieldMap @Expression(ALLOW_SECRETS) private List<NameValuePair> infraVariables;
  @IncludeFieldMap private String deploymentTypeTemplateVersion;

  private transient String customDeploymentName;

  @Override
  public InfrastructureMapping getInfraMapping() {
    final CustomInfrastructureMapping infraMapping =
        CustomInfrastructureMapping.builder().infraVariables(infraVariables).build();
    infraMapping.setInfraMappingType(InfrastructureMappingType.CUSTOM.name());
    infraMapping.setComputeProviderSettingId(DUMMY_CLOUD_PROVIDER);
    infraMapping.setDeploymentTypeTemplateVersion(deploymentTypeTemplateVersion);
    return infraMapping;
  }

  @Override
  public Class<? extends InfrastructureMapping> getMappingClass() {
    return CustomInfrastructureMapping.class;
  }

  @Override
  public String getCloudProviderId() {
    return DUMMY_CLOUD_PROVIDER;
  }

  @Override
  public CloudProviderType getCloudProviderType() {
    return CloudProviderType.CUSTOM;
  }

  @Override
  public String getInfrastructureType() {
    return InfrastructureMappingType.CUSTOM.name();
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName(InfrastructureType.CUSTOM_INFRASTRUCTURE)
  public static final class Yaml extends CloudProviderInfrastructureYaml {
    private List<NameValuePair> infraVariables;
    private String deploymentTypeTemplateVersion;

    @Builder
    public Yaml(String type, List<NameValuePair> infraVariables, String deploymentTypeTemplateVersion) {
      super(type);
      setInfraVariables(infraVariables);
      setDeploymentTypeTemplateVersion(deploymentTypeTemplateVersion);
    }

    public Yaml() {
      super(InfrastructureType.CUSTOM_INFRASTRUCTURE);
    }
  }
}
