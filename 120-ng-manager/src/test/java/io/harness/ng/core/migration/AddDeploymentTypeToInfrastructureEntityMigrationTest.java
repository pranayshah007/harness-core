/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.rule.OwnerRule.INDER;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.NgManagerTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.migration.background.AddDeploymentTypeToInfrastructureEntityMigration;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(HarnessTeam.CDC)
public class AddDeploymentTypeToInfrastructureEntityMigrationTest extends NgManagerTestBase {
  @InjectMocks AddDeploymentTypeToInfrastructureEntityMigration migration;

  private static final String yaml = "infrastructureDefinition:\n"
      + "    name: IDENTIFIER1\n"
      + "    identifier: IDENTIFIER1\n"
      + "    description: 1st infra\n"
      + "    tags: {}\n"
      + "    type: KubernetesDirect\n"
      + "    spec:\n"
      + "        connectorRef: k8s_master_url\n"
      + "        namespace: default\n"
      + "        releaseName: release-<+INFRA_KEY>\n"
      + "    orgIdentifier: ORG_ID\n"
      + "    projectIdentifier: PROJECT_ID\n"
      + "    envIdentifier: ENV_IDENTIFIER\n";

  private static final String expectedYaml = "infrastructureDefinition:\n"
      + "  name: IDENTIFIER1\n"
      + "  identifier: IDENTIFIER1\n"
      + "  description: 1st infra\n"
      + "  tags: {}\n"
      + "  type: KubernetesDirect\n"
      + "  spec:\n"
      + "    connectorRef: k8s_master_url\n"
      + "    namespace: default\n"
      + "    releaseName: release-<+INFRA_KEY>\n"
      + "  orgIdentifier: ORG_ID\n"
      + "  projectIdentifier: PROJECT_ID\n"
      + "  envIdentifier: ENV_IDENTIFIER\n"
      + "  deploymentType: Kubernetes\n";

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testAddDeploymentTypeToYaml() {
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder().yaml(yaml).build();

    String modifiedYaml = migration.addDeploymentTypeToYaml(infrastructureEntity, ServiceDefinitionType.KUBERNETES);
    assertThat(modifiedYaml).isNotNull().isNotEmpty();
    assertThat(modifiedYaml).isEqualTo(expectedYaml);
  }
}
