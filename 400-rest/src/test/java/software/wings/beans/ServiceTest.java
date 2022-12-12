/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static software.wings.persistence.AppContainer.Builder.anAppContainer;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.k8s.model.HelmVersion;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentType;
import software.wings.utils.ArtifactType;
import software.wings.utils.WingsTestConstants;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceTest extends WingsBaseTest {
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void cloneInternal() {
    Service srcService = Service.builder()
                             .appId(WingsTestConstants.APP_ID)
                             .name("service-1")
                             .accountId(WingsTestConstants.ACCOUNT_ID)
                             .deploymentTypeTemplateId(WingsTestConstants.TEMPLATE_ID)
                             .customDeploymentName("weblogic")
                             .deploymentType(DeploymentType.CUSTOM)
                             .helmVersion(HelmVersion.V2)
                             .artifactType(ArtifactType.JAR)
                             .description("desc")
                             .configMapYaml("env: prod")
                             .appContainer(anAppContainer().build())
                             .helmValueYaml("replicas: 1")
                             .isK8sV2(true)
                             .isPcfV2(true)
                             .build();

    Service clonedService = srcService.cloneInternal();

    assertThat(clonedService.getAppId()).isEqualTo(WingsTestConstants.APP_ID);
    assertThat(clonedService.getName()).isEqualTo("service-1");
    assertThat(clonedService.getAccountId()).isEqualTo(WingsTestConstants.ACCOUNT_ID);
    assertThat(clonedService.getDeploymentTypeTemplateId()).isEqualTo(WingsTestConstants.TEMPLATE_ID);
    assertThat(clonedService.getCustomDeploymentName()).isEqualTo("weblogic");
    assertThat(clonedService.getDeploymentType()).isEqualTo(DeploymentType.CUSTOM);
    assertThat(clonedService.getHelmVersion()).isEqualTo(HelmVersion.V2);
    assertThat(clonedService.getArtifactType()).isEqualTo(ArtifactType.JAR);
    assertThat(clonedService.getDescription()).isEqualTo("desc");
    assertThat(clonedService.getConfigMapYaml()).isEqualTo("env: prod");
    assertThat(clonedService.getHelmValueYaml()).isEqualTo("replicas: 1");
    assertThat(clonedService.getAppContainer()).isEqualTo(anAppContainer().build());
    assertThat(clonedService.isK8sV2()).isTrue();
    assertThat(clonedService.isPcfV2()).isTrue();
  }
}
