/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.annotation.Blueprint;
import software.wings.beans.AwsInstanceFilter.AwsInstanceFilterBuilder;
import software.wings.beans.AwsInstanceFilter.Tag;
import software.wings.beans.AwsInstanceFilter.Tag.TagBuilder;
import software.wings.beans.InfrastructureMappingBlueprint.NodeFilteringType;
import software.wings.stencils.DataProvider;
import software.wings.utils.Utils;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.annotations.VisibleForTesting;
import dev.morphia.annotations.Transient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

/**
 * Created by anubhaw on 1/10/17.
 */
@JsonTypeName("AWS_SSH")
@FieldNameConstants(innerTypeName = "AwsInfrastructureMappingKeys")
@OwnedBy(CDP)
@TargetModule(_957_CG_BEANS)
public class AwsInfrastructureMapping extends InfrastructureMapping {
  private String restrictionType;
  private String restrictionExpression;
  @Blueprint private String region;
  private String hostConnectionAttrs;
  private String loadBalancerId;
  @Transient private String loadBalancerName;
  @Deprecated private String customName;
  private boolean usePublicDns;
  private String hostConnectionType;
  private boolean provisionInstances;
  @Blueprint private AwsInstanceFilter awsInstanceFilter;
  @Blueprint private String autoScalingGroupName;
  private boolean setDesiredCapacity;
  private int desiredCapacity;
  private String hostNameConvention;

  /**
   * Instantiates a new Aws infrastructure mapping.
   */
  public AwsInfrastructureMapping() {
    super(InfrastructureMappingType.AWS_SSH.name());
  }

  private void applyAwsInstanceFilters(Map<String, Object> map) {
    setAutoScalingGroupName(null);
    setProvisionInstances(false);
    AwsInstanceFilterBuilder builder = null;
    try {
      for (Entry<String, Object> entry : map.entrySet()) {
        switch (entry.getKey()) {
          case "region": {
            setRegion((String) entry.getValue());
            break;
          }
          case "vpcs": {
            if (builder == null) {
              builder = AwsInstanceFilter.builder();
            }
            builder.vpcIds(getList(entry.getValue()));
            break;
          }
          case "tags": {
            if (builder == null) {
              builder = AwsInstanceFilter.builder();
            }
            getTags(entry.getValue(), builder);
            break;
          }
          default: {
            throw new InvalidRequestException(
                format("Unsupported infrastructure mapping provisioner variable %s", entry.getKey()));
          }
        }
      }
    } catch (WingsException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new InvalidRequestException("Unable to set the provisioner variables to the mapping", exception);
    }

    if (getRegion() == null) {
      throw new InvalidRequestException("Region is required");
    }
    if (builder != null) {
      setAwsInstanceFilter(builder.build());
    } else {
      setAwsInstanceFilter(null);
    }
  }

  private void applyAutoScalingGroups(Map<String, Object> map) {
    setAwsInstanceFilter(null);
    setProvisionInstances(true);
    try {
      for (Entry<String, Object> entry : map.entrySet()) {
        switch (entry.getKey()) {
          case "region": {
            setRegion((String) entry.getValue());
            break;
          }
          case "autoScalingGroup": {
            setAutoScalingGroupName((String) entry.getValue());
            break;
          }
          default: {
            throw new InvalidRequestException(
                format("Unsupported infrastructure mapping provisioner variable %s", entry.getKey()));
          }
        }
      }
    } catch (WingsException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new InvalidRequestException("Unable to set the provisioner variables to the mapping", exception);
    }

    if (getRegion() == null) {
      throw new InvalidRequestException("Region is required.");
    }
    if (getAutoScalingGroupName() == null) {
      throw new InvalidRequestException("Auto scaling group name is required.");
    }
  }

  @Override
  public void applyProvisionerVariables(
      Map<String, Object> map, NodeFilteringType nodeFilteringType, boolean featureFlagEnabled) {
    if (featureFlagEnabled) {
      applyProvisionerVariables(map);
    } else {
      switch (nodeFilteringType) {
        case AWS_INSTANCE_FILTER: {
          applyAwsInstanceFilters(map);
          break;
        }
        case AWS_AUTOSCALING_GROUP: {
          applyAutoScalingGroups(map);
          break;
        }
        default: {
          // Should never happen
          throw new InvalidRequestException(format("Unidentified: [%s] node filtering type", nodeFilteringType.name()));
        }
      }
    }
  }

