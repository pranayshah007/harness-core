/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

public class MockPath implements Path {
  private final String path;
  private final FileSystem fs;

  public MockPath(String path) {
    this.path = path;
    this.fs = new MockFileSystem(path);
  }

  public FileSystem getFileSystem() {
    return this.fs;
  }

  public boolean isAbsolute() {
    return true;
  }

  public Path getRoot() {
    return this;
  }

  public Path getFileName() {
    return this;
  }

  public Path getParent() {
    return null;
  }

  public int getNameCount() {
    return 0;
  }

  public Path getName(int index) {
    if (index == 0) {
      return this;
    } else {
      throw new IllegalArgumentException("getName - bad index: " + index);
    }
  }

  public Path subpath(int beginIndex, int endIndex) {
    throw new UnsupportedOperationException("subPath(" + beginIndex + "," + endIndex + ") N/A");
  }

  public boolean startsWith(Path other) {
    return this.startsWith(other.toString());
  }

  public boolean startsWith(String other) {
    return this.path.startsWith(other);
  }

  public boolean endsWith(Path other) {
    return this.endsWith(other.toString());
  }

  public boolean endsWith(String other) {
    return this.path.endsWith(other);
  }

  public Path normalize() {
    return this;
  }

  public Path resolve(Path other) {
    return this.resolve(other.toString());
  }

  public Path resolve(String other) {
    throw new UnsupportedOperationException("resolve(" + other + ") N/A");
  }

  public Path resolveSibling(Path other) {
    return this.resolveSibling(other.toString());
  }

  public Path resolveSibling(String other) {
    throw new UnsupportedOperationException("resolveSibling(" + other + ") N/A");
  }

  public Path relativize(Path other) {
    throw new UnsupportedOperationException("relativize(" + other + ") N/A");
  }

  public URI toUri() {
    throw new UnsupportedOperationException("toUri() N/A");
  }

  public Path toAbsolutePath() {
    return this;
  }

  public Path toRealPath(LinkOption... options) throws IOException {
    return this;
  }

  public File toFile() {
    throw new UnsupportedOperationException("toFile() N/A");
  }

  public WatchKey register(WatchService watcher, Kind<?>... events) throws IOException {
    return this.register(watcher, events, (Modifier[]) null);
  }

  public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers) throws IOException {
    throw new IOException("register(" + this.path + ") N/A");
  }

  public int compareTo(Path other) {
    return this.path.compareTo(other.toString());
  }

  public String toString() {
    return this.path;
  }
}
