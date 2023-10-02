/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.artifact.DockerRegistrySpec;
import io.harness.ngtriggers.beans.source.artifact.EcrSpec;
import io.harness.ngtriggers.beans.source.artifact.NexusRegistrySpec;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class ArtifactConfigHelperTest extends CategoryTest {
  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testSetConnectorAndImagePath() {
    String ecrImage = "ecrImage";
    String dockerImage = "dockerImage";
    String nexusImage = "nexusImage";

    String connectorRef = "connectorRef";

    EcrSpec ecrSpec = EcrSpec.builder().imagePath(ecrImage).connectorRef(ecrImage + connectorRef).build();
    DockerRegistrySpec dockerSpec =
        DockerRegistrySpec.builder().imagePath(dockerImage).connectorRef(dockerImage + connectorRef).build();
    NexusRegistrySpec nexusSpec =
        NexusRegistrySpec.builder().imagePath(nexusImage).connectorRef(nexusImage + connectorRef).build();
    ArtifactTriggerConfig artifactTriggerConfig = ArtifactTriggerConfig.builder().spec(ecrSpec).build();

    TriggerPayload.Builder triggerPayload = TriggerPayload.newBuilder();

    ArtifactConfigHelper.setConnectorAndImage(triggerPayload, artifactTriggerConfig);
    assertThat(triggerPayload.getImagePath()).isEqualTo(ecrImage);
    assertThat(triggerPayload.getConnectorRef()).isEqualTo(ecrImage + connectorRef);

    artifactTriggerConfig = ArtifactTriggerConfig.builder().spec(dockerSpec).build();
    ArtifactConfigHelper.setConnectorAndImage(triggerPayload, artifactTriggerConfig);
    assertThat(triggerPayload.getImagePath()).isEqualTo(dockerImage);
    assertThat(triggerPayload.getConnectorRef()).isEqualTo(dockerImage + connectorRef);

    artifactTriggerConfig = ArtifactTriggerConfig.builder().spec(nexusSpec).build();
    ArtifactConfigHelper.setConnectorAndImage(triggerPayload, artifactTriggerConfig);
    assertThat(triggerPayload.getImagePath()).isEqualTo(nexusImage);
    assertThat(triggerPayload.getConnectorRef()).isEqualTo(nexusImage + connectorRef);
  }
}
