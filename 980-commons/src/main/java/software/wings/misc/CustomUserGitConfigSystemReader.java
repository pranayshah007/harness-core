/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.misc;

import java.io.File;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

public class CustomUserGitConfigSystemReader extends SystemReader {
  private static final SystemReader proxy = SystemReader.getInstance();
  private final String userGitConfigPath;

  public CustomUserGitConfigSystemReader(String userGitConfigPath) {
    this.userGitConfigPath = userGitConfigPath;
  }

  @Override
  public String getenv(String variable) {
    return proxy.getenv(variable);
  }

  @Override
  public String getHostname() {
    return proxy.getHostname();
  }

  @Override
  public String getProperty(String key) {
    return proxy.getProperty(key);
  }

  @Override
  public long getCurrentTime() {
    return proxy.getCurrentTime();
  }

  @Override
  public int getTimezone(long when) {
    return proxy.getTimezone(when);
  }

  @Override
  public FileBasedConfig openJGitConfig(Config parent, FS fs) {
    return proxy.openJGitConfig(parent, fs);
  }

  @Override
  public FileBasedConfig openUserConfig(Config parent, FS fs) {
    if (userGitConfigPath != null) {
      return new FileBasedConfig(parent, new File(userGitConfigPath), fs);
    }
    return openSystemConfig(parent, fs);
  }

  @Override
  public FileBasedConfig openSystemConfig(Config parent, FS fs) {
    return new FileBasedConfig(parent, null, fs) {
      @Override
      public void load() {}

      @Override
      public boolean isOutdated() {
        return false;
      }
    };
  }
}
