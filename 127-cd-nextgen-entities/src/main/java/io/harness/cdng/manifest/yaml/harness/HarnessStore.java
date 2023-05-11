/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml.harness;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.manifest.yaml.harness.HarnessStoreConstants.HARNESS_STORE_TYPE;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.yaml.FileStorageConfigDTO;
import io.harness.cdng.manifest.yaml.FileStorageStoreConfig;
import io.harness.cdng.manifest.yaml.StoreConfigHelper;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.visitor.helpers.store.HarnessStoreVisitorHelper;
import io.harness.common.ParameterFieldHelper;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashSet;
import java.util.List;
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
@JsonTypeName(HARNESS_STORE_TYPE)
@SimpleVisitorHelper(helperClass = HarnessStoreVisitorHelper.class)
@TypeAlias("harnessStore")
@RecasterAlias("io.harness.cdng.manifest.yaml.harness.HarnessStore")
@FieldNameConstants(innerTypeName = "HarnessStoreConfigKeys")
public class HarnessStore implements HarnessStoreConfig, FileStorageStoreConfig, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @Wither
  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @JsonProperty("files")
  private ParameterField<List<String>> files;

  @Wither
  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @JsonProperty("secretFiles")
  private ParameterField<List<String>> secretFiles;

  @Override
  public String getKind() {
    return HARNESS_STORE_TYPE;
  }

  public HarnessStore cloneInternal() {
    return HarnessStore.builder().files(files).secretFiles(secretFiles).build();
  }

  @Override
  public StoreConfig applyOverrides(StoreConfig overrideConfig) {
    HarnessStore harnessStore = (HarnessStore) overrideConfig;
    HarnessStore resultantHarnessStore = this;

    if (harnessStore.getFiles() != null) {
      resultantHarnessStore = resultantHarnessStore.withFiles(harnessStore.getFiles());
    }

    if (harnessStore.getSecretFiles() != null) {
      resultantHarnessStore = resultantHarnessStore.withSecretFiles(harnessStore.getSecretFiles());
    }

    return resultantHarnessStore;
  }

  public HarnessStoreDTO toHarnessStoreDTO() {
    return HarnessStoreDTO.builder()
        .files(ParameterFieldHelper.getParameterFieldValue(files))
        .secretFiles(ParameterFieldHelper.getParameterFieldValue(secretFiles))
        .build();
  }

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public VisitableChildren getChildrenToWalk() {
    return VisitableChildren.builder().build();
  }

  @Override
  public FileStorageConfigDTO toFileStorageConfigDTO() {
    return toHarnessStoreDTO();
  }

  @Override
  public Set<String> validateAtRuntime() {
    Set<String> invalidParameters = new HashSet<>();
    if (StoreConfigHelper.checkListOfStringsParameterNullOrInput(secretFiles)
        && StoreConfigHelper.checkListOfStringsParameterNullOrInput(files)) {
      invalidParameters.add(HarnessStoreConfigKeys.files);
    }
    return invalidParameters;
  }
}