  @VisibleForTesting
  public void applyProvisionerVariables(Map<String, Object> resolvedBlueprints) {
    if (isNotEmpty(resolvedBlueprints)) {
      for (Map.Entry<String, Object> entry : resolvedBlueprints.entrySet()) {
        switch (entry.getKey()) {
          case "autoScalingGroupName":
            setAutoScalingGroupName((String) entry.getValue());
            break;
          case "awsInstanceFilter":
            AwsInstanceFilterBuilder builder = AwsInstanceFilter.builder();
            Map<String, Object> awsInstanceFilterMap = (Map<String, Object>) entry.getValue();
            for (Map.Entry<String, Object> awsInstanceFilterEntry : awsInstanceFilterMap.entrySet()) {
              switch (awsInstanceFilterEntry.getKey()) {
                case "vpcIds":
                  builder.vpcIds(getList(awsInstanceFilterEntry.getValue()));
                  break;
                case "tags":
                  getTags(awsInstanceFilterEntry.getValue(), builder);
                  break;
                default:
                  throw new InvalidRequestException(
                      format("Unknown blueprint field : [%s]", awsInstanceFilterEntry.getKey()));
              }
            }
            setAwsInstanceFilter(builder.build());
            break;
          case "region":
            setRegion((String) entry.getValue());
            break;
          default:
            throw new InvalidRequestException(format("Unknown blueprint field : [%s]", entry.getKey()));
        }
      }
    }
    if (isEmpty(getRegion())) {
      throw new InvalidRequestException("Region is required");
    }
    if (isProvisionInstances() && isEmpty(getAutoScalingGroupName())) {
      throw new InvalidRequestException("Auto scaling group is required");
    }
  }

  private void getTags(Object input, AwsInstanceFilterBuilder builder) {
    if (input instanceof Map) {
      final Map<String, Object> value = (Map<String, Object>) input;
      builder.tags(value.entrySet()
                       .stream()
                       .map(item -> new TagBuilder().key(item.getKey()).value((String) item.getValue()).build())
                       .collect(toList()));
    } else {
      List<Tag> tags = new ArrayList<>();
      String[] tokens = ((String) input).split(";");
      for (String token : tokens) {
        String[] subTokens = token.split(":");
        if (subTokens.length == 2) {
          tags.add(new TagBuilder().key(subTokens[0]).value(subTokens[1]).build());
        }
      }
      builder.tags(tags);
    }
  }

  /**
   * The type Yaml.
   */
  @Data
  @JsonPropertyOrder({"type", "harnessApiVersion", "connectionType"})
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends YamlWithComputeProvider {
    // maps to restrictionType
    private String restrictions;
    // maps to restrictionExpression
    private String expression;
    private String region;
    // maps to hostConnectionAttrs
    private String provisionerName;
    private String connectionType;
    private String loadBalancer;
    private boolean usePublicDns;
    private String hostConnectionType;
    private boolean provisionInstances;
    private String autoScalingGroup;
    private int desiredCapacity;
    private String hostNameConvention;

    // These four fields map to AwsInstanceFilter
    private List<String> vpcs = new ArrayList<>();
    private List<NameValuePair.Yaml> awsTags = new ArrayList<>();

    @lombok.Builder
    public Yaml(String type, String harnessApiVersion, String computeProviderType, String serviceName,
        String infraMappingType, String deploymentType, String computeProviderName, String name, String restrictions,
        String expression, String region, String provisionerName, String connectionType, String loadBalancer,
        boolean usePublicDns, String hostConnectionType, boolean provisionInstances, String autoScalingGroup,
        int desiredCapacity, List<String> vpcs, List<NameValuePair.Yaml> awsTags, String hostNameConvention,
        Map<String, Object> blueprints) {
      super(type, harnessApiVersion, computeProviderType, serviceName, infraMappingType, deploymentType,
          computeProviderName, blueprints);
      this.restrictions = restrictions;
      this.expression = expression;
      this.region = region;
      this.provisionerName = provisionerName;
      this.connectionType = connectionType;
      this.loadBalancer = loadBalancer;
      this.usePublicDns = usePublicDns;
      this.hostConnectionType = hostConnectionType;
      this.provisionInstances = provisionInstances;
      this.autoScalingGroup = autoScalingGroup;
      this.desiredCapacity = desiredCapacity;
      this.vpcs = vpcs;
      this.awsTags = awsTags;
      this.hostNameConvention = hostNameConvention;
    }
  }

