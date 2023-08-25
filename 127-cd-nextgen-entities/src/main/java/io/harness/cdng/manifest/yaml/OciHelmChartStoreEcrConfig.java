/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.manifest.yaml.oci.OciStoreConfigType.ECR;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.yaml.oci.OciHelmChartStoreConfig;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(ECR)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("OciEcrConfig")
@RecasterAlias("io.harness.cdng.manifest.yaml.OciHelmChartStoreEcrConfig")
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
public class OciHelmChartStoreEcrConfig implements OciHelmChartStoreConfig, Visitable, WithConnectorRef {
  @Wither
  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  private ParameterField<String> connectorRef;
  @Wither
  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  private ParameterField<String> region;
  @Wither @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) private ParameterField<String> registryId;

  @Override
  public String getKind() {
    return ECR;
  }

  @Override
  public OciHelmChartStoreEcrConfig cloneInternal() {
    return OciHelmChartStoreEcrConfig.builder()
        .connectorRef(connectorRef)
        .region(region)
        .registryId(registryId)
        .build();
  }

  @Override
  public ParameterField<String> getConnectorReference() {
    return connectorRef;
  }

  @Override
  public OciHelmChartStoreConfig applyOverrides(OciHelmChartStoreConfig overrideConfig) {
    OciHelmChartStoreEcrConfig ociHelmChartStoreEcrConfig = (OciHelmChartStoreEcrConfig) overrideConfig;
    OciHelmChartStoreEcrConfig resultantHelmOciHelmChart = this;
    if (!ParameterField.isNull(ociHelmChartStoreEcrConfig.getConnectorReference())) {
      resultantHelmOciHelmChart =
          resultantHelmOciHelmChart.withConnectorRef(ociHelmChartStoreEcrConfig.getConnectorReference());
    }
    if (!ParameterField.isNull(ociHelmChartStoreEcrConfig.getRegion())) {
      resultantHelmOciHelmChart = resultantHelmOciHelmChart.withRegion(ociHelmChartStoreEcrConfig.getRegion());
    }
    if (!ParameterField.isNull(ociHelmChartStoreEcrConfig.getRegistryId())) {
      resultantHelmOciHelmChart = resultantHelmOciHelmChart.withRegistryId(ociHelmChartStoreEcrConfig.getRegistryId());
    }
    return resultantHelmOciHelmChart;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }

  @Override
  public void overrideConnectorRef(ParameterField<String> overridingConnectorRef) {
    if (ParameterField.isNotNull(overridingConnectorRef)) {
      connectorRef = overridingConnectorRef;
    }
  }
}
