/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.visitor.SimpleVisitorHelper;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(ManifestStoreType.INLINE)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("inlineStore")
@RecasterAlias("io.harness.cdng.manifest.yaml.InlineStoreConfig")
public class InlineStoreConfig implements StoreConfig {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  private ParameterField<String> content;

  @Override
  public String getKind() {
    return ManifestStoreType.INLINE;
  }

  @Override
  public StoreConfig cloneInternal() {
    return InlineStoreConfig.builder().content(content).build();
  }

  @Override
  public ParameterField<String> getConnectorReference() {
    return null;
  }

  @Override
  public StoreConfig applyOverrides(StoreConfig overrideConfig) {
    InlineStoreConfig inlineStoreConfig = (InlineStoreConfig) overrideConfig;
    InlineStoreConfig resultantInlineStore = this;

    if (!ParameterField.isNull(inlineStoreConfig.getContent())) {
      resultantInlineStore = resultantInlineStore.withContent(inlineStoreConfig.getContent());
    }

    return resultantInlineStore;
  }

  public String extractContent() {
    return this.content.getValue() == null ? this.content.getExpressionValue() : this.content.getValue();
  }
}
