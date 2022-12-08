/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.collect;

import static io.harness.delegate.task.ListNotifyResponseData.Builder.aListNotifyResponseData;
import static io.harness.rule.OwnerRule.GARVIT;

import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.persistence.artifact.Artifact.ContentStatus.DOWNLOADED;
import static software.wings.persistence.artifact.ArtifactFile.Builder.anArtifactFile;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_PATH;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_NAME;
import static software.wings.utils.WingsTestConstants.JOB_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.JenkinsArtifactStream;
import software.wings.persistence.artifact.Artifact;
import software.wings.persistence.artifact.Artifact.Status;
import software.wings.persistence.artifact.ArtifactFile;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.NotificationService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.assertj.core.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by rishi on 12/21/16.
 */
public class ArtifactCollectionCallbackTest extends WingsBaseTest {
  @Mock private ArtifactService artifactService;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private NotificationService notificationService;

  @InjectMocks @Inject private ArtifactCollectionCallback artifactCollectionCallback;

  private final Artifact ARTIFACT = anArtifact()
                                        .withUuid(ARTIFACT_ID)
                                        .withAccountId(ACCOUNT_ID)
                                        .withAppId(APP_ID)
                                        .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                        .build();

  private final ArtifactStream ARTIFACT_SOURCE = JenkinsArtifactStream.builder()
                                                     .sourceName(ARTIFACT_STREAM_NAME)
                                                     .appId(APP_ID)
                                                     .settingId(SETTING_ID)
                                                     .jobname(JOB_NAME)
                                                     .serviceId(SERVICE_ID)
                                                     .artifactPaths(asList(ARTIFACT_PATH))
                                                     .build();

  public static final ArtifactFile ARTIFACT_FILE =
      anArtifactFile().withAppId(APP_ID).withUuid("ARTIFACT_FILE_ID").build();

  @Before
  public void setupMocks() {
    when(artifactService.get(ARTIFACT_ID)).thenReturn(ARTIFACT);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(ARTIFACT_SOURCE);
    artifactCollectionCallback.setArtifactId(ARTIFACT_ID);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotify() {
    artifactCollectionCallback.notify(
        Maps.newHashMap("", aListNotifyResponseData().addData(ARTIFACT_FILE.toDTO()).build()));
    verify(artifactService).updateStatus(ARTIFACT_ID, ACCOUNT_ID, Status.APPROVED, DOWNLOADED, "");
    verify(artifactService).addArtifactFile(ARTIFACT_ID, ACCOUNT_ID, Lists.newArrayList(ARTIFACT_FILE));
  }
}
