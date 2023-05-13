/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
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
@JsonTypeName(ManifestStoreType.CUSTOM_REMOTE)
@TypeAlias("CustomRemoteStoreConfig")
@RecasterAlias("io.harness.cdng.manifest.yaml.CustomRemoteStoreConfig")
@FieldNameConstants(innerTypeName = "CustomRepoStoreConfigKeys")
public class CustomRemoteStoreConfig implements StoreConfig {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  private ParameterField<String> extractionScript;

  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  private ParameterField<String> filePath;

  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  @Override
  public String getKind() {
    return ManifestStoreType.CUSTOM_REMOTE;
  }

  @Override
  public StoreConfig cloneInternal() {
    return CustomRemoteStoreConfig.builder().extractionScript(extractionScript).filePath(filePath).build();
  }

  @Override
  public ParameterField<String> getConnectorReference() {
    return null;
  }

  @Override
  public StoreConfig applyOverrides(StoreConfig overrideConfig) {
    CustomRemoteStoreConfig customRemoteStoreConfig = (CustomRemoteStoreConfig) overrideConfig;
    CustomRemoteStoreConfig resultantStoreConfig = this;
    if (!ParameterField.isNull(customRemoteStoreConfig.getExtractionScript())) {
      resultantStoreConfig = resultantStoreConfig.withExtractionScript(customRemoteStoreConfig.getExtractionScript());
    }
    if (!ParameterField.isNull(customRemoteStoreConfig.getFilePath())) {
      resultantStoreConfig = resultantStoreConfig.withFilePath(customRemoteStoreConfig.getFilePath());
    }

    return resultantStoreConfig;
  }

  @Override
  public Set<String> validateAtRuntime() {
    Set<String> invalidParameters = new HashSet<>();
    if (StoreConfigHelper.checkStringParameterNullOrInput(extractionScript)) {
      invalidParameters.add(CustomRepoStoreConfigKeys.extractionScript);
    }
    if (StoreConfigHelper.checkStringParameterNullOrInput(filePath)) {
      invalidParameters.add(CustomRepoStoreConfigKeys.filePath);
    }
    return invalidParameters;
  }
}
