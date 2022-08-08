/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.utils;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.logging.Misc;

import com.google.common.collect.Sets;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

/**
 * Created by peeyushaggarwal on 9/6/16.
 */
public enum FileType {
  /**
   * The constant ZIP.
   */
  ZIP {
    private static final long serialVersionUID = 1L;

    @Override
    protected ArchiveInputStream getArchiveInputStream(BufferedInputStream bufferedInputStream) {
      return new ZipArchiveInputStream(bufferedInputStream);
    }

    @Override
    protected String getUnarchiveCommandString(String fileName) {
      return "unzip " + fileName;
    }
  },

  /**
   * The constant TAR_GZ.
   */
  TAR_GZ {
    private static final long serialVersionUID = 1L;

    @Override
    protected ArchiveInputStream getArchiveInputStream(BufferedInputStream bufferedInputStream) throws IOException {
      return new TarArchiveInputStream(new GZIPInputStream(bufferedInputStream));
    }

    @Override
    protected String getUnarchiveCommandString(String fileName) {
      return "tar xzvf " + fileName;
    }
  },

  /**
   * The constant TAR_BZ.
   */
  TAR_BZ {
    private static final long serialVersionUID = 1L;

    @Override
    protected ArchiveInputStream getArchiveInputStream(BufferedInputStream bufferedInputStream) throws IOException {
      return new TarArchiveInputStream(new BZip2CompressorInputStream(bufferedInputStream));
    }

    @Override
    protected String getUnarchiveCommandString(String fileName) {
      return "tar xjvf " + fileName;
    }
  },

  /**
   * The constant TAR.
   */
  TAR {
    private static final long serialVersionUID = 1L;

    @Override
    protected ArchiveInputStream getArchiveInputStream(BufferedInputStream bufferedInputStream) throws IOException {
      return new TarArchiveInputStream(bufferedInputStream);
    }

    @Override
    protected String getUnarchiveCommandString(String fileName) {
      return "tar xvf " + fileName;
    }
  },

  /**
   * The constant UNKNOWN.
   */
  UNKNOWN {
    private static final long serialVersionUID = 1L;

    @Override
    public boolean test(BufferedInputStream bufferedInputStream) {
      return true;
    }

    @Override
    public String getRoot(BufferedInputStream bufferedInputStream) {
      return ".";
    }

    @Override
    protected ArchiveInputStream getArchiveInputStream(BufferedInputStream bufferedInputStream) throws IOException {
      return null;
    }

    @Override
    protected String getUnarchiveCommandString(String fileName) {
      return "#Please write commands to unarchive " + fileName;
    }
  };

  /**
   * Gets top level hierarchy.
   *
   * @param archiveInputStream the archive input stream
   * @return the top level hierarchy
   * @throws IOException the io exception
   */
  protected static Set<String> getTopLevelHierarchy(ArchiveInputStream archiveInputStream) throws IOException {
    Set<String> topLevelElements = Sets.newHashSet();
    ArchiveEntry archiveEntry;
    while ((archiveEntry = archiveInputStream.getNextEntry()) != null) {
      Path path = Paths.get(archiveEntry.getName());
      if (path.getNameCount() > 1) {
        topLevelElements.add(path.getName(0).toString());
      } else if (archiveEntry.isDirectory()) {
        topLevelElements.add(path.getName(0).toString());
      }
    }
    return topLevelElements;
  }

  /**
   * Test boolean.
   *
   * @param bufferedInputStream the buffered input stream
   * @return the boolean
   */
  public boolean test(BufferedInputStream bufferedInputStream) {
    try {
      bufferedInputStream.mark(10 * 1024);
      return Misc.ignoreException(() -> getArchiveInputStream(bufferedInputStream).getNextEntry() != null, false);
    } finally {
      Misc.ignoreException(bufferedInputStream::reset);
    }
  }

  /**
   * Gets root.
   *
   * @param bufferedInputStream the buffered input stream
   * @return the root
   */
  public String getRoot(BufferedInputStream bufferedInputStream) {
    try {
      bufferedInputStream.mark(10 * 1024);
      return Misc.ignoreException(() -> {
        Set<String> topLevelHierarchy = getTopLevelHierarchy(getArchiveInputStream(bufferedInputStream));
        if (topLevelHierarchy.size() == 1) {
          return topLevelHierarchy.iterator().next();
        } else {
          return ".";
        }
      }, null);
    } finally {
      Misc.ignoreException(bufferedInputStream::reset);
    }
  }

  /**
   * Gets unarchive command.
   *
   * @param fileName      the file name
   * @param rootDirectory the root directory
   * @param symlink       the symlink
   * @return the unarchive command
   */
  public String getUnarchiveCommand(String fileName, String rootDirectory, String symlink) {
    return getUnarchiveCommandString(fileName) + "\n"
        + (isNotBlank(rootDirectory) ? "ln -s " + rootDirectory + " " + symlink : "");
  }

  /**
   * Gets archive input stream.
   *
   * @param bufferedInputStream the buffered input stream
   * @return the archive input stream
   * @throws IOException the io exception
   */
  protected abstract ArchiveInputStream getArchiveInputStream(BufferedInputStream bufferedInputStream)
      throws IOException;

  /**
   * Gets unarchive command string.
   *
   * @param fileName the file name
   * @return the unarchive command string
   */
  protected abstract String getUnarchiveCommandString(String fileName);
}
