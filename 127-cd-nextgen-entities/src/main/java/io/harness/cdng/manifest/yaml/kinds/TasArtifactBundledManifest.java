/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml.kinds;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.data.validator.EntityIdentifier;
import io.harness.delegate.task.pcf.artifact.TasArtifactBundledArtifactType;
import io.harness.pcf.model.CfCliVersionNG;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.SkipAutoEvaluation;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PCF})
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(ManifestType.TAS_ARTIFACT_BUNDLED_MANIFEST)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "TasArtifactBundledManifestKeys")
@SimpleVisitorHelper(helperClass = TasArtifactBundledManifestVisitorHelper.class)
@TypeAlias("tasArtifactBundledManifest")
@OwnedBy(CDP)
@RecasterAlias("io.harness.cdng.manifest.yaml.kinds.TasArtifactBundledManifest")
public class TasArtifactBundledManifest implements ManifestAttributes, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;
  @EntityIdentifier String identifier;
  @Wither ParameterField<String> manifestPath;
  @Wither ParameterField<String> artifactPath;
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
  @Wither TasArtifactBundledArtifactType artifactType;

  // For Visitor Framework Impl
  String metadata;

  @Override
  public ManifestAttributes applyOverrides(ManifestAttributes overrideConfig) {
    TasArtifactBundledManifest tasArtifactBundledManifest = (TasArtifactBundledManifest) overrideConfig;
    TasArtifactBundledManifest resultantManifest = this;
    if (!ParameterField.isNull(tasArtifactBundledManifest.getManifestPath())) {
      resultantManifest = resultantManifest.withManifestPath(tasArtifactBundledManifest.getManifestPath());
    }
    if (!ParameterField.isNull(tasArtifactBundledManifest.getArtifactPath())) {
      resultantManifest = resultantManifest.withArtifactPath(tasArtifactBundledManifest.getArtifactPath());
    }
    if (tasArtifactBundledManifest.getCfCliVersion() != null) {
      resultantManifest = resultantManifest.withCfCliVersion(tasArtifactBundledManifest.getCfCliVersion());
    }
    if (!ParameterField.isNull(tasArtifactBundledManifest.getVarsPaths())) {
      resultantManifest = resultantManifest.withVarsPaths(tasArtifactBundledManifest.getVarsPaths());
    }
    if (!ParameterField.isNull(tasArtifactBundledManifest.getAutoScalerPath())) {
      resultantManifest = resultantManifest.withAutoScalerPath(tasArtifactBundledManifest.getAutoScalerPath());
    }
    if (tasArtifactBundledManifest.getArtifactType() != null) {
      resultantManifest = resultantManifest.withArtifactType(tasArtifactBundledManifest.getArtifactType());
    }
    return resultantManifest;
  }

  @Override
  public String getKind() {
    return ManifestType.TAS_ARTIFACT_BUNDLED_MANIFEST;
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    return VisitableChildren.builder().build();
  }

  @Override
  public ManifestAttributeStepParameters getManifestAttributeStepParameters() {
    return new TasArtifactBundledManifestStepParameters(
        identifier, manifestPath, artifactPath, cfCliVersion, varsPaths, autoScalerPath, artifactType);
  }

  @Value
  public static class TasArtifactBundledManifestStepParameters implements ManifestAttributeStepParameters {
    String identifier;
    ParameterField<String> manifestPath;
    ParameterField<String> artifactPath;
    CfCliVersionNG cfCliVersion;
    ParameterField<List<String>> varsPaths;
    ParameterField<List<String>> autoScalerPath;
    TasArtifactBundledArtifactType artifactType;
  }
}
