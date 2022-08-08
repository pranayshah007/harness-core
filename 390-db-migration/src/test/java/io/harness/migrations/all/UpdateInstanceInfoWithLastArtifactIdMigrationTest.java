/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.rule.OwnerRule.ANSHUL;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.artifact.Artifact.Builder.anArtifact;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_NAME;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.ArtifactMetadata;
import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.info.K8sContainerInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class UpdateInstanceInfoWithLastArtifactIdMigrationTest extends WingsBaseTest {
  @Inject private HPersistence persistence;

  @InjectMocks @Inject private UpdateInstanceInfoWithLastArtifactIdMigration instanceInfoMigration;

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testMigrate() {
    Account account = anAccount().withAccountName(ACCOUNT_NAME).withUuid(ACCOUNT_ID).build();
    persistence.save(account);

    Application application = anApplication().accountId(ACCOUNT_ID).uuid(APP_ID).appId(APP_ID).build();
    persistence.save(application);

    Instance instance1 = Instance.builder()
                             .uuid("instance1")
                             .lastArtifactStreamId(ARTIFACT_STREAM_ID + "1")
                             .appId(APP_ID)
                             .accountId(ACCOUNT_ID)
                             .build();
    Instance instance2 = Instance.builder()
                             .uuid("instance2")
                             .lastArtifactStreamId(ARTIFACT_STREAM_ID + "2")
                             .appId(APP_ID)
                             .accountId(ACCOUNT_ID)
                             .build();
    instance2.setInstanceInfo(K8sPodInfo.builder()
                                  .containers(Arrays.asList(K8sContainerInfo.builder().image("image:latest").build()))
                                  .build());

    Instance instance3 = Instance.builder()
                             .uuid("instance3")
                             .lastArtifactStreamId(ARTIFACT_STREAM_ID + "3")
                             .appId(APP_ID)
                             .accountId(ACCOUNT_ID)
                             .isDeleted(true)
                             .build();

    persistence.save(instance1);
    persistence.save(instance2);
    persistence.save(instance3);

    Artifact artifact =
        anArtifact().withUuid(ARTIFACT_ID).withAppId(APP_ID).withArtifactStreamId(ARTIFACT_STREAM_ID + "2").build();
    persistence.save(artifact);

    instanceInfoMigration.migrate();
    Instance instance = persistence.get(Instance.class, "instance2");
    assertThat(instance).isNotNull();
    assertThat(instance.getLastArtifactId()).isNull();

    Map<String, String> metadata = new HashMap<>();
    metadata.put("image", "image:latest");
    artifact.setMetadata(new ArtifactMetadata(metadata));
    persistence.save(artifact);

    instanceInfoMigration.migrate();
    instance = persistence.get(Instance.class, "instance2");
    assertThat(instance).isNotNull();
    assertThat(instance.getLastArtifactId()).isEqualTo(ARTIFACT_ID);
  }
}
