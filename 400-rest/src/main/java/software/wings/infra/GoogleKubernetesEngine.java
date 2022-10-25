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

import static software.wings.beans.GcpKubernetesInfrastructureMapping.Builder.aGcpKubernetesInfrastructureMapping;
import static software.wings.beans.InfrastructureType.GCP_KUBERNETES_ENGINE;
import static software.wings.common.InfrastructureConstants.INFRA_KUBERNETES_INFRAID_EXPRESSION;

import static java.lang.String.format;

import io.harness.exception.InvalidRequestException;
import io.harness.expression.common.Expression;

import software.wings.annotation.IncludeFieldMap;
import software.wings.api.CloudProviderType;
import software.wings.beans.GcpKubernetesInfrastructureMapping;
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

@JsonTypeName("GCP_KUBERNETES")
@Data
@Builder
@FieldNameConstants(innerTypeName = "GoogleKubernetesEngineKeys")
public class GoogleKubernetesEngine
    implements KubernetesInfrastructure, InfraMappingInfrastructureProvider, FieldKeyValMapProvider, ProvisionerAware {
  private String cloudProviderId;
  @IncludeFieldMap private String clusterName;
  @IncludeFieldMap @Expression(DISALLOW_SECRETS) private String namespace;
  private String releaseName;
  private Map<String, String> expressions;

  @Override
  public InfrastructureMapping getInfraMapping() {
    return aGcpKubernetesInfrastructureMapping()
        .withClusterName(clusterName)
        .withComputeProviderSettingId(cloudProviderId)
        .withNamespace(namespace)
        .withReleaseName(releaseName)
        .withInfraMappingType(InfrastructureMappingType.GCP_KUBERNETES.name())
        .build();
  }

  @Override
  public String getReleaseName() {
    return isEmpty(releaseName) ? INFRA_KUBERNETES_INFRAID_EXPRESSION : releaseName;
  }

  @Override
  public Class<GcpKubernetesInfrastructureMapping> getMappingClass() {
    return GcpKubernetesInfrastructureMapping.class;
  }

  @Override
  public String getInfrastructureType() {
    return GCP_KUBERNETES_ENGINE;
  }

  @Override
  public CloudProviderType getCloudProviderType() {
    return CloudProviderType.GCP;
  }

  @Override
  public Set<String> getSupportedExpressions() {
    return ImmutableSet.of(GoogleKubernetesEngineKeys.clusterName, GoogleKubernetesEngineKeys.namespace,
        GoogleKubernetesEngineKeys.releaseName);
  }

  @Override
  public void applyExpressions(
      Map<String, Object> resolvedExpressions, String appId, String envId, String infraDefinitionId) {
    for (Map.Entry<String, Object> entry : resolvedExpressions.entrySet()) {
      switch (entry.getKey()) {
        case "clusterName":
          ensureType(String.class, entry.getValue(), "Region should be of String type");
          setClusterName((String) entry.getValue());
          break;
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
    if (getClusterName() == null) {
      throw new InvalidRequestException("Cluster Name is mandatory");
    }
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName(GCP_KUBERNETES_ENGINE)
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
      super(GCP_KUBERNETES_ENGINE);
    }
  }
}