  /**
   * Gets restriction type.
   *
   * @return the restriction type
   */
  public String getRestrictionType() {
    return restrictionType;
  }

  /**
   * Sets restriction type.
   *
   * @param restrictionType the restriction type
   */
  public void setRestrictionType(String restrictionType) {
    this.restrictionType = restrictionType;
  }

  /**
   * Gets restriction expression.
   *
   * @return the restriction expression
   */
  public String getRestrictionExpression() {
    return restrictionExpression;
  }

  /**
   * Sets restriction expression.
   *
   * @param restrictionExpression the restriction expression
   */
  public void setRestrictionExpression(String restrictionExpression) {
    this.restrictionExpression = restrictionExpression;
  }

  /**
   * Gets load balancer id.
   *
   * @return the load balancer id
   */
  public String getLoadBalancerId() {
    return loadBalancerId;
  }

  /**
   * Sets load balancer id.
   *
   * @param loadBalancerId the load balancer id
   */
  public void setLoadBalancerId(String loadBalancerId) {
    this.loadBalancerId = loadBalancerId;
  }

  @Override
  public String getHostConnectionAttrs() {
    return hostConnectionAttrs;
  }

  @SchemaIgnore
  @Override
  public String getDefaultName() {
    List<String> parts = new ArrayList();
    if (isNotEmpty(customName)) {
      parts.add(customName);
    }

    if (isNotEmpty(getComputeProviderName())) {
      parts.add(getComputeProviderName().toLowerCase());
    }

    parts.add("AWS");

    if (isNotEmpty(getRegion())) {
      parts.add(getRegion());
    }

    return Utils.normalize(String.join(" - ", parts));
  }

  /**
   * Gets region.
   *
   * @return the region
   */
  public String getRegion() {
    return region;
  }

  /**
   * Sets region.
   *
   * @param region the region
   */
  public void setRegion(String region) {
    this.region = region;
  }

  /**
   * Sets host connection attrs.
   *
   * @param hostConnectionAttrs the host connection attrs
   */
  public void setHostConnectionAttrs(String hostConnectionAttrs) {
    this.hostConnectionAttrs = hostConnectionAttrs;
  }

  /**
   * Gets load balancer name.
   *
   * @return the load balancer name
   */
  public String getLoadBalancerName() {
    return loadBalancerName;
  }

  /**
   * Sets load balancer name.
   *
   * @param loadBalancerName the load balancer name
   */
  public void setLoadBalancerName(String loadBalancerName) {
    this.loadBalancerName = loadBalancerName;
  }

  /**
   * Gets aws instance filter.
   *
   * @return the aws instance filter
   */
  public AwsInstanceFilter getAwsInstanceFilter() {
    return awsInstanceFilter;
  }

  /**
   * Sets aws instance filter.
   *
   * @param awsInstanceFilter the aws instance filter
   */
  public void setAwsInstanceFilter(AwsInstanceFilter awsInstanceFilter) {
    this.awsInstanceFilter = awsInstanceFilter;
  }

  /**
   * Is provision instances boolean.
   *
   * @return the boolean
   */
  public boolean isProvisionInstances() {
    return provisionInstances;
  }

  /**
   * Sets provision instances.
   *
   * @param provisionInstances the provision instances
   */
  public void setProvisionInstances(boolean provisionInstances) {
    this.provisionInstances = provisionInstances;
  }

  /**
   * Is use public dns boolean.
   *
   * @return the boolean
   */
  public boolean isUsePublicDns() {
    return usePublicDns;
  }

  /**
   * Sets use public dns.
   *
   * @param usePublicDns the use public dns
   */
  public void setUsePublicDns(boolean usePublicDns) {
    this.usePublicDns = usePublicDns;
  }

  public String getHostConnectionType() {
    return hostConnectionType;
  }

  public void setHostConnectionType(String hostConnectionType) {
    this.hostConnectionType = hostConnectionType;
  }

  /**
   * Gets auto scaling group name.
   *
   * @return the auto scaling group name
   */
  public String getAutoScalingGroupName() {
    return autoScalingGroupName;
  }

  /**
   * Sets auto scaling group name.
   *
   * @param autoScalingGroupName the auto scaling group name
   */
  public void setAutoScalingGroupName(String autoScalingGroupName) {
    this.autoScalingGroupName = autoScalingGroupName;
  }

  /**
   * Gets custom name.
   *
   * @return the custom name
   */
  @SchemaIgnore
  public String getCustomName() {
    return customName;
  }

