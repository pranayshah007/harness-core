/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.visitor.helpers.SecretConnectorRefExtractorHelper;
import io.harness.delegate.task.pcf.artifact.TasArtifactBundledArtifactType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashSet;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PCF})
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(ManifestStoreType.ARTIFACT_BUNDLE)
@SimpleVisitorHelper(helperClass = SecretConnectorRefExtractorHelper.class)
@TypeAlias("artifactBundleStore")
@OwnedBy(CDP)
@RecasterAlias("io.harness.cdng.manifest.yaml.ArtifactBundleStore")
@FieldNameConstants(innerTypeName = "ArtifactBundleStoreConfigKeys")
public class ArtifactBundleStore implements ArtifactBundleStoreConfig, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  private ParameterField<String> manifestPath;

  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  private ParameterField<String> deployableUnitPath; // it's the artifact deployable unit

  @NotNull @Wither private TasArtifactBundledArtifactType artifactBundleType;
  // For Visitor Framework Impl
  @Override
  public String getKind() {
    return ManifestStoreType.ARTIFACT_BUNDLE;
  }

  public ArtifactBundleStore cloneInternal() {
    return ArtifactBundleStore.builder()
        .manifestPath(manifestPath)
        .deployableUnitPath(deployableUnitPath)
        .artifactBundleType(artifactBundleType)
        .build();
  }

  @Override
  public StoreConfig applyOverrides(StoreConfig overrideConfig) {
    ArtifactBundleStore gitStore = (ArtifactBundleStore) overrideConfig;
    ArtifactBundleStore resultantGitStore = this;
    if (!ParameterField.isNull(gitStore.getDeployableUnitPath())) {
      resultantGitStore = resultantGitStore.withDeployableUnitPath(gitStore.getDeployableUnitPath());
    }
    if (!ParameterField.isNull(gitStore.getManifestPath())) {
      resultantGitStore = resultantGitStore.withManifestPath(gitStore.getManifestPath());
    }
    if (gitStore.getArtifactBundleType() != null) {
      resultantGitStore = resultantGitStore.withArtifactBundleType(gitStore.getArtifactBundleType());
    }
    return resultantGitStore;
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    return VisitableChildren.builder().build();
  }

  @Override
  public Set<String> validateAtRuntime() {
    Set<String> invalidParameters = new HashSet<>();
    if (StoreConfigHelper.checkStringParameterNullOrInput(manifestPath)) {
      invalidParameters.add(ArtifactBundleStoreConfigKeys.manifestPath);
    }
    if (StoreConfigHelper.checkStringParameterNullOrInput(deployableUnitPath)) {
      invalidParameters.add(ArtifactBundleStoreConfigKeys.deployableUnitPath);
    }
    if (artifactBundleType == null
        || (!artifactBundleType.equals(TasArtifactBundledArtifactType.ZIP)
            && !artifactBundleType.equals(TasArtifactBundledArtifactType.TAR))) {
      invalidParameters.add(ArtifactBundleStoreConfigKeys.artifactBundleType);
    }

    return invalidParameters;
  }
}
