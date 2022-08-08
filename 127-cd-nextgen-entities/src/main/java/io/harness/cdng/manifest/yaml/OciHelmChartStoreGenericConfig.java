/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.manifest.yaml.oci.OciStoreConfigType.GENERIC;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.oci.OciHelmChartStoreConfig;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(GENERIC)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("OciGenericConfig")
@RecasterAlias("io.harness.cdng.manifest.yaml.OciHelmChartGenericConfig")
public class OciHelmChartStoreGenericConfig implements OciHelmChartStoreConfig, Visitable, WithConnectorRef {
  @YamlSchemaTypes({string}) @Wither private ParameterField<String> connectorRef;

  @Override
  public String getKind() {
    return GENERIC;
  }

  @Override
  public OciHelmChartStoreConfig cloneInternal() {
    return OciHelmChartStoreGenericConfig.builder().connectorRef(connectorRef).build();
  }

  @Override
  public ParameterField<String> getConnectorReference() {
    return connectorRef;
  }

  @Override
  public OciHelmChartStoreConfig applyOverrides(OciHelmChartStoreConfig overrideConfig) {
    OciHelmChartStoreGenericConfig ociHelmChartStoreGenericConfig = (OciHelmChartStoreGenericConfig) overrideConfig;
    OciHelmChartStoreGenericConfig resultantHelmOciHelmChart = this;
    if (!ParameterField.isNull(ociHelmChartStoreGenericConfig.getConnectorReference())) {
      resultantHelmOciHelmChart =
          resultantHelmOciHelmChart.withConnectorRef(ociHelmChartStoreGenericConfig.getConnectorReference());
    }
    return resultantHelmOciHelmChart;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }
}
