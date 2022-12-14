package software.wings.delegatetasks.k8s;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class MockFileSystem extends FileSystem {
  private final AtomicBoolean open = new AtomicBoolean(true);
  private final String name;

  public MockFileSystem(String name) {
    this.name = name;
  }

  public FileSystemProvider provider() {
    throw new UnsupportedOperationException("provider() N/A");
  }

  public void close() throws IOException {
    if (!this.open.getAndSet(false)) {
      return;
    }
  }

  public boolean isOpen() {
    return this.open.get();
  }

  public boolean isReadOnly() {
    return true;
  }

  public String getSeparator() {
    return File.separator;
  }

  public Iterable<Path> getRootDirectories() {
    return Collections.emptyList();
  }

  public Iterable<FileStore> getFileStores() {
    return Collections.emptyList();
  }

  public Set<String> supportedFileAttributeViews() {
    return Collections.emptySet();
  }

  public Path getPath(String first, String... more) {
    throw new UnsupportedOperationException("getPath(" + first + ") " + Arrays.toString(more));
  }

  public PathMatcher getPathMatcher(String syntaxAndPattern) {
    throw new UnsupportedOperationException("getPathMatcher(" + syntaxAndPattern + ")");
  }

  public UserPrincipalLookupService getUserPrincipalLookupService() {
    throw new UnsupportedOperationException("getUserPrincipalLookupService() N/A");
  }

  public WatchService newWatchService() throws IOException {
    throw new IOException("newWatchService() N/A");
  }

  public String toString() {
    return this.name;
  }
}
