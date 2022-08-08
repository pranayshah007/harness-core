/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.artifact;

import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.beans.artifact.Artifact.Builder.anArtifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.within;

import io.harness.beans.ArtifactMetadata;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.beans.Service;
import software.wings.beans.User;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.BambooArtifactStream;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.schema.query.QLArtifactQueryParameters;
import software.wings.graphql.schema.type.artifact.QLArtifact;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ServiceResourceService;

import com.google.inject.Inject;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ArtifactDataFetcherTest extends AbstractDataFetcherTestBase {
  public static final String ARTIFACT_ID = "ARTIFACT_ID";
  @Inject ArtifactDataFetcher artifactDataFetcher;
  @Inject ArtifactService artifactService;
  @Inject ServiceResourceService serviceResourceService;
  @Inject ArtifactStreamService artifactStreamService;
  @Before
  public void setup() throws SQLException {
    User user = testUtils.createUser(testUtils.createAccount());
    UserThreadLocal.set(user);

    // Account1
    createAccount(ACCOUNT1_ID, getLicenseInfo());
    createApp(ACCOUNT1_ID, APP1_ID_ACCOUNT1, APP1_ID_ACCOUNT1, TAG_TEAM, TAG_VALUE_TEAM1);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testArtifactDataFetcher() {
    Service service = createService(
        ACCOUNT1_ID, APP1_ID_ACCOUNT1, SERVICE1_ID_APP1_ACCOUNT1, "SERVICE_NAME", TAG_TEAM, TAG_VALUE_TEAM1);
    service.setArtifactStreamIds(Arrays.asList(ARTIFACT_STREAM_ID_1));
    serviceResourceService.save(service);
    ArtifactStream artifactStream = BambooArtifactStream.builder()
                                        .appId(APP1_ID_ACCOUNT1)
                                        .autoPopulate(true)
                                        .jobname("JOB_NAME")
                                        .uuid(ARTIFACT_STREAM_ID_1)
                                        .serviceId(SERVICE1_ID_APP1_ACCOUNT1)
                                        .metadataOnly(true)
                                        .build();
    artifactStreamService.create(artifactStream);
    Artifact artifact =
        anArtifact()
            .withAppId(APP1_ID_ACCOUNT1)
            .withUuid(ARTIFACT_ID)
            .withMetadata(new ArtifactMetadata(Collections.singletonMap(ArtifactMetadataKeys.buildNo, "1")))
            .withArtifactStreamId(ARTIFACT_STREAM_ID_1)
            .withDisplayName("ARTIFACT")
            .withArtifactStreamType("BAMBOO")
            .build();
    artifactService.create(artifact);

    QLArtifact qlArtifact =
        artifactDataFetcher.fetch(QLArtifactQueryParameters.builder().artifactId(ARTIFACT_ID).build(), ACCOUNT1_ID);
    assertThat(qlArtifact).isNotNull();
    assertThat(qlArtifact.getBuildNo()).isEqualTo("1");
    assertThat(qlArtifact.getCollectedAt()).isCloseTo(System.currentTimeMillis(), within(60000L));
    assertThat(qlArtifact.getArtifactSourceId()).isEqualTo(ARTIFACT_STREAM_ID_1);

    qlArtifact =
        artifactDataFetcher.fetch(QLArtifactQueryParameters.builder().artifactId(ARTIFACT_ID + 2).build(), ACCOUNT1_ID);
    assertThat(qlArtifact).isNull();

    try {
      artifactDataFetcher.fetch(QLArtifactQueryParameters.builder().artifactId(ARTIFACT_ID).build(), ACCOUNT2_ID);
      fail("InvalidRequestException expected here");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(InvalidRequestException.class);
    }
  }
}
