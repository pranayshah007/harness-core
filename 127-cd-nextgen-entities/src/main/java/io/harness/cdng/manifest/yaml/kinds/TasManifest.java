/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml.kinds;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper.StoreConfigWrapperParameters;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.visitor.helpers.manifest.TasManifestVisitorHelper;
import io.harness.data.validator.EntityIdentifier;
import io.harness.pcf.model.CfCliVersionNG;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.SkipAutoEvaluation;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(ManifestType.TAS_MANIFEST)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "TasManifestKeys")
@SimpleVisitorHelper(helperClass = TasManifestVisitorHelper.class)
@TypeAlias("tasManifest")
@OwnedBy(CDP)
@RecasterAlias("io.harness.cdng.manifest.yaml.kinds.TasManifest")
public class TasManifest implements ManifestAttributes, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;
  @EntityIdentifier String identifier;
  @Wither
  @JsonProperty("store")
  @ApiModelProperty(dataType = "io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper")
  @SkipAutoEvaluation
  ParameterField<StoreConfigWrapper> store;
  @Wither CfCliVersionNG cfCliVersion;
  @Wither
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @YamlSchemaTypes({runtime})
  @SkipAutoEvaluation
  ParameterField<List<String>> varsPaths;
  @Wither
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @YamlSchemaTypes({runtime})
  @SkipAutoEvaluation
  ParameterField<List<String>> autoScalerPath;
  // For Visitor Framework Impl
  String metadata;

  @Override
  public ManifestAttributes applyOverrides(ManifestAttributes overrideConfig) {
    TasManifest tasManifest = (TasManifest) overrideConfig;
    TasManifest resultantManifest = this;
    if (tasManifest.getStore() != null && tasManifest.getStore().getValue() != null) {
      resultantManifest = resultantManifest.withStore(
          ParameterField.createValueField(store.getValue().applyOverrides(tasManifest.getStore().getValue())));
    }
    if (tasManifest.getCfCliVersion() != null) {
      resultantManifest = resultantManifest.withCfCliVersion(tasManifest.getCfCliVersion());
    }
    if (!ParameterField.isNull(tasManifest.getVarsPaths())) {
      resultantManifest = resultantManifest.withVarsPaths(tasManifest.getVarsPaths());
    }
    if (!ParameterField.isNull(tasManifest.getAutoScalerPath())) {
      resultantManifest = resultantManifest.withAutoScalerPath(tasManifest.getAutoScalerPath());
    }
    return resultantManifest;
  }

  @Override
  public String getKind() {
    return ManifestType.TAS_MANIFEST;
  }

  @Override
  public StoreConfig getStoreConfig() {
    return store.getValue().getSpec();
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();
    children.add(YAMLFieldNameConstants.STORE, store.getValue());
    return children;
  }

  @Override
  public ManifestAttributeStepParameters getManifestAttributeStepParameters() {
    return new TasManifestStepParameters(identifier,
        StoreConfigWrapperParameters.fromStoreConfigWrapper(store.getValue()), cfCliVersion, varsPaths, autoScalerPath);
  }

  @Value
  public static class TasManifestStepParameters implements ManifestAttributeStepParameters {
    String identifier;
    StoreConfigWrapperParameters store;
    CfCliVersionNG cfCliVersion;
    ParameterField<List<String>> varsPaths;
    ParameterField<List<String>> autoScalerPath;
  }
}
