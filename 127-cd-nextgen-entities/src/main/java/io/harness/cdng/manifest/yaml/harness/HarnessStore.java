/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml.harness;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.manifest.yaml.harness.HarnessStoreConstants.HARNESS_STORE_TYPE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.common.ParameterFieldHelper;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Wither;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(HARNESS_STORE_TYPE)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("harnessStore")
@RecasterAlias("io.harness.cdng.manifest.yaml.harness.HarnessStore")
public class HarnessStore implements HarnessStoreConfig, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull
  @NotEmpty
  @Wither
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @JsonProperty("fileReference")
  private ParameterField<String> fileReference;

  @NotNull
  @NotEmpty
  @Wither
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @JsonProperty("filePath")
  private ParameterField<String> filePath;

  @NotNull
  @Wither
  @ApiModelProperty("io.harness.cdng.manifest.yaml.harness.HarnessFileType")
  @JsonProperty("fileType")
  private HarnessFileType fileType;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public String getKind() {
    return HARNESS_STORE_TYPE;
  }

  @Override
  public ParameterField<String> getFileReference() {
    return fileReference;
  }

  public HarnessStore cloneInternal() {
    return HarnessStore.builder().fileReference(fileReference).filePath(filePath).fileType(fileType).build();
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    return VisitableChildren.builder().build();
  }

  @Override
  public StoreConfig applyOverrides(StoreConfig overrideConfig) {
    HarnessStore harnessStore = (HarnessStore) overrideConfig;
    HarnessStore resultantHarnessStore = this;
    if (!ParameterField.isNull(harnessStore.getFileReference())) {
      resultantHarnessStore = resultantHarnessStore.withFileReference(harnessStore.getFileReference());
    }

    if (!ParameterField.isNull(harnessStore.getFilePath())) {
      resultantHarnessStore = resultantHarnessStore.withFilePath(harnessStore.getFilePath());
    }

    if (harnessStore.getFileType() != null) {
      resultantHarnessStore = resultantHarnessStore.withFileType(harnessStore.getFileType());
    }

    return resultantHarnessStore;
  }

  @Override
  public Map<String, ParameterField<String>> extractFileRefs() {
    Map<String, ParameterField<String>> fileRefMap = new HashMap<>();
    fileRefMap.put(YAMLFieldNameConstants.FILE_REF, fileReference);
    return fileRefMap;
  }

  public HarnessStoreDTO toHarnessStoreDTO() {
    return HarnessStoreDTO.builder()
        .fileReference(ParameterFieldHelper.getParameterFieldValue(fileReference))
        .filePath(ParameterFieldHelper.getParameterFieldValue(filePath))
        .fileType(fileType)
        .build();
  }
}
