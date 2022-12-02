/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml.kinds;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper.StoreConfigWrapperParameters;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.visitor.helpers.manifest.AsgConfigurationManifestVisitorHelper;
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

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(ManifestType.AsgScalingPolicy)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "AsgScalingPolicyManifestKeys")
@SimpleVisitorHelper(helperClass = AsgConfigurationManifestVisitorHelper.class)
@TypeAlias("asgScalingPolicyManifest")
@OwnedBy(CDP)
@RecasterAlias("io.harness.cdng.manifest.yaml.kinds.AsgScalingPolicyManifest")
public class AsgScalingPolicyManifest implements ManifestAttributes, Visitable {
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

  // For Visitor Framework Impl
  String metadata;

  @Override
  public ManifestAttributes applyOverrides(ManifestAttributes overrideConfig) {
    AsgScalingPolicyManifest asgScalingPolicyManifest = (AsgScalingPolicyManifest) overrideConfig;
    AsgScalingPolicyManifest resultantManifest = this;
    if (asgScalingPolicyManifest.getStore() != null && asgScalingPolicyManifest.getStore().getValue() != null) {
      resultantManifest = resultantManifest.withStore(ParameterField.createValueField(
          store.getValue().applyOverrides(asgScalingPolicyManifest.getStore().getValue())));
    }
    return resultantManifest;
  }

  @Override
  public String getKind() {
    return ManifestType.AsgScalingPolicy;
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
    return new AsgScalingPolicyStepParameters(
        identifier, StoreConfigWrapperParameters.fromStoreConfigWrapper(store.getValue()));
  }

  @Value
  public static class AsgScalingPolicyStepParameters implements ManifestAttributeStepParameters {
    String identifier;
    StoreConfigWrapperParameters store;
  }
}
