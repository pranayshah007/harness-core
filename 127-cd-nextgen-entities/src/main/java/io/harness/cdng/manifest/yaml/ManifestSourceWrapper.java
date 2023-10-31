/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.cdng.manifest.yaml.ManifestAttributes.ManifestAttributeStepParameters;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.visitor.helpers.manifest.ManifestSourceWrapperConfigVisitorHelper;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
@Data
@NoArgsConstructor
@SimpleVisitorHelper(helperClass = ManifestSourceWrapperConfigVisitorHelper.class)
@TypeAlias("manifestSourceWrapper")
@RecasterAlias("io.harness.cdng.manifest.yaml.ManifestSourceWrapper")
public class ManifestSourceWrapper implements Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull @JsonProperty("type") @ApiModelProperty(allowableValues = ManifestType.K8Manifest) ManifestConfigType type;

  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  private ManifestAttributes spec;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  public void setSpec(ManifestAttributes spec) {
    this.spec = spec;
  }

  // Use Builder as Constructor then only external property(visible) will be filled.
  @Builder
  public ManifestSourceWrapper(String uuid, ManifestConfigType type, ManifestAttributes spec, List<String> filePaths) {
    this.uuid = uuid;
    this.type = type;
    this.spec = spec;
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();
    children.add(YAMLFieldNameConstants.SPEC, spec);
    return children;
  }

  @Value
  public static class ManifestSourceStepParameters {
    String type;
    ManifestAttributeStepParameters spec;

    public static ManifestSourceStepParameters fromManifestStore(ManifestSourceWrapper manifestSourceWrapper) {
      if (manifestSourceWrapper == null) {
        return null;
      }
      return new ManifestSourceStepParameters(
          manifestSourceWrapper.getType() == null ? null : manifestSourceWrapper.getType().getDisplayName(),
          manifestSourceWrapper.getSpec().getManifestAttributeStepParameters());
    }
  }
}
