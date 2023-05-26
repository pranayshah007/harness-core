/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.oci.OciHelmChartStoreConfig;
import io.harness.cdng.manifest.yaml.oci.OciHelmChartStoreConfigWrapper;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.SkipAutoEvaluation;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(ManifestStoreType.OCI)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("OciHelmChartConfig")
@RecasterAlias("io.harness.cdng.manifest.yaml.OciHelmChartConfig")
@FieldNameConstants(innerTypeName = "OciHelmChartConfigKeys")
public class OciHelmChartConfig implements StoreConfig, Visitable, WithConnectorRef {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @Wither
  @JsonProperty("config")
  @ApiModelProperty(dataType = "io.harness.cdng.manifest.yaml.oci.OciHelmChartStoreConfigWrapper")
  @SkipAutoEvaluation
  private ParameterField<OciHelmChartStoreConfigWrapper> config;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither private ParameterField<String> basePath;

  @Override
  public String getKind() {
    return ManifestStoreType.OCI;
  }

  @Override
  public StoreConfig cloneInternal() {
    return OciHelmChartConfig.builder().basePath(basePath).config(config).build();
  }

  @Override
  public ParameterField<String> getConnectorReference() {
    return config.getValue().getSpec().getConnectorReference();
  }

  public StoreConfig applyOverrides(StoreConfig overrideConfig) {
    OciHelmChartConfig overrideHelmConfig = (OciHelmChartConfig) overrideConfig;
    OciHelmChartConfig resultantHelmOciChart = this;
    if (!ParameterField.isNull(overrideHelmConfig.getBasePath())) {
      resultantHelmOciChart = resultantHelmOciChart.withBasePath(overrideHelmConfig.getBasePath());
    }
    if (!ParameterField.isNull(overrideHelmConfig.getConfig())) {
      resultantHelmOciChart = resultantHelmOciChart.withConfig(overrideHelmConfig.getConfig());
    }

    return resultantHelmOciChart;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, getConnectorReference());
    return connectorRefMap;
  }

  @Override
  public void overrideConnectorRef(ParameterField<String> overridingConnectorRef) {
    if (ParameterField.isNotNull(config)) {
      OciHelmChartStoreConfig ociStoreConfig = config.getValue().getSpec();
      if (ociStoreConfig != null && !ParameterField.isNull(overridingConnectorRef)) {
        ociStoreConfig.overrideConnectorRef(overridingConnectorRef);
      }
    }
  }

  @Override
  public Set<String> validateAtRuntime() {
    Set<String> invalidParameters = new HashSet<>();
    if (StoreConfigHelper.checkStringParameterNullOrInput(getConnectorReference())) {
      invalidParameters.add("connectorRef");
    }
    if (StoreConfigHelper.checkStringParameterNullOrInput(basePath)) {
      invalidParameters.add(OciHelmChartConfigKeys.basePath);
    }
    return invalidParameters;
  }
}
