package io.harness.cdng.service.beans;

import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactOverrideSetWrapper;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.ManifestOverrideSetWrapper;
import io.harness.cdng.service.ServiceSpec;
import io.harness.cdng.variables.beans.NGVariableOverrideSetWrapper;
import io.harness.cdng.visitor.helpers.serviceconfig.NativeHelmServiceSpecVisitorHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.SkipAutoEvaluation;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.variables.NGVariable;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@JsonTypeName(ServiceSpecType.NATIVE_HELM)
@SimpleVisitorHelper(helperClass = NativeHelmServiceSpecVisitorHelper.class)
@TypeAlias("nativeHelmServiceSpec")
public class NativeHelmServiceSpec implements ServiceSpec, Visitable {
  List<NGVariable> variables;
  ArtifactListConfig artifacts;
  @ApiModelProperty(dataType = "[Lio.harness.cdng.manifest.yaml.ManifestConfigWrapper;")
  @SkipAutoEvaluation
  ParameterField<List<ManifestConfigWrapper>> manifests;

  List<NGVariableOverrideSetWrapper> variableOverrideSets;
  List<ArtifactOverrideSetWrapper> artifactOverrideSets;

  @ApiModelProperty(dataType = "[Lio.harness.cdng.manifest.yaml.ManifestOverrideSetWrapper;")
  @SkipAutoEvaluation
  ParameterField<List<ManifestOverrideSetWrapper>> manifestOverrideSets;

  // For Visitor Framework Impl
  String metadata;
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
    if (EmptyPredicate.isNotEmpty(artifactOverrideSets)) {
      artifactOverrideSets.forEach(artifactOverrideSet -> children.add("artifactOverrideSets", artifactOverrideSet));
    }
    return children;
  }
}
