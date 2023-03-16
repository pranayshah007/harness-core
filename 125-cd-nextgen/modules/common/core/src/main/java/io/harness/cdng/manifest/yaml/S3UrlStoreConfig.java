/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.common.ParameterFieldHelper;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(ManifestStoreType.S3URL)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("s3UrlStore")
@RecasterAlias("io.harness.cdng.manifest.yaml.S3UrlStoreConfig")
public class S3UrlStoreConfig implements FileStorageStoreConfig, Visitable, WithConnectorRef {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  private ParameterField<String> connectorRef;
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  private ParameterField<String> region;
  @NotNull
  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @Wither
  private ParameterField<List<String>> urls;

  @Override
  public String getKind() {
    return ManifestStoreType.S3URL;
  }

  @Override
  public ParameterField<String> getConnectorReference() {
    return connectorRef;
  }

  public StoreConfig cloneInternal() {
    return S3UrlStoreConfig.builder().region(region).urls(urls).connectorRef(connectorRef).build();
  }

  @Override
  public StoreConfig applyOverrides(StoreConfig overrideConfig) {
    S3UrlStoreConfig s3UrlStoreConfig = (S3UrlStoreConfig) overrideConfig;
    S3UrlStoreConfig resultantS3UrlStore = this;
    if (!ParameterField.isNull(s3UrlStoreConfig.getConnectorRef())) {
      resultantS3UrlStore = resultantS3UrlStore.withConnectorRef(s3UrlStoreConfig.getConnectorRef());
    }
    if (!ParameterField.isNull(s3UrlStoreConfig.getUrls())) {
      resultantS3UrlStore = resultantS3UrlStore.withUrls(s3UrlStoreConfig.getUrls());
    }
    if (!ParameterField.isNull(s3UrlStoreConfig.getRegion())) {
      resultantS3UrlStore = resultantS3UrlStore.withRegion(s3UrlStoreConfig.getRegion());
    }
    return resultantS3UrlStore;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }

  @Override
  public FileStorageConfigDTO toFileStorageConfigDTO() {
    return S3UrlStorageConfigDTO.builder()
        .connectorRef(ParameterFieldHelper.getParameterFieldValue(connectorRef))
        .region(ParameterFieldHelper.getParameterFieldValue(region))
        .urls(ParameterFieldHelper.getParameterFieldValue(urls))
        .build();
  }
}
