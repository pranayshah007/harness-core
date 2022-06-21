/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.shell;

import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.shell.SshSessionConfig.Builder.aSshSessionConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.ApiServiceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.logging.LogCallback;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@Slf4j
@RunWith(PowerMockRunner.class)
@PrepareForTest(SshSessionFactory.class)
public class SshSessionManagerTest extends ApiServiceTestBase {
  public static final String EXEC = "exec";
  public static final int SOCKET_CONNECT_TIMEOUT = 1000;
  private JSch jSch = mock(JSch.class);
  private Session session = mock(Session.class);
  private ChannelExec channel = mock(ChannelExec.class);
  private LogCallback logCallback = mock(LogCallback.class);
  private SshSessionManager manager = new SshSessionManager();

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testEvictAndDisconnectCachedSession() {
    Map<String, Session> sessions = (Map<String, Session>) ReflectionUtils.getFieldValue(manager, "sessions");
    sessions.put("e~h", session);
    doReturn(true).when(session).isConnected();

    Map<String, List<Session>> simplexSessions =
        (Map<String, List<Session>>) ReflectionUtils.getFieldValue(manager, "simplexSessions");
    List<Session> simplexSessionsList = Arrays.asList(session, session, session);
    simplexSessions.put("e~h", simplexSessionsList);

    assertThat(sessions).hasSize(1);
    assertThat(simplexSessions).hasSize(1);
    SshSessionManager.evictAndDisconnectCachedSession("e", "h");
    assertThat(sessions).hasSize(0);
    assertThat(simplexSessions).hasSize(0);
    verify(session, times(4)).disconnect();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetSimplexSession() throws Exception {
    PowerMockito.mockStatic(SshSessionFactory.class);
    SshSessionConfig config =
        aSshSessionConfig().withHost("h").withExecutionId("e").withSocketConnectTimeout(SOCKET_CONNECT_TIMEOUT).build();
    when(SshSessionFactory.getSSHSession(config, logCallback)).thenReturn(session);
    doReturn(channel).when(session).openChannel(EXEC);

    Map<String, List<Session>> simplexSessions =
        (Map<String, List<Session>>) ReflectionUtils.getFieldValue(manager, "simplexSessions");

    doThrow(new JSchException("")).when(channel).connect(SOCKET_CONNECT_TIMEOUT);
    Session simplexSession = SshSessionManager.getSimplexSession(config, logCallback);
    PowerMockito.verifyStatic(SshSessionFactory.class, times(2));

    assertThat(simplexSessions).hasSize(1);
    assertThat(simplexSessions.get("e~h")).hasSize(1);
    assertThat(simplexSession).isEqualTo(session);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetSimplexSessionWithAlreadyCreatedSessions() throws Exception {
    PowerMockito.mockStatic(SshSessionFactory.class);
    SshSessionConfig config =
        aSshSessionConfig().withHost("h").withExecutionId("e").withSocketConnectTimeout(SOCKET_CONNECT_TIMEOUT).build();
    when(SshSessionFactory.getSSHSession(config, logCallback)).thenReturn(session);
    doReturn(channel).when(session).openChannel(EXEC);

    Map<String, List<Session>> simplexSessions =
        (Map<String, List<Session>>) ReflectionUtils.getFieldValue(manager, "simplexSessions");
    simplexSessions.put("e~h", Lists.newArrayList(session));
    simplexSessions.put("random", Lists.newArrayList(session));

    Session simplexSession = SshSessionManager.getSimplexSession(config, logCallback);
    PowerMockito.verifyStatic(SshSessionFactory.class, times(1));

    assertThat(simplexSessions).hasSize(2);
    assertThat(simplexSessions.get("e~h")).hasSize(2);
    assertThat(simplexSession).isEqualTo(session);
  }
}
