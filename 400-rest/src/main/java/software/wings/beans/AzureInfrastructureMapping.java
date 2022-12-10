/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;

import software.wings.beans.InfrastructureMappingBlueprint.NodeFilteringType;
import software.wings.utils.Utils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@JsonTypeName("AZURE_INFRA")
@FieldNameConstants(innerTypeName = "AzureInfrastructureMappingKeys")
@OwnedBy(HarnessTeam.CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class AzureInfrastructureMapping extends InfrastructureMapping {
  @Attributes(title = "Subscription Id") private String subscriptionId;
  @Attributes(title = "Resource Group") private String resourceGroup;
  @Attributes(title = "Tags") private List<AzureTag> tags = new ArrayList<>();
  private String hostConnectionAttrs;
  private String winRmConnectionAttributes;
  private boolean usePublicDns;
  private boolean usePrivateIp;

  public String getWinRmConnectionAttributes() {
    return winRmConnectionAttributes;
  }
  public void setWinRmConnectionAttributes(String winRmConnectionAttributes) {
    this.winRmConnectionAttributes = winRmConnectionAttributes;
  }

  public AzureInfrastructureMapping() {
    super(InfrastructureMappingType.AZURE_INFRA.name());
  }
  @Override
  public String getHostConnectionAttrs() {
    return hostConnectionAttrs;
  }
  public void setHostConnectionAttrs(String hostConnectionAttrs) {
    this.hostConnectionAttrs = hostConnectionAttrs;
  }
  public String getSubscriptionId() {
    return subscriptionId;
  }
  public void setSubscriptionId(String subscriptionId) {
    this.subscriptionId = subscriptionId;
  }
  public String getResourceGroup() {
    return resourceGroup;
  }
  public void setResourceGroup(String resourceGroup) {
    this.resourceGroup = resourceGroup;
  }
  public List<AzureTag> getTags() {
    return tags;
  }
  public void setTags(List<AzureTag> tags) {
    this.tags = tags;
  }
  public boolean isUsePublicDns() {
    return this.usePublicDns;
  }
  public void setUsePublicDns(boolean usePublicDns) {
    this.usePublicDns = usePublicDns;
  }

  public boolean isUsePrivateIp() {
    return this.usePrivateIp;
  }
  public void setUsePrivateIp(boolean usePrivateIp) {
    this.usePrivateIp = usePrivateIp;
  }

  @SchemaIgnore
  @Override
  public String getDefaultName() {
    List<String> parts = new ArrayList();
    if (isNotEmpty(getComputeProviderName())) {
      parts.add(getComputeProviderName().toLowerCase());
    }

    parts.add("AZURE");

    return Utils.normalize(String.join(" - ", parts));
  }
  @Override
  public void applyProvisionerVariables(
      Map<String, Object> map, NodeFilteringType nodeFilteringType, boolean featureFlagEnabled) {
    throw new UnsupportedOperationException();
  }

  public static final class Builder {
    public transient String entityYamlPath;
    protected String appId;
    private String accountId;
    private String subscriptionId;
    private String resourceGroup;
    private String uuid;
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;
    private String computeProviderSettingId;
    private String envId;
    private String serviceTemplateId;
    private String serviceId;
    private String computeProviderType;
    private String infraMappingType;
    private String deploymentType;
    private String computeProviderName;
    private String name;
    private boolean autoPopulate = true;
    private List<AzureTag> tags;
    private String hostConnectionAttrs;
    private String winRmConnectionAttributes;
    private boolean usePublicDns;
    private boolean usePrivateIp;

    private Builder() {}

    public static AzureInfrastructureMapping.Builder anAzureInfrastructureMapping() {
      return new AzureInfrastructureMapping.Builder();
    }

    public AzureInfrastructureMapping.Builder withSubscriptionId(String subscriptionId) {
      this.subscriptionId = subscriptionId;
      return this;
    }

    public AzureInfrastructureMapping.Builder withResourceGroup(String resourceGroup) {
      this.resourceGroup = resourceGroup;
      return this;
    }

    public AzureInfrastructureMapping.Builder withUuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    public AzureInfrastructureMapping.Builder withAppId(String appId) {
      this.appId = appId;
      return this;
    }

    public AzureInfrastructureMapping.Builder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    public AzureInfrastructureMapping.Builder withCreatedBy(EmbeddedUser createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public AzureInfrastructureMapping.Builder withCreatedAt(long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public AzureInfrastructureMapping.Builder withLastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    public AzureInfrastructureMapping.Builder withLastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    public AzureInfrastructureMapping.Builder withEntityYamlPath(String entityYamlPath) {
      this.entityYamlPath = entityYamlPath;
      return this;
    }

    public AzureInfrastructureMapping.Builder withComputeProviderSettingId(String computeProviderSettingId) {
      this.computeProviderSettingId = computeProviderSettingId;
      return this;
    }

    public AzureInfrastructureMapping.Builder withEnvId(String envId) {
      this.envId = envId;
      return this;
    }

    public AzureInfrastructureMapping.Builder withServiceTemplateId(String serviceTemplateId) {
      this.serviceTemplateId = serviceTemplateId;
      return this;
    }

    public AzureInfrastructureMapping.Builder withServiceId(String serviceId) {
      this.serviceId = serviceId;
      return this;
    }

    public AzureInfrastructureMapping.Builder withComputeProviderType(String computeProviderType) {
      this.computeProviderType = computeProviderType;
      return this;
    }

    public AzureInfrastructureMapping.Builder withInfraMappingType(String infraMappingType) {
      this.infraMappingType = infraMappingType;
      return this;
    }

    public AzureInfrastructureMapping.Builder withDeploymentType(String deploymentType) {
      this.deploymentType = deploymentType;
      return this;
    }

    public AzureInfrastructureMapping.Builder withComputeProviderName(String computeProviderName) {
      this.computeProviderName = computeProviderName;
      return this;
    }

    public AzureInfrastructureMapping.Builder withName(String name) {
      this.name = name;
      return this;
    }

    public AzureInfrastructureMapping.Builder withAutoPopulate(boolean autoPopulate) {
      this.autoPopulate = autoPopulate;
      return this;
    }

    public AzureInfrastructureMapping.Builder withTags(List<AzureTag> tags) {
      this.tags = tags;
      return this;
    }

    public AzureInfrastructureMapping.Builder withWinRmConnectionAttributes(String winRmConnectionAttributes) {
      this.winRmConnectionAttributes = winRmConnectionAttributes;
      return this;
    }

    public AzureInfrastructureMapping.Builder withHostConnectionAttributes(String hostConnectionAttrs) {
      this.hostConnectionAttrs = hostConnectionAttrs;
      return this;
    }

    public AzureInfrastructureMapping.Builder withUsePublicDns(boolean usePublicDns) {
      this.usePublicDns = usePublicDns;
      return this;
    }

    public AzureInfrastructureMapping.Builder withUsePrivateIp(boolean usePrivateIp) {
      this.usePrivateIp = usePrivateIp;
      return this;
    }

    public AzureInfrastructureMapping build() {
      AzureInfrastructureMapping azureInfrastructureMapping = new AzureInfrastructureMapping();
      azureInfrastructureMapping.setSubscriptionId(subscriptionId);
      azureInfrastructureMapping.setResourceGroup(resourceGroup);
      azureInfrastructureMapping.setUuid(uuid);
      azureInfrastructureMapping.setAppId(appId);
      azureInfrastructureMapping.setCreatedBy(createdBy);
      azureInfrastructureMapping.setCreatedAt(createdAt);
      azureInfrastructureMapping.setLastUpdatedBy(lastUpdatedBy);
      azureInfrastructureMapping.setLastUpdatedAt(lastUpdatedAt);
      azureInfrastructureMapping.setEntityYamlPath(entityYamlPath);
      azureInfrastructureMapping.setComputeProviderSettingId(computeProviderSettingId);
      azureInfrastructureMapping.setEnvId(envId);
      azureInfrastructureMapping.setServiceTemplateId(serviceTemplateId);
      azureInfrastructureMapping.setServiceId(serviceId);
      azureInfrastructureMapping.setComputeProviderType(computeProviderType);
      azureInfrastructureMapping.setInfraMappingType(infraMappingType);
      azureInfrastructureMapping.setDeploymentType(deploymentType);
      azureInfrastructureMapping.setComputeProviderName(computeProviderName);
      azureInfrastructureMapping.setName(name);
      azureInfrastructureMapping.setAutoPopulate(autoPopulate);
      azureInfrastructureMapping.setAccountId(accountId);
      azureInfrastructureMapping.setTags(tags);
      azureInfrastructureMapping.setHostConnectionAttrs(hostConnectionAttrs);
      azureInfrastructureMapping.setWinRmConnectionAttributes(winRmConnectionAttributes);
      azureInfrastructureMapping.setUsePublicDns(usePublicDns);
      azureInfrastructureMapping.setUsePrivateIp(usePrivateIp);
      return azureInfrastructureMapping;
    }
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends YamlWithComputeProvider {
    private String subscriptionId;
    private String resourceGroup;
    private List<AzureTag> azureTags;

    @lombok.Builder
    public Yaml(String type, String harnessApiVersion, String computeProviderType, String serviceName,
        String infraMappingType, String deploymentType, String computeProviderName, String subscriptionId,
        String resourceGroup, List<AzureTag> azureTags, Map<String, Object> blueprints) {
      super(type, harnessApiVersion, serviceName, infraMappingType, deploymentType, computeProviderType,
          computeProviderName, blueprints);
      this.subscriptionId = subscriptionId;
      this.resourceGroup = resourceGroup;
      this.azureTags = azureTags;
    }
  }
}
