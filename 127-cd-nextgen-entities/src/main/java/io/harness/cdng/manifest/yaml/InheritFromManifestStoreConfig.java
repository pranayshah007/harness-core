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
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(ManifestStoreType.InheritFromManifest)
@TypeAlias("InheritFromManifestStoreConfig")
@RecasterAlias("io.harness.cdng.manifest.yaml.InheritFromManifestStoreConfig")
@FieldNameConstants(innerTypeName = "InheritFromManifestStoreConfigKeys")
public class InheritFromManifestStoreConfig implements StoreConfig {
  @Wither
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @YamlSchemaTypes(value = {runtime})
  private ParameterField<List<String>> paths;

  @Override
  public String getKind() {
    return ManifestStoreType.InheritFromManifest;
  }

  @Override
  public StoreConfig cloneInternal() {
    return InheritFromManifestStoreConfig.builder().paths(paths).build();
  }

  @Override
  public ParameterField<String> getConnectorReference() {
    return null;
  }

  public ParameterField<List<String>> getPaths() {
    return paths;
  }

  public StoreConfig applyOverrides(StoreConfig overrideConfig) {
    InheritFromManifestStoreConfig inheritFromManifestStoreConfig = (InheritFromManifestStoreConfig) overrideConfig;
    InheritFromManifestStoreConfig resultantInheritFromManifestStoreConfig = this;
    if (!ParameterField.isNull(inheritFromManifestStoreConfig.getPaths())) {
      resultantInheritFromManifestStoreConfig =
          resultantInheritFromManifestStoreConfig.withPaths(inheritFromManifestStoreConfig.getPaths());
    }

    return resultantInheritFromManifestStoreConfig;
  }

  @Override
  public Set<String> validateAtRuntime() {
    Set<String> invalidParameters = new HashSet<>();
    if (StoreConfigHelper.checkListOfStringsParameterNullOrInput(paths)) {
      invalidParameters.add(InheritFromManifestStoreConfigKeys.paths);
    }
    return invalidParameters;
  }
}
