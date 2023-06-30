/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class DocumentationConstants {
  public static final String infrastructureRequestDTO =

      "{\"name\":\"infrastructure\",\"identifier\":\"infrastructureId\",\"description\":\"infrastructure description\",\"tags\":{},\"orgIdentifier\":\"default\",\"projectIdentifier\":\"projectIdentifier\",\"environmentRef\":\"environmentId\",\"deploymentType\":\"Kubernetes\",\"type\":\"KubernetesDirect\",\"yaml\":\"infrastructureDefinition:\\n  name: infrastructure\\n  identifier: infrastructure\\n  description: infrastructure description\\n  tags: {}\\n  orgIdentifier: default\\n  projectIdentifier: projectIdentifier\\n  environmentRef: environmentId\\n  deploymentType: Kubernetes\\n  type: KubernetesDirect\\n  spec:\\n    connectorRef: connectorId\\n    namespace: namespace\\n    releaseName: release-<+INFRA_KEY>\\n  allowSimultaneousDeployments: false\\n\"}";

  public static final String serviceRequestDTO =
      "{\"name\":\"serviceName\",\"identifier\":\"serviceId\",\"tags\":{},\"projectIdentifier\":\"s\",\"orgIdentifier\":\"default\",\"yaml\":\"service:\\n  name: serviceName\\n  identifier: serviceId\\n  tags: {}\\n  serviceDefinition:\\n    spec:\\n      artifacts:\\n        primary:\\n          primaryArtifactRef: artifactName\\n          sources:\\n            - spec:\\n                connectorRef: connectorId\\n                imagePath: imagePath\\n                tag: tagId\\n              identifier: artifactName\\n              type: DockerRegistry\\n    type: Kubernetes\\n\"}";

  public static final String SERVICE_OVERRIDE_V2_REQUEST_DTO =
      "{\"orgIdentifier\":\"defaultOrgId\",\"projectIdentifier\":\"defaultProjId\",\"environmentRef\":\"defaultEnvRef\",\"serviceRef\":\"defaultServiceRef\",\"infraIdentifier\":\"defaultInfraId\",\"type\":\"ENV_SERVICE_OVERRIDE\",\"spec\":{\"variables\":[{\"name\":\"v1\",\"type\":\"String\",\"value\":\"val1\"}],\"manifests\":[{\"manifest\":{\"identifier\":\"manifest1\",\"type\":\"K8sManifest\",\"spec\":{\"store\":{\"type\":\"Github\",\"spec\":{\"connectorRef\":\"abcdConnector\",\"gitFetchType\":\"Branch\",\"paths\":[\"files1\"],\"repoName\":\"abcd\",\"branch\":\"master\"}},\"skipResourceVersioning\":false}}}],\"configFiles\":[{\"configFile\":{\"identifier\":\"configFile1\",\"spec\":{\"store\":{\"type\":\"Harness\",\"spec\":{\"files\":[\"/abcd\"]}}}}}]}}\n";
}
