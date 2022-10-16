/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.infra;

import static software.wings.beans.AzureInfrastructureMapping.Builder.anAzureInfrastructureMapping;
import static software.wings.beans.InfrastructureType.AZURE_SSH;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.annotation.IncludeFieldMap;
import software.wings.api.CloudProviderType;
import software.wings.beans.AzureInfrastructureMapping;
import software.wings.beans.AzureTag;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.service.impl.yaml.handler.InfraDefinition.CloudProviderInfrastructureYaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonTypeName("AZURE_INFRA")
@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class AzureInstanceInfrastructure implements InfraMappingInfrastructureProvider, FieldKeyValMapProvider,
                                                    SshBasedInfrastructure, WinRmBasedInfrastructure {
  private String cloudProviderId;
  @IncludeFieldMap private String subscriptionId;
  @IncludeFieldMap private String resourceGroup;
  @Builder.Default private List<AzureTag> tags = new ArrayList<>();
  private String hostConnectionAttrs;
  private String winRmConnectionAttributes;
  private boolean usePublicDns;
  private boolean usePrivateIp;

  @Override
  public InfrastructureMapping getInfraMapping() {
    return anAzureInfrastructureMapping()
        .withComputeProviderSettingId(cloudProviderId)
        .withSubscriptionId(subscriptionId)
        .withResourceGroup(resourceGroup)
        .withTags(tags)
        .withHostConnectionAttributes(hostConnectionAttrs)
        .withWinRmConnectionAttributes(winRmConnectionAttributes)
        .withUsePublicDns(usePublicDns)
        .withUsePrivateIp(usePrivateIp)
        .withResourceGroup(resourceGroup)
        .withInfraMappingType(InfrastructureMappingType.AZURE_INFRA.name())
        .build();
  }

  @Override
  public Class<AzureInfrastructureMapping> getMappingClass() {
    return AzureInfrastructureMapping.class;
  }

  @Override
  public CloudProviderType getCloudProviderType() {
    return CloudProviderType.AZURE;
  }

  @Override
  public String getInfrastructureType() {
    return AZURE_SSH;
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName(AZURE_SSH)
  public static final class Yaml extends CloudProviderInfrastructureYaml {
    private String cloudProviderName;
    private String resourceGroup;
    private String subscriptionId;
    private List<AzureTag> tags;
    private String hostConnectionAttrsName;
    private String winRmConnectionAttributesName;

    @Builder
    public Yaml(String type, String cloudProviderName, String resourceGroup, String subscriptionId, List<AzureTag> tags,
        String hostConnectionAttrsName, String winRmConnectionAttributesName) {
      super(type);
      setCloudProviderName(cloudProviderName);
      setResourceGroup(resourceGroup);
      setSubscriptionId(subscriptionId);
      setTags(tags);
      setHostConnectionAttrsName(hostConnectionAttrsName);
      setWinRmConnectionAttributesName(winRmConnectionAttributesName);
    }

    public Yaml() {
      super(AZURE_SSH);
    }
  }
}
