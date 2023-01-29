/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.rules;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.shell.InvertedShell;
import org.apache.sshd.server.shell.ProcessShell;
import org.apache.sshd.server.shell.ProcessShellFactory;

/**
 * Created by peeyushaggarwal on 7/27/16.
 */
public class FileSystemAwareProcessShellFactory extends ProcessShellFactory {
  /**
   * Instantiates a new File system aware process shell factory.
   */
  public FileSystemAwareProcessShellFactory() {}

  /**
   * Instantiates a new File system aware process shell factory.
   *
   * @param command the command
   */
  public FileSystemAwareProcessShellFactory(String command, String... elements) {
    this(command, GenericUtils.isEmpty(elements) ? Collections.emptyList() : Arrays.asList(elements));
  }

  /**
   * Instantiates a new File system aware process shell factory.
   *
   * @param command the command
   */
  public FileSystemAwareProcessShellFactory(String command, List<String> elements) {
    super(command, elements);
  }

  @Override
  public Command createShell(ChannelSession channel) {
    return new FileSystemAwareInvertedShellWrapper(createInvertedShell(channel));
  }

  @Override
  protected InvertedShell createInvertedShell(ChannelSession channel) {
    return new ProcessShell(this.resolveEffectiveCommand(channel, this.getCommand(), this.getElements()));
  }
}
