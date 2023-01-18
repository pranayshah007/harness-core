/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml.kinds;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper.StoreConfigWrapperParameters;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.visitor.helpers.manifest.HelmRepoOverrideManifestVisitorHelper;
import io.harness.data.validator.EntityIdentifier;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.SkipAutoEvaluation;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
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

@OwnedBy(CDC)
@Data
@Builder
@FieldNameConstants(innerTypeName = "HelmRepoOverrideManifestKeys")
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(ManifestType.HelmRepoOverride)
@FieldDefaults(level = AccessLevel.PRIVATE)
@SimpleVisitorHelper(helperClass = HelmRepoOverrideManifestVisitorHelper.class)
@TypeAlias("helmRepoOverrideManifest")
@RecasterAlias("io.harness.cdng.manifest.yaml.kinds.HelmRepoOverrideManifest")
public class HelmRepoOverrideManifest implements ManifestAttributes, Visitable {
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
  @Override
  public ManifestAttributes applyOverrides(ManifestAttributes overrideConfig) {
    HelmRepoOverrideManifest helmChartManifest = (HelmRepoOverrideManifest) overrideConfig;
    HelmRepoOverrideManifest resultantManifest = this;
    if (helmChartManifest.getStore() != null && helmChartManifest.getStore().getValue() != null) {
      StoreConfigWrapper storeConfigOverride = helmChartManifest.getStore().getValue();
      resultantManifest = resultantManifest.withStore(
          ParameterField.createValueField(store.getValue().applyOverrides(storeConfigOverride)));
    }

    return resultantManifest;
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();
    children.add(YAMLFieldNameConstants.STORE, store.getValue());
    return children;
  }

  @Override
  public String getKind() {
    return ManifestType.HelmRepoOverride;
  }

  @Override
  public StoreConfig getStoreConfig() {
    return this.store.getValue().getSpec();
  }

  @Override
  public ManifestAttributeStepParameters getManifestAttributeStepParameters() {
    return new HelmRepoOverrideManifestStepParameters(
        identifier, StoreConfigWrapperParameters.fromStoreConfigWrapper(store.getValue()));
  }

  @Value
  public static class HelmRepoOverrideManifestStepParameters implements ManifestAttributeStepParameters {
    String identifier;
    StoreConfigWrapperParameters store;
  }
}
