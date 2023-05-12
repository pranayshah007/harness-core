/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.INDER;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_SOURCE_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.artifact.AmiArtifactStream;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.beans.artifact.CustomArtifactStream.Action;
import software.wings.beans.artifact.JenkinsArtifactStream;
import software.wings.dl.WingsPersistence;
import software.wings.persistence.artifact.Artifact;

import com.google.inject.Inject;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
public class ArtifactServiceImplTest extends WingsBaseTest {
  @Mock private WingsPersistence mockWingsPersistence;
  @Inject @InjectMocks ArtifactServiceImpl artifactService;

  private AmiArtifactStream artifactStream = new AmiArtifactStream();

  private JenkinsArtifactStream jenkinsArtifactStream = JenkinsArtifactStream.builder()
                                                            .appId(APP_ID)
                                                            .uuid(ARTIFACT_STREAM_ID)
                                                            .sourceName(ARTIFACT_SOURCE_NAME)
                                                            .settingId(SETTING_ID)
                                                            .jobname("JOB")
                                                            .serviceId(SERVICE_ID)
                                                            .artifactPaths(asList("*WAR"))
                                                            .build();

  ArtifactStream customArtifactStream =
      CustomArtifactStream.builder()
          .accountId(ACCOUNT_ID)
          .appId(APP_ID)
          .serviceId(SERVICE_ID)
          .name("Custom Artifact Stream" + System.currentTimeMillis())
          .scripts(asList(CustomArtifactStream.Script.builder()
                              .action(Action.FETCH_VERSIONS)
                              .scriptString("echo Hello World!! and echo ${secrets.getValue(My Secret)}")
                              .timeout("60")
                              .build()))
          .build();

  @Before
  public void setUp() throws Exception {
    Query mockQuery = mock(Query.class);
    doReturn(mockQuery).when(mockWingsPersistence).createQuery(any(), any());
    doReturn(mockQuery).when(mockWingsPersistence).createQuery(any());
    doReturn(mockQuery).when(mockQuery).filter(any(), any());
    doReturn(mockQuery).when(mockQuery).project(any(), anyBoolean());
    doReturn(mockQuery).when(mockQuery).disableValidation();
    FieldEnd mockFieldEnd = mock(FieldEnd.class);
    doReturn(mockFieldEnd).when(mockQuery).field(any());
    doReturn(mockQuery).when(mockFieldEnd).in(any());
    doReturn(mockQuery).when(mockFieldEnd).hasAnyOf(any());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldFetchAMIBuilds() {
    artifactStream.setRegion("TestRegion");

    assertThat(artifactService.prepareArtifactWithMetadataQuery(artifactStream, false)).isNotNull();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldFetchNonAMIBuilds() {
    assertThat(artifactService.prepareArtifactWithMetadataQuery(jenkinsArtifactStream, false)).isNotNull();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldFetchCleanupBuilds() {
    assertThat(artifactService.prepareCleanupQuery(artifactStream)).isNotNull();
    assertThat(artifactService.prepareCleanupQuery(customArtifactStream)).isNotNull();
    assertThat(artifactService.prepareCleanupQuery(jenkinsArtifactStream)).isNotNull();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testDeleteArtifactsByUniqueKey() {
    assertThat(artifactService.deleteArtifactsByUniqueKey(artifactStream, null, null)).isTrue();
    artifactService.deleteArtifactsByUniqueKey(artifactStream, null, asList("0.1", "0.2"));
    verify(mockWingsPersistence).delete(any(Query.class));
    artifactService.deleteArtifactsByUniqueKey(jenkinsArtifactStream, null, asList("0.1", "0.2"));
    verify(mockWingsPersistence, times(2)).delete(any(Query.class));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testListSortByBuildNo() {
    Artifact artifact =
        Artifact.Builder.anArtifact().withAppId(APP_ID).withArtifactStreamId(ARTIFACT_STREAM_ID).build();
    ArtifactStream artifactStream1 = CustomArtifactStream.builder().uuid(ARTIFACT_STREAM_ID).name("test").build();
    PageResponse<Artifact> pageResponse = new PageResponse<>();
    pageResponse.add(artifact);
    doReturn(pageResponse).when(mockWingsPersistence).query(any(), any());
    doReturn(artifactStream1).when(mockWingsPersistence).get(any(), eq(ARTIFACT_STREAM_ID));
    PageRequest<Artifact> pageRequest = aPageRequest().build();
    PageResponse<Artifact> out = artifactService.listArtifactsForService(pageRequest);
    assertThat(out).isNotNull();
    assertThat(out.get(0).getArtifactStreamName()).isEqualTo("test");
  }
}
