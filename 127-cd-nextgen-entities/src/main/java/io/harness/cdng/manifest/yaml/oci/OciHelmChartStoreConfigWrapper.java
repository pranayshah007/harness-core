/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml.oci;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;
import static java.lang.String.format;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedTypeException;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.validation.Validator;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.intfc.OverridesApplier;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SimpleVisitorHelper(helperClass = OciStoreConfigWrapperVisitorHelper.class)
@TypeAlias("ociHelmChartStoreConfigWrapper")
@RecasterAlias("io.harness.cdng.manifest.yaml.oci.OciHelmChartStoreConfigWrapper")
public class OciHelmChartStoreConfigWrapper implements OverridesApplier<OciHelmChartStoreConfigWrapper>, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull OciHelmChartStoreConfigType type;
  @NotNull
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  @Wither
  OciHelmChartStoreConfig spec;

  @Builder
  public OciHelmChartStoreConfigWrapper(String uuid, OciHelmChartStoreConfigType type, OciHelmChartStoreConfig spec) {
    this.uuid = uuid;
    this.type = type;
    this.spec = spec;
  }

  public void validateParams() {
    Validator.notNullCheck("Type cannot be empty inside Config.", type);
    Validator.notNullCheck("Spec cannot be empty inside Config.", spec);
  }

  @Override
  public OciHelmChartStoreConfigWrapper applyOverrides(OciHelmChartStoreConfigWrapper overrideConfig) {
    OciHelmChartStoreConfigWrapper resultantConfig = this;
    if (overrideConfig != null) {
      if (!overrideConfig.getType().equals(resultantConfig.getType())) {
        throw new UnexpectedTypeException(format("Unable to apply spec override of type '%s' to spec of type '%s'",
            overrideConfig.getType().getDisplayName(), resultantConfig.getType().getDisplayName()));
      }

      resultantConfig = resultantConfig.withSpec(spec.applyOverrides(overrideConfig.getSpec()));
    }
    return resultantConfig;
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();
    children.add(YAMLFieldNameConstants.SPEC, spec);
    return children;
  }

  @Value
  public static class OciHelmChartStoreConfigWrapperParameters {
    String type;
    OciHelmChartStoreConfig spec;

    public static OciHelmChartStoreConfigWrapperParameters fromOciHelmChartStoreConfigWrapper(
        OciHelmChartStoreConfigWrapper storeConfigWrapper) {
      if (storeConfigWrapper == null) {
        return null;
      }

      return new OciHelmChartStoreConfigWrapperParameters(
          storeConfigWrapper.getType() == null ? null : storeConfigWrapper.getType().getDisplayName(),
          storeConfigWrapper.getSpec());
    }
  }
}
