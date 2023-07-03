/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service.beans;

import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.hooks.ServiceHookWrapper;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifestConfigs.ManifestConfigurations;
import io.harness.cdng.service.ServiceSpec;
import io.harness.cdng.visitor.helpers.serviceconfig.NativeHelmServiceSpecVisitorHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.variables.NGVariable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@JsonTypeName(ServiceSpecType.NATIVE_HELM)
@SimpleVisitorHelper(helperClass = NativeHelmServiceSpecVisitorHelper.class)
@TypeAlias("nativeHelmServiceSpec")
public class NativeHelmServiceSpec implements ServiceSpec, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  List<NGVariable> variables;
  ArtifactListConfig artifacts;
  List<ManifestConfigWrapper> manifests;
  List<ConfigFileWrapper> configFiles;
  List<ServiceHookWrapper> hooks;
  ManifestConfigurations manifestConfigurations;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;
  @Override
  public String getType() {
    return ServiceSpecType.NATIVE_HELM;
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();
    if (EmptyPredicate.isNotEmpty(variables)) {
      variables.forEach(ngVariable -> children.add("variables", ngVariable));
    }

    children.add("artifacts", artifacts);
    if (EmptyPredicate.isNotEmpty(manifests)) {
      manifests.forEach(manifest -> children.add("manifests", manifest));
    }

    if (EmptyPredicate.isNotEmpty(configFiles)) {
      configFiles.forEach(configFile -> children.add("configFiles", configFile));
    }

    if (EmptyPredicate.isNotEmpty(hooks)) {
      hooks.forEach(hook -> children.add("hooks", hook));
    }

    children.add("manifestConfigurations", manifestConfigurations);

    return children;
  }
}
