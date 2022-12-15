/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml.kinds;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;
import static io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper.StoreConfigWrapperParameters;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.data.validator.EntityIdentifier;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.SkipAutoEvaluation;
import io.harness.pms.yaml.YamlNode;

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
@JsonTypeName(ManifestType.DeploymentRepo)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "GitOpsDeploymentRepoManifestKeys")
@TypeAlias("deploymentRepoManifest")
@OwnedBy(GITOPS)
@RecasterAlias("io.harness.cdng.manifest.yaml.kinds.GitOpsDeploymentRepoManifest")
public class GitOpsDeploymentRepoManifest implements ManifestAttributes {
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
    GitOpsDeploymentRepoManifest deploymentRepoManifest = (GitOpsDeploymentRepoManifest) overrideConfig;
    GitOpsDeploymentRepoManifest resultantManifest = this;
    if (deploymentRepoManifest.getStore() != null && deploymentRepoManifest.getStore().getValue() != null) {
      resultantManifest = resultantManifest.withStore(ParameterField.createValueField(
          store.getValue().applyOverrides(deploymentRepoManifest.getStore().getValue())));
    }

    return resultantManifest;
  }

  @Override
  public String getKind() {
    return ManifestType.DeploymentRepo;
  }

  @Override
  public StoreConfig getStoreConfig() {
    return store.getValue().getSpec();
  }

  @Override
  public ManifestAttributeStepParameters getManifestAttributeStepParameters() {
    return new DeploymentRepoManifestStepParameters(
        identifier, StoreConfigWrapperParameters.fromStoreConfigWrapper(store.getValue()));
  }

  @Value
  public static class DeploymentRepoManifestStepParameters implements ManifestAttributeStepParameters {
    String identifier;
    StoreConfigWrapperParameters store;
  }
}
