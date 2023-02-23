package io.harness.ng.core.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class Constants {
  public static final String infrastructureRequestDTO =

      "{\"name\":\"infrastructure\",\"identifier\":\"infrastructureId\",\"description\":\"infrastructure description\",\"tags\":{},\"orgIdentifier\":\"default\",\"projectIdentifier\":\"projectIdentifier\",\"environmentRef\":\"environmentId\",\"deploymentType\":\"Kubernetes\",\"type\":\"KubernetesDirect\",\"yaml\":\"infrastructureDefinition:\\n  name: infrastructure\\n  identifier: infrastructure\\n  description: infrastructure description\\n  tags: {}\\n  orgIdentifier: default\\n  projectIdentifier: projectIdentifier\\n  environmentRef: environmentId\\n  deploymentType: Kubernetes\\n  type: KubernetesDirect\\n  spec:\\n    connectorRef: connectorId\\n    namespace: namespace\\n    releaseName: release-<+INFRA_KEY>\\n  allowSimultaneousDeployments: false\\n\"}";
}