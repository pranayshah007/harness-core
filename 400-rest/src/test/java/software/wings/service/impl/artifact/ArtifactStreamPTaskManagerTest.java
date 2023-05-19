/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDC)
public class ArtifactStreamPTaskManagerTest extends CategoryTest {
  private static final String PERPETUAL_TASK_ID = "PERPETUAL_TASK_ID";

  @Mock private ArtifactStreamPTaskHelper artifactStreamPTaskHelper;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private PerpetualTaskService perpetualTaskService;

  @Inject @InjectMocks private ArtifactStreamPTaskManager manager;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void setUp() {
    enableFeatureFlag();
  }

  @Test
  @Owner(developers = OwnerRule.SRINIVAS)
  @Category(UnitTests.class)
  public void testOnSaved() {
    ArtifactStream artifactStream = prepareArtifactStream();
    disableFeatureFlag();
    manager.onSaved(artifactStream);
    verify(artifactStreamPTaskHelper, never()).createPerpetualTask(eq(artifactStream));

    enableFeatureFlag();
    manager.onSaved(artifactStream);
    verify(artifactStreamPTaskHelper, times(1)).createPerpetualTask(eq(artifactStream));
  }

  @Test
  @Owner(developers = OwnerRule.GARVIT)
  @Category(UnitTests.class)
  public void testOnUpdatedCreatePerpetualTask() {
    ArtifactStream artifactStream = prepareArtifactStream();
    disableFeatureFlag();
    manager.onUpdated(artifactStream);
    verify(artifactStreamPTaskHelper, never()).createPerpetualTask(eq(artifactStream));

    enableFeatureFlag();
    manager.onUpdated(artifactStream);
    verify(artifactStreamPTaskHelper, times(1)).createPerpetualTask(eq(artifactStream));
  }

  @Test
  @Owner(developers = OwnerRule.GARVIT)
  @Category(UnitTests.class)
  public void testOnUpdatedResetPerpetualTask() {
    ArtifactStream artifactStream = prepareArtifactStream();
    artifactStream.setPerpetualTaskId(PERPETUAL_TASK_ID);
    manager.onUpdated(artifactStream);
    verify(perpetualTaskService, times(1)).resetTask(eq(ACCOUNT_ID), eq(PERPETUAL_TASK_ID), eq(null));

    when(perpetualTaskService.resetTask(ACCOUNT_ID, PERPETUAL_TASK_ID, null)).thenReturn(false);
    manager.onUpdated(artifactStream);
    verify(perpetualTaskService, times(2)).resetTask(eq(ACCOUNT_ID), eq(PERPETUAL_TASK_ID), eq(null));
  }

  @Test
  @Owner(developers = OwnerRule.GARVIT)
  @Category(UnitTests.class)
  public void testOnDeletedDeletePerpetualTask() {
    ArtifactStream artifactStream = prepareArtifactStream();
    manager.onDeleted(artifactStream);
    verify(perpetualTaskService, never()).deleteTask(eq(ACCOUNT_ID), eq(PERPETUAL_TASK_ID));

    artifactStream.setPerpetualTaskId(PERPETUAL_TASK_ID);
    disableFeatureFlag();
    manager.onDeleted(artifactStream);
    verify(perpetualTaskService, never()).deleteTask(eq(ACCOUNT_ID), eq(PERPETUAL_TASK_ID));

    enableFeatureFlag();
    manager.onDeleted(artifactStream);
    verify(artifactStreamPTaskHelper, times(1)).deletePerpetualTask(eq(ACCOUNT_ID), eq(PERPETUAL_TASK_ID));

    when(perpetualTaskService.deleteTask(ACCOUNT_ID, PERPETUAL_TASK_ID)).thenReturn(false);
    manager.onDeleted(artifactStream);
    verify(artifactStreamPTaskHelper, times(2)).deletePerpetualTask(eq(ACCOUNT_ID), eq(PERPETUAL_TASK_ID));
  }

  private void enableFeatureFlag() {
    when(featureFlagService.isEnabled(FeatureName.ARTIFACT_PERPETUAL_TASK, ACCOUNT_ID)).thenReturn(true);
  }

  private void disableFeatureFlag() {
    when(featureFlagService.isEnabled(FeatureName.ARTIFACT_PERPETUAL_TASK, ACCOUNT_ID)).thenReturn(false);
  }

  private ArtifactStream prepareArtifactStream() {
    return DockerArtifactStream.builder()
        .accountId(ACCOUNT_ID)
        .appId(APP_ID)
        .uuid(ARTIFACT_STREAM_ID)
        .settingId(SETTING_ID)
        .imageName("wingsplugins/todolist")
        .autoPopulate(true)
        .serviceId(SERVICE_ID)
        .build();
  }
}
