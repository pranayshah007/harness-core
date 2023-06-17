/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;
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
import java.util.HashSet;
import java.util.List;
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
@JsonTypeName(ManifestStoreType.S3)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("s3Store")
@RecasterAlias("io.harness.cdng.manifest.yaml.S3StoreConfig")
@FieldNameConstants(innerTypeName = "S3StoreConfigKeys")
public class S3StoreConfig implements FileStorageStoreConfig, Visitable, WithConnectorRef {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither private ParameterField<String> connectorRef;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither private ParameterField<String> bucketName;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither private ParameterField<String> region;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither private ParameterField<String> folderPath;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @Wither
  private ParameterField<List<String>> paths;

  @Override
  public String getKind() {
    return ManifestStoreType.S3;
  }

  @Override
  public ParameterField<String> getConnectorReference() {
    return connectorRef;
  }

  public StoreConfig cloneInternal() {
    return S3StoreConfig.builder()
        .connectorRef(connectorRef)
        .bucketName(bucketName)
        .region(region)
        .folderPath(folderPath)
        .paths(paths)
        .build();
  }

  @Override
  public StoreConfig applyOverrides(StoreConfig overrideConfig) {
    S3StoreConfig s3StoreConfig = (S3StoreConfig) overrideConfig;
    S3StoreConfig resultantS3Store = this;
    if (!ParameterField.isNull(s3StoreConfig.getConnectorRef())) {
      resultantS3Store = resultantS3Store.withConnectorRef(s3StoreConfig.getConnectorRef());
    }

    if (!ParameterField.isNull(s3StoreConfig.getBucketName())) {
      resultantS3Store = resultantS3Store.withBucketName(s3StoreConfig.getBucketName());
    }

    if (!ParameterField.isNull(s3StoreConfig.getRegion())) {
      resultantS3Store = resultantS3Store.withRegion(s3StoreConfig.getRegion());
    }

    if (!ParameterField.isNull(s3StoreConfig.getFolderPath())) {
      resultantS3Store = resultantS3Store.withFolderPath(s3StoreConfig.getFolderPath());
    }

    return resultantS3Store;
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
  public FileStorageConfigDTO toFileStorageConfigDTO() {
    return S3StorageConfigDTO.builder()
        .connectorRef(ParameterFieldHelper.getParameterFieldValue(connectorRef))
        .region(ParameterFieldHelper.getParameterFieldValue(region))
        .bucket(ParameterFieldHelper.getParameterFieldValue(bucketName))
        .paths(ParameterFieldHelper.getParameterFieldValue(paths))
        .folderPath(ParameterFieldHelper.getParameterFieldValue(folderPath))
        .build();
  }

  @Override
  public Set<String> validateAtRuntime() {
    Set<String> invalidParameters = new HashSet<>();
    if (StoreConfigHelper.checkStringParameterNullOrInput(connectorRef)) {
      invalidParameters.add(S3StoreConfigKeys.connectorRef);
    }
    if (StoreConfigHelper.checkStringParameterNullOrInput(bucketName)) {
      invalidParameters.add(S3StoreConfigKeys.bucketName);
    }
    if (StoreConfigHelper.checkStringParameterNullOrInput(region)) {
      invalidParameters.add(S3StoreConfigKeys.region);
    }
    if (StoreConfigHelper.checkStringParameterNullOrInput(folderPath)
        && StoreConfigHelper.checkListOfStringsParameterNullOrInput(paths)) {
      invalidParameters.add(S3StoreConfigKeys.folderPath);
    }
    return invalidParameters;
  }
}
