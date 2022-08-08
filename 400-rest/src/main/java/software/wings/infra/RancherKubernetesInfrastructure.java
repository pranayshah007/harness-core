/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.infra;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.expression.Expression.DISALLOW_SECRETS;

import static software.wings.beans.InfrastructureType.RANCHER_KUBERNETES;
import static software.wings.beans.RancherKubernetesInfrastructureMapping.Builder.aRancherKubernetesInfrastructureMapping;
import static software.wings.common.InfrastructureConstants.INFRA_KUBERNETES_INFRAID_EXPRESSION;

import io.harness.data.validator.Trimmed;
import io.harness.expression.Expression;

import software.wings.annotation.IncludeFieldMap;
import software.wings.api.CloudProviderType;
import software.wings.beans.ClusterSelectionCriteriaEntry;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.RancherKubernetesInfrastructureMapping;
import software.wings.service.impl.yaml.handler.InfraDefinition.CloudProviderInfrastructureYaml;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

@JsonTypeName("RANCHER_KUBERNETES")
@Data
@Builder
@FieldNameConstants(innerTypeName = "RancherKubernetesInfrastructureKeys")
public class RancherKubernetesInfrastructure
    implements InfraMappingInfrastructureProvider, KubernetesInfrastructure, FieldKeyValMapProvider {
  private String cloudProviderId;
  @IncludeFieldMap @Expression(DISALLOW_SECRETS) private String namespace;
  @Trimmed private String releaseName;
  private List<ClusterSelectionCriteriaEntry> clusterSelectionCriteria;

  @JsonIgnore
  @Override
  public String getClusterName() {
    // cluster id is null in rancher
    return null;
  }

  @Override
  public InfrastructureMapping getInfraMapping() {
    return aRancherKubernetesInfrastructureMapping()
        .withNamespace(namespace)
        .withReleaseName(releaseName)
        .withComputeProviderSettingId(cloudProviderId)
        .withInfraMappingType(InfrastructureMappingType.RANCHER_KUBERNETES.name())
        .build();
  }

  @Override
  public String getReleaseName() {
    return isEmpty(releaseName) ? INFRA_KUBERNETES_INFRAID_EXPRESSION : releaseName;
  }

  @Override
  public Class<RancherKubernetesInfrastructureMapping> getMappingClass() {
    return RancherKubernetesInfrastructureMapping.class;
  }

  @Override
  public CloudProviderType getCloudProviderType() {
    return CloudProviderType.RANCHER;
  }

  @Override
  public String getInfrastructureType() {
    return RANCHER_KUBERNETES;
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName(RANCHER_KUBERNETES)
  public static final class Yaml extends CloudProviderInfrastructureYaml {
    private String cloudProviderName;
    private String namespace;
    private String releaseName;
    private List<ClusterSelectionCriteriaEntry> clusterSelectionCriteria;

    @Builder
    public Yaml(String type, String cloudProviderName, String namespace, String releaseName,
        List<ClusterSelectionCriteriaEntry> clusterSelectionCriteria) {
      super(type);
      setCloudProviderName(cloudProviderName);
      setNamespace(namespace);
      setReleaseName(releaseName);
      setClusterSelectionCriteria(clusterSelectionCriteria);
    }

    public Yaml() {
      super(RANCHER_KUBERNETES);
    }
  }
}
