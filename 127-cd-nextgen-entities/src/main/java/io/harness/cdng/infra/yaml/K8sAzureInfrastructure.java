/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.yaml;

import static io.harness.cdng.k8s.K8sEntityHelper.K8S_INFRA_NAMESPACE_REGEX_PATTERN;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.infra.beans.InfraMapping;
import io.harness.cdng.infra.beans.K8sAzureInfraMapping;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.SkipAutoEvaluation;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiParam;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import lombok.experimental.Wither;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.TypeAlias;

@Value
@SuperBuilder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(InfrastructureKind.KUBERNETES_AZURE)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("k8sAzureInfrastructure")
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.infra.yaml.K8sAzureInfrastructure")
public class K8sAzureInfrastructure
    extends InfrastructureDetailsAbstract implements Infrastructure, Visitable, WithConnectorRef, AzureInfrastructure {
  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> connectorRef;
  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @YamlSchemaTypes({expression})
  @Pattern(regexp = K8S_INFRA_NAMESPACE_REGEX_PATTERN)
  @Wither
  ParameterField<String> namespace;
  @NotNull
  @NotEmpty
  @SkipAutoEvaluation
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> releaseName;
  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> cluster;
  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> subscriptionId;
  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> resourceGroup;
  @ApiParam(defaultValue = "false")
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  @Wither
  ParameterField<Boolean> useClusterAdminCredentials;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public InfraMapping getInfraMapping() {
    return K8sAzureInfraMapping.builder()
        .azureConnector(connectorRef.getValue())
        .namespace(namespace.getValue())
        .cluster(cluster.getValue())
        .subscription(subscriptionId.getValue())
        .resourceGroup(resourceGroup.getValue())
        .useClusterAdminCredentials(useClusterAdminCredentials.getValue())
        .build();
  }

  @Override
  public ParameterField<String> getConnectorReference() {
    return connectorRef;
  }

  @Override
  public String[] getInfrastructureKeyValues() {
    return new String[] {connectorRef.getValue(), cluster.getValue(), namespace.getValue(), subscriptionId.getValue(),
        resourceGroup.getValue()};
  }

  @Override
  public String getKind() {
    return InfrastructureKind.KUBERNETES_AZURE;
  }

  @Override
  public Infrastructure applyOverrides(Infrastructure overrideConfig) {
    K8sAzureInfrastructure config = (K8sAzureInfrastructure) overrideConfig;
    K8sAzureInfrastructure resultantInfra = this;
    if (!ParameterField.isNull(config.getConnectorRef())) {
      resultantInfra = resultantInfra.withConnectorRef(config.getConnectorRef());
    }
    if (!ParameterField.isNull(config.getNamespace())) {
      resultantInfra = resultantInfra.withNamespace(config.getNamespace());
    }
    if (!ParameterField.isNull(config.getCluster())) {
      resultantInfra = resultantInfra.withCluster(config.getCluster());
    }
    if (!ParameterField.isNull(config.getReleaseName())) {
      resultantInfra = resultantInfra.withReleaseName(config.getReleaseName());
    }
    if (!ParameterField.isNull(config.getSubscriptionId())) {
      resultantInfra = resultantInfra.withSubscriptionId(config.getSubscriptionId());
    }
    if (!ParameterField.isNull(config.getResourceGroup())) {
      resultantInfra = resultantInfra.withResourceGroup(config.getResourceGroup());
    }
    if (!ParameterField.isNull(config.getUseClusterAdminCredentials())) {
      resultantInfra = resultantInfra.withUseClusterAdminCredentials(config.getUseClusterAdminCredentials());
    }
    if (!ParameterField.isNull(config.getProvisioner())) {
      resultantInfra.setProvisioner(config.getProvisioner());
    }

    return resultantInfra;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }
}
