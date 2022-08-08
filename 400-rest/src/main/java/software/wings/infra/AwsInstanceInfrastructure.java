/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.infra;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.Expression.ALLOW_SECRETS;
import static io.harness.validation.Validator.ensureType;

import static software.wings.beans.AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping;
import static software.wings.beans.InfrastructureType.AWS_INSTANCE;

import static java.lang.String.format;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.expression.Expression;

import software.wings.annotation.IncludeFieldMap;
import software.wings.api.CloudProviderType;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.AwsInstanceFilter;
import software.wings.beans.AwsInstanceFilter.AwsInstanceFilterKeys;
import software.wings.beans.AwsInstanceFilter.Tag;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.service.impl.yaml.handler.InfraDefinition.CloudProviderInfrastructureYaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Transient;

@JsonTypeName("AWS_SSH")
@Data
@Builder
@FieldNameConstants(innerTypeName = "AwsInstanceInfrastructureKeys")
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class AwsInstanceInfrastructure
    implements InfraMappingInfrastructureProvider, FieldKeyValMapProvider, SshBasedInfrastructure, ProvisionerAware {
  private String cloudProviderId;
  @IncludeFieldMap private String region;
  private String hostConnectionAttrs;
  private String loadBalancerId;
  @Transient private String loadBalancerName;
  private boolean usePublicDns;
  private String hostConnectionType;
  @Expression(ALLOW_SECRETS) private AwsInstanceFilter awsInstanceFilter;
  private String autoScalingGroupName;
  private boolean setDesiredCapacity;
  private int desiredCapacity;
  private String hostNameConvention;
  private boolean provisionInstances;
  private Map<String, String> expressions;

  private boolean createAwsInstanceFilter() {
    return !provisionInstances && awsInstanceFilter == null;
  }

  @Override
  public InfrastructureMapping getInfraMapping() {
    AwsInfrastructureMapping infrastructureMapping = anAwsInfrastructureMapping()
                                                         .withComputeProviderSettingId(cloudProviderId)
                                                         .withRegion(region)
                                                         .withHostConnectionAttrs(hostConnectionAttrs)
                                                         .withLoadBalancerId(loadBalancerId)
                                                         .withUsePublicDns(usePublicDns)
                                                         .withHostConnectionType(hostConnectionType)
                                                         .withAutoScalingGroupName(autoScalingGroupName)
                                                         .withSetDesiredCapacity(setDesiredCapacity)
                                                         .withDesiredCapacity(desiredCapacity)
                                                         .withHostNameConvention(hostNameConvention)
                                                         .withProvisionInstances(provisionInstances)
                                                         .withAwsInstanceFilter(awsInstanceFilter)
                                                         .withInfraMappingType(InfrastructureMappingType.AWS_SSH.name())
                                                         .build();
    if (createAwsInstanceFilter()) {
      infrastructureMapping.setAwsInstanceFilter(AwsInstanceFilter.builder().build());
    }
    return infrastructureMapping;
  }

  @Override
  public Class<AwsInfrastructureMapping> getMappingClass() {
    return AwsInfrastructureMapping.class;
  }

  @Override
  public String getInfrastructureType() {
    return AWS_INSTANCE;
  }

  @Override
  public CloudProviderType getCloudProviderType() {
    return CloudProviderType.AWS;
  }

  @Override
  public Set<String> getSupportedExpressions() {
    return ImmutableSet.of(AwsInstanceInfrastructureKeys.autoScalingGroupName, AwsInstanceInfrastructureKeys.region,
        AwsInstanceFilterKeys.vpcIds, AwsInstanceFilterKeys.tags, AwsInstanceInfrastructureKeys.loadBalancerId);
  }

  @Override
  public Set<String> getUserDefinedUniqueInfraFields() {
    Set<String> fields = new HashSet<>(Collections.singletonList(AwsInstanceInfrastructureKeys.awsInstanceFilter));
    if (null != awsInstanceFilter) {
      return fields;
    } else if (createAwsInstanceFilter()) {
      awsInstanceFilter = AwsInstanceFilter.builder().build();
      return fields;
    }

    return emptySet();
  }

  @Override
  public void applyExpressions(
      Map<String, Object> resolvedExpressions, String appId, String envId, String infraDefinitionId) {
    if (isNotEmpty(resolvedExpressions)) {
      for (Map.Entry<String, Object> entry : resolvedExpressions.entrySet()) {
        switch (entry.getKey()) {
          case "autoScalingGroupName":
            ensureType(String.class, entry.getValue(), "Auto-Scaling Group should be String type");
            setAutoScalingGroupName((String) entry.getValue());
            break;
          case "region":
            ensureType(String.class, entry.getValue(), "Region should be String type");
            setRegion((String) entry.getValue());
            break;
          case "vpcIds":
            AwsInstanceFilter awsInstanceFilter =
                getAwsInstanceFilter() != null ? getAwsInstanceFilter() : AwsInstanceFilter.builder().build();
            awsInstanceFilter.setVpcIds(getList(entry.getValue()));
            setAwsInstanceFilter(awsInstanceFilter);
            break;
          case "tags":
            awsInstanceFilter =
                getAwsInstanceFilter() != null ? getAwsInstanceFilter() : AwsInstanceFilter.builder().build();
            awsInstanceFilter.setTags(getTags(entry.getValue()));
            setAwsInstanceFilter(awsInstanceFilter);
            break;
          case "loadBalancerId":
            ensureType(String.class, entry.getValue(), "Load balancer name should be String type");
            setLoadBalancerId((String) entry.getValue());
            break;
          default:
            throw new InvalidRequestException(format("Unknown expression : [%s]", entry.getKey()));
        }
      }
    }
  }

  private List<Tag> getTags(Object input) {
    if (input instanceof Map) {
      Map<?, ?> map = (Map) input;
      return map.entrySet()
          .stream()
          .map(item -> Tag.builder().key((String) item.getKey()).value(item.getValue().toString()).build())
          .collect(toList());
    } else if (input instanceof String) {
      List<Tag> tags = new ArrayList<>();
      String[] tokens = ((String) input).split(";");
      for (String token : tokens) {
        String[] subTokens = token.split(":");
        if (subTokens.length == 2) {
          tags.add(Tag.builder().key(subTokens[0]).value(subTokens[1]).build());
        }
      }
      return tags;
    } else {
      throw new InvalidRequestException(
          format(
              "Map<String,String> or Comma-separated string with Semi-colon separated key-value expected. Found [%s]",
              input.getClass()),
          WingsException.USER);
    }
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName(AWS_INSTANCE)
  public static final class Yaml extends CloudProviderInfrastructureYaml {
    private String cloudProviderName;
    private String region;
    private String hostConnectionAttrsName;
    private String loadBalancerName;
    private boolean usePublicDns;
    private String hostConnectionType;
    private boolean useAutoScalingGroup;
    private AwsInstanceFilter awsInstanceFilter;
    private String autoScalingGroupName;
    private boolean setDesiredCapacity;
    private int desiredCapacity;
    private String hostNameConvention;
    private Map<String, String> expressions;

    @Builder
    public Yaml(String type, String cloudProviderName, String region, String hostConnectionAttrsName,
        String loadBalancerName, boolean usePublicDns, String hostConnectionType, boolean useAutoScalingGroup,
        AwsInstanceFilter awsInstanceFilter, String autoScalingGroupName, boolean setDesiredCapacity,
        int desiredCapacity, String hostNameConvention, Map<String, String> expressions) {
      super(type);
      setCloudProviderName(cloudProviderName);
      setRegion(region);
      setHostConnectionAttrsName(hostConnectionAttrsName);
      setLoadBalancerName(loadBalancerName);
      setUsePublicDns(usePublicDns);
      setHostConnectionType(hostConnectionType);
      setUseAutoScalingGroup(useAutoScalingGroup);
      setAutoScalingGroupName(autoScalingGroupName);
      setAwsInstanceFilter(awsInstanceFilter);
      setSetDesiredCapacity(setDesiredCapacity);
      setDesiredCapacity(desiredCapacity);
      setHostNameConvention(hostNameConvention);
      setExpressions(expressions);
    }

    public Yaml() {
      super(AWS_INSTANCE);
    }
  }
}
