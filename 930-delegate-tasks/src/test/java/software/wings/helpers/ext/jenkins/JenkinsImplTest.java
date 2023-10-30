/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.jenkins;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.ArtifactServerException;
import io.harness.rule.Owner;

import software.wings.beans.JenkinsConfig;

import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.Executable;
import com.offbytwo.jenkins.model.QueueItem;
import com.offbytwo.jenkins.model.QueueReference;
import com.offbytwo.jenkins.model.QueueTask;
import java.io.IOException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class JenkinsImplTest {
  private JenkinsImpl jenkins;

  @Mock private CustomJenkinsServer jenkinsServer;
  @Mock private CustomJenkinsHttpClient jenkinsHttpClient;

  @Before
  public void setup() {
    this.jenkins = new JenkinsImpl(jenkinsServer, jenkinsHttpClient);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  @Ignore("https://harness.atlassian.net/browse/CDS-79499 - Need to fix")
  public void shouldGetBuildDetectCancelledQueuedItem() throws IOException {
    QueueReference queueReference = mock(QueueReference.class);
    JenkinsConfig jenkinsConfig = mock(JenkinsConfig.class);

    QueueItem queueItem = new QueueItem();
    queueItem.setCancelled(true);

    when(jenkinsServer.getQueueItem(queueReference)).thenReturn(queueItem);

    assertThatCode(() -> jenkins.getBuild(queueReference, jenkinsConfig))
        .isInstanceOf(ArtifactServerException.class)
        .hasMessage("Queued job cancelled");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  @Ignore("https://harness.atlassian.net/browse/CDS-79499 - Need to fix")
  public void shouldGetBuildReturnNullWhenQueueItemIsNull() throws IOException {
    QueueReference queueReference = mock(QueueReference.class);
    JenkinsConfig jenkinsConfig = mock(JenkinsConfig.class);

    when(jenkinsServer.getQueueItem(queueReference)).thenReturn(null);

    assertThat(jenkins.getBuild(queueReference, jenkinsConfig)).isNull();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  @Ignore("https://harness.atlassian.net/browse/CDS-79499 - Need to fix")
  public void shouldGetBuildReturnNullWhenExecutableIsNull() throws IOException {
    QueueReference queueReference = mock(QueueReference.class);
    JenkinsConfig jenkinsConfig = mock(JenkinsConfig.class);

    QueueItem queueItem = new QueueItem();
    queueItem.setExecutable(null);

    when(jenkinsServer.getQueueItem(queueReference)).thenReturn(queueItem);

    assertThat(jenkins.getBuild(queueReference, jenkinsConfig)).isNull();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  @Ignore("https://harness.atlassian.net/browse/CDS-79499 - Need to fix")
  public void shouldGetBuildReturnNullWhenTaskIsNull() throws IOException {
    QueueReference queueReference = mock(QueueReference.class);
    JenkinsConfig jenkinsConfig = mock(JenkinsConfig.class);

    QueueItem queueItem = new QueueItem();
    queueItem.setExecutable(null);

    when(jenkinsServer.getQueueItem(queueReference)).thenReturn(queueItem);

    assertThat(jenkins.getBuild(queueReference, jenkinsConfig)).isNull();
  }
  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  @Ignore("https://harness.atlassian.net/browse/CDS-79499 - Need to fix")
  public void shouldGetBuildReturnBuild() throws IOException {
    QueueReference queueReference = mock(QueueReference.class);
    JenkinsConfig jenkinsConfig = mock(JenkinsConfig.class);

    final Build build = new Build();
    QueueItem queueItem = new QueueItem();
    queueItem.setExecutable(new Executable());
    queueItem.setTask(new QueueTask());

    when(jenkinsConfig.isUseConnectorUrlForJobExecution()).thenReturn(false);
    when(jenkinsServer.getQueueItem(queueReference)).thenReturn(queueItem);
    when(jenkinsServer.getBuild(queueItem)).thenReturn(build);

    assertThat(jenkins.getBuild(queueReference, jenkinsConfig)).isEqualTo(build);
  }
}
