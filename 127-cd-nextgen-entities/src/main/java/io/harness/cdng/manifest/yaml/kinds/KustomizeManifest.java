/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml.kinds;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;
import static io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper.StoreConfigWrapperParameters;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.bool;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.kinds.kustomize.OverlayConfiguration;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.visitor.helpers.manifest.KustomizeManifestVisitorHelper;
import io.harness.data.validator.EntityIdentifier;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.SkipAutoEvaluation;
import io.harness.pms.yaml.YamlNode;
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
@JsonTypeName(ManifestType.Kustomize)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "KustomizeManifestKeys")
@SimpleVisitorHelper(helperClass = KustomizeManifestVisitorHelper.class)
@TypeAlias("kustomizeManifest")
@OwnedBy(CDC)
@RecasterAlias("io.harness.cdng.manifest.yaml.kinds.KustomizeManifest")
public class KustomizeManifest implements ManifestAttributes, Visitable {
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

  @Wither
  @JsonProperty("overlayConfiguration")
  @ApiModelProperty(dataType = "io.harness.cdng.manifest.yaml.kinds.kustomize.OverlayConfiguration")
  @SkipAutoEvaluation
  ParameterField<OverlayConfiguration> overlayConfiguration;

  @Wither
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @YamlSchemaTypes({runtime})
  @SkipAutoEvaluation
  @JsonProperty("patchesPaths")
  ParameterField<List<String>> patchesPaths;

  @Wither @YamlSchemaTypes({string, bool}) @SkipAutoEvaluation ParameterField<Boolean> skipResourceVersioning;
  @Wither @YamlSchemaTypes({string, bool}) @SkipAutoEvaluation ParameterField<Boolean> enableDeclarativeRollback;
  @Wither @ApiModelProperty(dataType = STRING_CLASSPATH) @SkipAutoEvaluation ParameterField<String> pluginPath;
  @Wither List<KustomizeManifestCommandFlag> commandFlags;

  @Override
  public String getKind() {
    return ManifestType.Kustomize;
  }

  @Override
  public StoreConfig getStoreConfig() {
    return this.store.getValue().getSpec();
  }

  @Override
  public ManifestAttributes applyOverrides(ManifestAttributes overrideConfig) {
    KustomizeManifest kustomizeManifest = (KustomizeManifest) overrideConfig;
    KustomizeManifest resultantManifest = this;
    if (kustomizeManifest.getStore() != null && kustomizeManifest.getStore().getValue() != null) {
      StoreConfigWrapper storeConfigOverride = kustomizeManifest.getStore().getValue();
      resultantManifest = resultantManifest.withStore(
          ParameterField.createValueField(store.getValue().applyOverrides(storeConfigOverride)));
    }
    if (!ParameterField.isNull(kustomizeManifest.getPatchesPaths())) {
      resultantManifest = resultantManifest.withPatchesPaths(kustomizeManifest.getPatchesPaths());
    }
    if (kustomizeManifest.getSkipResourceVersioning() != null) {
      resultantManifest = resultantManifest.withSkipResourceVersioning(kustomizeManifest.getSkipResourceVersioning());
    }

    if (kustomizeManifest.getPluginPath() != null) {
      resultantManifest = resultantManifest.withPluginPath(kustomizeManifest.getPluginPath());
    }

    if (kustomizeManifest.getOverlayConfiguration() != null) {
      resultantManifest = resultantManifest.withOverlayConfiguration(kustomizeManifest.getOverlayConfiguration());
    }
    if (kustomizeManifest.getEnableDeclarativeRollback() != null) {
      resultantManifest =
          resultantManifest.withEnableDeclarativeRollback(kustomizeManifest.getEnableDeclarativeRollback());
    }

    return resultantManifest;
  }

  @Override
  public ManifestAttributeStepParameters getManifestAttributeStepParameters() {
    return new KustomizeManifestStepParameters(identifier,
        StoreConfigWrapperParameters.fromStoreConfigWrapper(store.getValue()), skipResourceVersioning, pluginPath,
        patchesPaths, enableDeclarativeRollback);
  }

  @Value
  public static class KustomizeManifestStepParameters implements ManifestAttributeStepParameters {
    String identifier;
    StoreConfigWrapperParameters store;
    ParameterField<Boolean> skipResourceVersioning;
    ParameterField<String> pluginPath;
    ParameterField<List<String>> patchesPaths;
    ParameterField<Boolean> enableDeclarativeRollback;
  }
}