  /**
   * Sets custom name.
   *
   * @param customName the custom name
   */
  public void setCustomName(String customName) {
    this.customName = customName;
  }

  /**
   * Is set desired capacity boolean.
   *
   * @return the boolean
   */
  public boolean isSetDesiredCapacity() {
    return setDesiredCapacity;
  }

  /**
   * Sets set desired capacity.
   *
   * @param setDesiredCapacity the set desired capacity
   */
  public void setSetDesiredCapacity(boolean setDesiredCapacity) {
    this.setDesiredCapacity = setDesiredCapacity;
  }

  /**
   * Gets desired capacity.
   *
   * @return the desired capacity
   */
  public int getDesiredCapacity() {
    return desiredCapacity;
  }

  /**
   * Sets desired capacity.
   *
   * @param desiredCapacity the desired capacity
   */
  public void setDesiredCapacity(int desiredCapacity) {
    this.desiredCapacity = desiredCapacity;
  }

  public String getHostNameConvention() {
    return hostNameConvention;
  }

  public void setHostNameConvention(String hostNameConvention) {
    this.hostNameConvention = hostNameConvention;
  }

  /**
   * The enum Restriction type.
   */
  public enum RestrictionType {
    /**
     * None restriction type.
     */
    NONE("None"),
    /**
     * Instance restriction type.
     */
    INSTANCE("By specific instances"),
    /**
     * Custom restriction type.
     */
    CUSTOM("By zone/tags etc");

    private String displayName;

    RestrictionType(String displayName) {
      this.displayName = displayName;
    }

    /**
     * Gets display name.
     *
     * @return the display name
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  /**
   * The type Aws infrastructure restriction provider.
   */
  public static class AwsInfrastructureRestrictionProvider implements DataProvider {
    @Override
    public Map<String, String> getData(String appId, Map<String, String> params) {
      return Arrays.stream(RestrictionType.values())
          .collect(toMap(RestrictionType::name, RestrictionType::getDisplayName));
    }
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    /**
     * The Entity yaml path.
     */
    public transient String entityYamlPath; // TODO:: remove it with changeSet batching
    /**
     * The App id.
     */
    protected String appId;
    private String accountId;
    private String restrictionType;
    private String restrictionExpression;
    private String region;
    private String uuid;
    private EmbeddedUser createdBy;
    private String hostConnectionAttrs;
    private long createdAt;
    private String loadBalancerId;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;
    private String loadBalancerName;
    private String customName;
    private boolean usePublicDns;
    private String hostConnectionType;
    private String computeProviderSettingId;
    private boolean provisionInstances;
    private String envId;
    private String serviceTemplateId;
    private AwsInstanceFilter awsInstanceFilter;
    private String serviceId;
    private String autoScalingGroupName;
    private String computeProviderType;
    private String infraMappingType;
    private boolean setDesiredCapacity;
    private String hostNameConvention;
    private String deploymentType;
    private int desiredCapacity;
    private String computeProviderName;
    private String name;
    private String provisionerId;
    // auto populate name
    private boolean autoPopulate = true;

    private Builder() {}

    /**
     * An aws infrastructure mapping builder.
     *
     * @return the builder
     */
    public static Builder anAwsInfrastructureMapping() {
      return new Builder();
    }

    /**
     * With restriction type builder.
     *
     * @param restrictionType the restriction type
     * @return the builder
     */
    public Builder withRestrictionType(String restrictionType) {
      this.restrictionType = restrictionType;
      return this;
    }

    /**
     * With restriction expression builder.
     *
     * @param restrictionExpression the restriction expression
     * @return the builder
     */
    public Builder withRestrictionExpression(String restrictionExpression) {
      this.restrictionExpression = restrictionExpression;
      return this;
    }

    /**
     * With region builder.
     *
     * @param region the region
     * @return the builder
     */
    public Builder withRegion(String region) {
      this.region = region;
      return this;
    }

    /**
     * With uuid builder.
     *
     * @param uuid the uuid
     * @return the builder
     */
    public Builder withUuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    /**
     * With app id builder.
     *
     * @param appId the app id
     * @return the builder
     */
    public Builder withAppId(String appId) {
      this.appId = appId;
      return this;
    }

