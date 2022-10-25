/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.infra;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.expression.common.Expression.DISALLOW_SECRETS;
import static io.harness.validation.Validator.ensureType;

import static software.wings.beans.DirectKubernetesInfrastructureMapping.Builder.aDirectKubernetesInfrastructureMapping;
import static software.wings.beans.InfrastructureType.DIRECT_KUBERNETES;
import static software.wings.common.InfrastructureConstants.INFRA_KUBERNETES_INFRAID_EXPRESSION;

import static java.lang.String.format;

import io.harness.data.validator.Trimmed;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.common.Expression;

import software.wings.annotation.IncludeFieldMap;
import software.wings.api.CloudProviderType;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.service.impl.yaml.handler.InfraDefinition.CloudProviderInfrastructureYaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

@JsonTypeName("DIRECT_KUBERNETES")
@Data
@Builder
@FieldNameConstants(innerTypeName = "DirectKubernetesInfrastructureKeys")
public class DirectKubernetesInfrastructure
    implements InfraMappingInfrastructureProvider, KubernetesInfrastructure, FieldKeyValMapProvider, ProvisionerAware {
  private String cloudProviderId;
  @IncludeFieldMap private String clusterName;
  @IncludeFieldMap @Expression(DISALLOW_SECRETS) private String namespace;
  @Trimmed private String releaseName;
  private Map<String, String> expressions;

  @Override
  public InfrastructureMapping getInfraMapping() {
    return aDirectKubernetesInfrastructureMapping()
        .withClusterName(clusterName)
        .withNamespace(namespace)
        .withReleaseName(releaseName)
        .withComputeProviderSettingId(cloudProviderId)
        .withInfraMappingType(InfrastructureMappingType.DIRECT_KUBERNETES.name())
        .build();
  }

  @Override
  public String getReleaseName() {
    return isEmpty(releaseName) ? INFRA_KUBERNETES_INFRAID_EXPRESSION : releaseName;
  }

  @Override
  public Class<DirectKubernetesInfrastructureMapping> getMappingClass() {
    return DirectKubernetesInfrastructureMapping.class;
  }

  @Override
  public CloudProviderType getCloudProviderType() {
    return CloudProviderType.KUBERNETES_CLUSTER;
  }

  @Override
  public String getInfrastructureType() {
    return DIRECT_KUBERNETES;
  }

  @Override
  public Set<String> getSupportedExpressions() {
    return ImmutableSet.of(
        DirectKubernetesInfrastructureKeys.namespace, DirectKubernetesInfrastructureKeys.releaseName);
  }

  @Override
  public void applyExpressions(
      Map<String, Object> resolvedExpressions, String appId, String envId, String infraDefinitionId) {
    for (Map.Entry<String, Object> entry : resolvedExpressions.entrySet()) {
      switch (entry.getKey()) {
        case "namespace":
          ensureType(String.class, entry.getValue(), "Namespace should be of String type");
          setNamespace((String) entry.getValue());
          break;
        case "releaseName":
          ensureType(String.class, entry.getValue(), "Release name should be of String type");
          setReleaseName((String) entry.getValue());
          break;
        default:
          throw new InvalidRequestException(format("Unknown expression : [%s]", entry.getKey()));
      }
    }
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName(DIRECT_KUBERNETES)
  public static final class Yaml extends CloudProviderInfrastructureYaml {
    private String cloudProviderName;
    private String clusterName;
    private String namespace;
    private String releaseName;
    private Map<String, String> expressions;

    @Builder
    public Yaml(String type, String cloudProviderName, String clusterName, String namespace, String releaseName,
        Map<String, String> expressions) {
      super(type);
      setCloudProviderName(cloudProviderName);
      setClusterName(clusterName);
      setNamespace(namespace);
      setReleaseName(releaseName);
      setExpressions(expressions);
    }

    public Yaml() {
      super(DIRECT_KUBERNETES);
    }
  }
}
