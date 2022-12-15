/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.rules;

import static java.util.Arrays.asList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.channel.PtyMode;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.MapEntryUtils;
import org.apache.sshd.common.util.ValidateUtils;
import org.apache.sshd.common.util.io.IoUtils;
import org.apache.sshd.common.util.logging.AbstractLoggingBean;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.channel.PuttyRequestHandler;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.session.ServerSessionHolder;
import org.apache.sshd.server.shell.InvertedShell;
import org.apache.sshd.server.shell.TtyFilterInputStream;
import org.apache.sshd.server.shell.TtyFilterOutputStream;

/**
 * Created by peeyushaggarwal on 7/27/16.
 */
@Slf4j
public class FileSystemAwareProcessShell extends AbstractLoggingBean implements InvertedShell, ServerSessionHolder {
  private final List<String> command;
  private String cmdValue;
  private ServerSession session;
  private ChannelSession channelSession;
  private Process process;
  private TtyFilterOutputStream in;
  private TtyFilterInputStream out;
  private TtyFilterInputStream err;
  private FileSystem root;

  /**
   * Instantiates a new File system aware process shell.
   *
   * @param command the command
   */
  public FileSystemAwareProcessShell(String... command) {
    this(GenericUtils.isEmpty(command) ? Collections.<String>emptyList() : asList(command));
  }

  /**
   * Instantiates a new File system aware process shell.
   *
   * @param command the command
   */
  public FileSystemAwareProcessShell(Collection<String> command) {
    this.command = new ArrayList<>(ValidateUtils.checkNotNullAndNotEmpty(command, "No process shell command(s)"));
    this.cmdValue = GenericUtils.join(command, ' ');
  }

  /**
   * Sets root.
   *
   * @param root the root
   */
  public void setRoot(FileSystem root) {
    this.root = root;
  }

  @Override
  public ServerSession getServerSession() {
    return session;
  }

  @Override
  public void setSession(ServerSession session) {
    this.session = ValidateUtils.checkNotNull(session, "No server session");
    ValidateUtils.checkTrue(process == null, "Session set after process started");
  }

  /**
   * Resolve shell environment map.
   *
   * @param env the env
   * @return the map
   */
  protected Map<String, String> resolveShellEnvironment(Map<String, String> env) {
    return env;
  }

  /**
   * Resolve shell tty options map.
   *
   * @param modes the modes
   * @return the map
   */
  // for some reason these modes provide best results BOTH with Linux SSH client and PUTTY
  protected Map<PtyMode, Integer> resolveShellTtyOptions(Map<PtyMode, Integer> modes) {
    if (PuttyRequestHandler.isPuttyClient(getServerSession())) {
      return PuttyRequestHandler.resolveShellTtyOptions(modes);
    } else {
      return modes;
    }
  }

  @Override
  public OutputStream getInputStream() {
    return in;
  }

  @Override
  public InputStream getOutputStream() {
    return out;
  }

  @Override
  public InputStream getErrorStream() {
    return err;
  }

  @Override
  public boolean isAlive() {
    return this.process.isAlive();
  }

  @Override
  public int exitValue() {
    if (this.isAlive()) {
      try {
        return this.process.waitFor();
      } catch (InterruptedException ex) {
        throw new RuntimeException(ex);
      }
    } else {
      return this.process.exitValue();
    }
  }

  @Override
  public String toString() {
    return GenericUtils.isEmpty(cmdValue) ? super.toString() : cmdValue;
  }

  @Override
  public ChannelSession getServerChannelSession() {
    return this.channelSession;
  }

  @Override
  public void start(ChannelSession channelSession, Environment env) throws IOException {
    this.channelSession = channelSession;
    Map<String, String> varsMap = this.resolveShellEnvironment(env.getEnv());

    for (int i = 0; i < this.command.size(); ++i) {
      String cmd = this.command.get(i);
      if ("$USER".equals(cmd)) {
        cmd = varsMap.get("USER");
        this.command.set(i, cmd);
        this.cmdValue = GenericUtils.join(this.command, ' ');
      }
    }

    ProcessBuilder builder = new ProcessBuilder(this.command);
    Map<String, String> modes;
    if (MapEntryUtils.size(varsMap) > 0) {
      try {
        modes = builder.environment();
        modes.putAll(varsMap);
      } catch (Exception ex) {
        this.warn("start({}) - Failed ({}) to set environment for command={}: {}", channelSession,
            ex.getClass().getSimpleName(), this.cmdValue, ex.getMessage(), ex);

        if (log.isDebugEnabled()) {
          log.debug("start(" + cmdValue + ") failure details: " + ex.getMessage(), ex);
          for (StackTraceElement elem : ex.getStackTrace()) {
            log.debug("Trace: {}", elem);
          }
        }
      }
    }

    if (log.isDebugEnabled()) {
      log.debug("Starting shell with command: '{}' and env: {}", builder.command(), builder.environment());
    }

    if (root != null) {
      builder.directory(new File(root.toString()));
    }
    process = builder.start();

    Map<PtyMode, ?> ptyModels = resolveShellTtyOptions(env.getPtyModes());
    out = new TtyFilterInputStream(process.getInputStream(), ptyModels);
    err = new TtyFilterInputStream(process.getErrorStream(), ptyModels);
    in = new TtyFilterOutputStream(process.getOutputStream(), err, ptyModels);
  }

  @Override
  public void destroy(ChannelSession channelSession) throws Exception {
    // NOTE !!! DO NOT NULL-IFY THE PROCESS SINCE "exitValue" is called subsequently
    if (process != null) {
      if (log.isDebugEnabled()) {
        log.debug("destroy({}) Destroy process for '{}'", channelSession, this.cmdValue);
      }
      process.destroy();
    }

    IOException e = IoUtils.closeQuietly(getInputStream(), getOutputStream(), getErrorStream());
    if (e != null) {
      if (log.isDebugEnabled()) {
        log.debug("destroy({}) {} while destroy streams of '{}': {}", channelSession, e.getClass().getSimpleName(),
            this, e.getMessage(), e);
      }

      if (log.isTraceEnabled()) {
        Throwable[] suppressed = e.getSuppressed();
        if (GenericUtils.length(suppressed) > 0) {
          for (Throwable t : suppressed) {
            log.trace("Suppressed " + t.getClass().getSimpleName() + ") while destroy streams of '" + this
                + "': " + t.getMessage());
          }
        }
      }
    }
  }
}