    public Builder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    /**
     * With created by builder.
     *
     * @param createdBy the created by
     * @return the builder
     */
    public Builder withCreatedBy(EmbeddedUser createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    /**
     * With host connection attrs builder.
     *
     * @param hostConnectionAttrs the host connection attrs
     * @return the builder
     */
    public Builder withHostConnectionAttrs(String hostConnectionAttrs) {
      this.hostConnectionAttrs = hostConnectionAttrs;
      return this;
    }

    /**
     * With created at builder.
     *
     * @param createdAt the created at
     * @return the builder
     */
    public Builder withCreatedAt(long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    /**
     * With load balancer id builder.
     *
     * @param loadBalancerId the load balancer id
     * @return the builder
     */
    public Builder withLoadBalancerId(String loadBalancerId) {
      this.loadBalancerId = loadBalancerId;
      return this;
    }

    /**
     * With last updated by builder.
     *
     * @param lastUpdatedBy the last updated by
     * @return the builder
     */
    public Builder withLastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    /**
     * With last updated at builder.
     *
     * @param lastUpdatedAt the last updated at
     * @return the builder
     */
    public Builder withLastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    /**
     * With load balancer name builder.
     *
     * @param loadBalancerName the load balancer name
     * @return the builder
     */
    public Builder withLoadBalancerName(String loadBalancerName) {
      this.loadBalancerName = loadBalancerName;
      return this;
    }

    /**
     * With custom name builder.
     *
     * @param customName the custom name
     * @return the builder
     */
    public Builder withCustomName(String customName) {
      this.customName = customName;
      return this;
    }

    /**
     * With entity yaml path builder.
     *
     * @param entityYamlPath the entity yaml path
     * @return the builder
     */
    public Builder withEntityYamlPath(String entityYamlPath) {
      this.entityYamlPath = entityYamlPath;
      return this;
    }

    /**
     * With use public dns builder.
     *
     * @param usePublicDns the use public dns
     * @return the builder
     */
    public Builder withUsePublicDns(boolean usePublicDns) {
      this.usePublicDns = usePublicDns;
      return this;
    }

    public Builder withHostConnectionType(String hostConnectionType) {
      this.hostConnectionType = hostConnectionType;
      return this;
    }

    /**
     * With compute provider setting id builder.
     *
     * @param computeProviderSettingId the compute provider setting id
     * @return the builder
     */
    public Builder withComputeProviderSettingId(String computeProviderSettingId) {
      this.computeProviderSettingId = computeProviderSettingId;
      return this;
    }

    /**
     * With provision instances builder.
     *
     * @param provisionInstances the provision instances
     * @return the builder
     */
    public Builder withProvisionInstances(boolean provisionInstances) {
      this.provisionInstances = provisionInstances;
      return this;
    }

    /**
     * With env id builder.
     *
     * @param envId the env id
     * @return the builder
     */
    public Builder withEnvId(String envId) {
      this.envId = envId;
      return this;
    }

    /**
     * With service template id builder.
     *
     * @param serviceTemplateId the service template id
     * @return the builder
     */
    public Builder withServiceTemplateId(String serviceTemplateId) {
      this.serviceTemplateId = serviceTemplateId;
      return this;
    }

    /**
     * With aws instance filter builder.
     *
     * @param awsInstanceFilter the aws instance filter
     * @return the builder
     */
    public Builder withAwsInstanceFilter(AwsInstanceFilter awsInstanceFilter) {
      this.awsInstanceFilter = awsInstanceFilter;
      return this;
    }

    /**
     * With service id builder.
     *
     * @param serviceId the service id
     * @return the builder
     */
    public Builder withServiceId(String serviceId) {
      this.serviceId = serviceId;
      return this;
    }

    /**
     * With auto scaling group name builder.
     *
     * @param autoScalingGroupName the auto scaling group name
     * @return the builder
     */
    public Builder withAutoScalingGroupName(String autoScalingGroupName) {
      this.autoScalingGroupName = autoScalingGroupName;
      return this;
    }

    /**
     * With compute provider type builder.
     *
     * @param computeProviderType the compute provider type
     * @return the builder
     */
    public Builder withComputeProviderType(String computeProviderType) {
      this.computeProviderType = computeProviderType;
      return this;
    }

    /**
     * With infra mapping type builder.
     *
     * @param infraMappingType the infra mapping type
     * @return the builder
     */
    public Builder withInfraMappingType(String infraMappingType) {
      this.infraMappingType = infraMappingType;
      return this;
    }

    /**
     * With set desired capacity builder.
     *
     * @param setDesiredCapacity the set desired capacity
     * @return the builder
     */
    public Builder withSetDesiredCapacity(boolean setDesiredCapacity) {
      this.setDesiredCapacity = setDesiredCapacity;
      return this;
    }

    /**
     * With set host name convention builder.
     *
     * @param hostNameConvention the hostNameConvention
     * @return the builder
     */
    public Builder withHostNameConvention(String hostNameConvention) {
      this.hostNameConvention = hostNameConvention;
      return this;
    }

    /**
     * With deployment type builder.
     *
     * @param deploymentType the deployment type
     * @return the builder
     */
    public Builder withDeploymentType(String deploymentType) {
      this.deploymentType = deploymentType;
      return this;
    }

    /**
     * With desired capacity builder.
     *
     * @param desiredCapacity the desired capacity
     * @return the builder
     */
    public Builder withDesiredCapacity(int desiredCapacity) {
      this.desiredCapacity = desiredCapacity;
      return this;
    }

    /**
     * With compute provider name builder.
     *
     * @param computeProviderName the compute provider name
     * @return the builder
     */
    public Builder withComputeProviderName(String computeProviderName) {
      this.computeProviderName = computeProviderName;
      return this;
    }

    /**
     * With name builder.
     *
     * @param name the name
     * @return the builder
     */
    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withProvisionerId(String provisionerId) {
      this.provisionerId = provisionerId;
      return this;
    }

    /**
     * With auto populate builder.
     *
     * @param autoPopulate the auto populate
     * @return the builder
     */
    public Builder withAutoPopulate(boolean autoPopulate) {
      this.autoPopulate = autoPopulate;
      return this;
    }

    /**
     * Build aws infrastructure mapping.
     *
     * @return the aws infrastructure mapping
     */
    public AwsInfrastructureMapping build() {
      AwsInfrastructureMapping awsInfrastructureMapping = new AwsInfrastructureMapping();
      awsInfrastructureMapping.setRestrictionType(restrictionType);
      awsInfrastructureMapping.setRestrictionExpression(restrictionExpression);
      awsInfrastructureMapping.setRegion(region);
      awsInfrastructureMapping.setUuid(uuid);
      awsInfrastructureMapping.setAppId(appId);
      awsInfrastructureMapping.setCreatedBy(createdBy);
      awsInfrastructureMapping.setHostConnectionAttrs(hostConnectionAttrs);
      awsInfrastructureMapping.setCreatedAt(createdAt);
      awsInfrastructureMapping.setLoadBalancerId(loadBalancerId);
      awsInfrastructureMapping.setLastUpdatedBy(lastUpdatedBy);
      awsInfrastructureMapping.setLastUpdatedAt(lastUpdatedAt);
      awsInfrastructureMapping.setLoadBalancerName(loadBalancerName);
      awsInfrastructureMapping.setCustomName(customName);
      awsInfrastructureMapping.setEntityYamlPath(entityYamlPath);
      awsInfrastructureMapping.setUsePublicDns(usePublicDns);
      awsInfrastructureMapping.setHostConnectionType(hostConnectionType);
      awsInfrastructureMapping.setComputeProviderSettingId(computeProviderSettingId);
      awsInfrastructureMapping.setProvisionInstances(provisionInstances);
      awsInfrastructureMapping.setEnvId(envId);
      awsInfrastructureMapping.setServiceTemplateId(serviceTemplateId);
      awsInfrastructureMapping.setAwsInstanceFilter(awsInstanceFilter);
      awsInfrastructureMapping.setServiceId(serviceId);
      awsInfrastructureMapping.setAutoScalingGroupName(autoScalingGroupName);
      awsInfrastructureMapping.setComputeProviderType(computeProviderType);
      awsInfrastructureMapping.setInfraMappingType(infraMappingType);
      awsInfrastructureMapping.setSetDesiredCapacity(setDesiredCapacity);
      awsInfrastructureMapping.setDeploymentType(deploymentType);
      awsInfrastructureMapping.setDesiredCapacity(desiredCapacity);
      awsInfrastructureMapping.setComputeProviderName(computeProviderName);
      awsInfrastructureMapping.setName(name);
      awsInfrastructureMapping.setProvisionerId(provisionerId);
      awsInfrastructureMapping.setAutoPopulate(autoPopulate);
      awsInfrastructureMapping.setAccountId(accountId);
      awsInfrastructureMapping.setHostNameConvention(hostNameConvention);
      return awsInfrastructureMapping;
    }
  }
}
