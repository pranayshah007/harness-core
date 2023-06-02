/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.yaml.ParameterField;
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

@OwnedBy(CDC)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(ManifestStoreType.GCS)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("gcsStore")
@RecasterAlias("io.harness.cdng.manifest.yaml.GcsStoreConfig")
@FieldNameConstants(innerTypeName = "GcsStoreConfigKeys")
public class GcsStoreConfig implements StoreConfig, Visitable, WithConnectorRef {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither private ParameterField<String> connectorRef;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither private ParameterField<String> bucketName;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither private ParameterField<String> folderPath;

  @Override
  public String getKind() {
    return ManifestStoreType.GCS;
  }

  @Override
  public ParameterField<String> getConnectorReference() {
    return connectorRef;
  }

  public StoreConfig cloneInternal() {
    return GcsStoreConfig.builder().connectorRef(connectorRef).bucketName(bucketName).folderPath(folderPath).build();
  }

  @Override
  public StoreConfig applyOverrides(StoreConfig overrideConfig) {
    GcsStoreConfig gcsStoreConfig = (GcsStoreConfig) overrideConfig;
    GcsStoreConfig resultantGcsStore = this;
    if (!ParameterField.isNull(gcsStoreConfig.getConnectorRef())) {
      resultantGcsStore = resultantGcsStore.withConnectorRef(gcsStoreConfig.getConnectorRef());
    }

    if (!ParameterField.isNull(gcsStoreConfig.getBucketName())) {
      resultantGcsStore = resultantGcsStore.withBucketName(gcsStoreConfig.getBucketName());
    }

    if (!ParameterField.isNull(gcsStoreConfig.getFolderPath())) {
      resultantGcsStore = resultantGcsStore.withFolderPath(gcsStoreConfig.getFolderPath());
    }

    return resultantGcsStore;
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

  @Override
  public Set<String> validateAtRuntime() {
    Set<String> invalidParameters = new HashSet<>();
    if (StoreConfigHelper.checkStringParameterNullOrInput(connectorRef)) {
      invalidParameters.add(GcsStoreConfigKeys.connectorRef);
    }
    if (StoreConfigHelper.checkStringParameterNullOrInput(bucketName)) {
      invalidParameters.add(GcsStoreConfigKeys.bucketName);
    }
    return invalidParameters;
  }
}
