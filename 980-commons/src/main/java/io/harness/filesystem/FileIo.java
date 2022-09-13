/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.filesystem;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.govern.IgnoreThrowable.ignoredOnPurpose;
import static io.harness.govern.Switch.noop;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.System.currentTimeMillis;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.SYNC;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileData;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.zeroturnaround.exec.InvalidExitValueException;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

@OwnedBy(PL)
@UtilityClass
@Slf4j
public class FileIo {
  public static void createDirectoryIfDoesNotExist(final String directoryPath) throws IOException {
    createDirectoryIfDoesNotExist(Paths.get(directoryPath));
  }

  public static void createDirectoryIfDoesNotExist(final Path filePath) throws IOException {
    try {
      Files.createDirectories(filePath);
    } catch (FileAlreadyExistsException exception) {
      ignoredOnPurpose(exception);
    }
  }

  public static boolean checkIfFileExist(final String filePath) throws IOException {
    Path path = Paths.get(filePath);
    return Files.exists(path);
  }

  public static boolean waitForDirectoryToBeAccessibleOutOfProcess(final String directoryPath, int maxRetries) {
    int retryCountRemaining = maxRetries;
    while (true) {
      try {
        ProcessExecutor processExecutor = new ProcessExecutor()
                                              .timeout(1, TimeUnit.SECONDS)
                                              .directory(new File(directoryPath))
                                              .commandSplit(getDirectoryCheckCommand())
                                              .readOutput(true);
        ProcessResult processResult = processExecutor.execute();
        if (processResult.getExitValue() == 0) {
          return true;
        }
      } catch (IOException | InterruptedException | TimeoutException | InvalidExitValueException e) {
        noop(); // Ignore
      }
      retryCountRemaining--;
      if (retryCountRemaining == 0) {
        return false;
      }
      sleep(ofSeconds(1));
    }
  }

  private static String getDirectoryCheckCommand() {
    String osName = System.getProperty("os.name");
    if (isNotEmpty(osName) && osName.startsWith("Windows")) {
      return "cmd /c cd";
    }
    return "bash -c pwd";
  }

  public static void deleteFileIfExists(final String filePath) throws IOException {
    Files.deleteIfExists(Paths.get(filePath));
  }

  public static void deleteDirectoryAndItsContentIfExists(final String directoryPath) throws IOException {
    if (isEmpty(directoryPath) || !Files.exists(Paths.get(directoryPath))) {
      log.info("Directory at path: " + directoryPath + " doesn't exist");
      return;
    }

    log.info("Deleting directory at path: " + directoryPath);

    Files.walkFileTree(Paths.get(directoryPath), new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.deleteIfExists(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        if (exc != null) {
          throw exc;
        }
        Files.deleteIfExists(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  public static void writeUtf8StringToFile(final String directoryPath, String content) throws IOException {
    Files.write(
        Paths.get(directoryPath), content.getBytes(StandardCharsets.UTF_8), CREATE, TRUNCATE_EXISTING, WRITE, SYNC);
  }

  public static void writeFile(final String directoryPath, byte[] content) throws IOException {
    writeFile(Paths.get(directoryPath), content);
  }

  public static void writeFile(final Path filePath, byte[] content) throws IOException {
    Files.write(filePath, content, CREATE, WRITE, SYNC);
  }

  public static boolean acquireLock(File file, Duration wait) {
    File lockFile = new File(file.getPath() + ".lock");

    long startTime = currentTimeMillis();

    try {
      while (!atomicallyCreateNewFile(lockFile)) {
        long modified = lockFile.lastModified();
        // If the file was deleted and we know it was there because the createNewFile failed, lets use current time.
        // This way we will give a complete cycle for the lock. If no other competes for it we will get it from the
        // next attempt and it wouldn't matter.
        if (modified == 0) {
          modified = currentTimeMillis();
        }

        // When we should finish waiting the other process to release the file.
        long finishAt = Math.max(startTime, modified) + wait.toMillis();

        // How much time remain
        long remaining = finishAt - currentTimeMillis();

        // If we have waited too long after the last lock we should just grab it.
        // Probably the other process died without cleaning the lock file.
        if (remaining <= 0) {
          break;
        }

        try {
          Thread.sleep(Math.min(100, remaining));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
      return true;
    } catch (RuntimeException | IOException e) {
      return false;
    }
  }

  private boolean atomicallyCreateNewFile(File lockFile) throws IOException {
    try {
      if (lockFile.createNewFile()) {
        return true;
      }
    } catch (IOException exception) {
      // Suppress "No such file or directory" - it seems internal createNewFile issue
      if (!exception.getMessage().equals("No such file or directory")) {
        throw exception;
      }
    }
    return false;
  }

  public static boolean releaseLock(File file) {
    File lockFile = new File(file.getPath() + ".lock");
    if (lockFile.delete()) {
      return true;
    }
    if (!lockFile.exists()) {
      return true;
    }
    return false;
  }

  public static boolean isLocked(File file) {
    File lockFile = new File(file.getPath() + ".lock");
    return lockFile.exists();
  }

  public static void writeWithExclusiveLockAcrossProcesses(
      String input, String filePath, StandardOpenOption standardOpenOption) throws IOException {
    ByteBuffer buffer = ByteBuffer.wrap(input.getBytes(StandardCharsets.UTF_8));
    Path path = Paths.get(filePath);
    try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.WRITE, standardOpenOption);
         FileLock ignore = fileChannel.lock()) {
      fileChannel.write(buffer);
    }
  }

  public static String getFileContentsWithSharedLockAcrossProcesses(String filePath) throws IOException {
    StringBuilder builder = new StringBuilder(128);
    Path path = Paths.get(filePath);
    try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.READ);
         FileLock ignore = fileChannel.lock(0, Long.MAX_VALUE, true)) {
      ByteBuffer buffer = ByteBuffer.allocate(128);
      int noOfBytesRead = fileChannel.read(buffer);
      while (noOfBytesRead != -1) {
        buffer.flip();
        while (buffer.hasRemaining()) {
          builder.append((char) buffer.get());
        }
        buffer.clear();
        noOfBytesRead = fileChannel.read(buffer);
      }
      return builder.toString();
    }
  }

  public static String getHomeDir() {
    String osName = System.getProperty("os.name");
    if (isNotEmpty(osName) && osName.toLowerCase().startsWith("win")) {
      String homeDrive = System.getenv("HOMEDRIVE");
      String homePath = System.getenv("HOMEPATH");
      if (isNotEmpty(homeDrive) && isNotEmpty(homePath)) {
        String homeDir = homeDrive + homePath;
        File f = new File(homeDir);
        if (f.exists() && f.isDirectory()) {
          return homeDir;
        }
      }
      String userProfile = System.getenv("USERPROFILE");
      if (isNotEmpty(userProfile)) {
        File f = new File(userProfile);
        if (f.exists() && f.isDirectory()) {
          return userProfile;
        }
      }
    }
    String home = System.getenv("HOME");
    if (isNotEmpty(home)) {
      File f = new File(home);
      if (f.exists() && f.isDirectory()) {
        return home;
      }
    }

    return System.getProperty("user.home", ".");
  }

  public static List<FileData> getFilesUnderPath(String filePath) throws IOException {
    List<FileData> fileList = new ArrayList<>();

    Path path = Paths.get(filePath);
    try (Stream<Path> paths = Files.walk(path)) {
      paths.filter(Files::isRegularFile).forEach(each -> addFiles(fileList, each, path.toString()));
    }

    return fileList;
  }

  public static List<Path> getFilesUnderPathMatchesFirstLine(String path, Predicate<String> predicate)
      throws IOException {
    List<Path> filePaths = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(Paths.get(path))) {
      for (Path filePath : paths.filter(Files::isRegularFile).collect(Collectors.toList())) {
        LineIterator lineIterator = FileUtils.lineIterator(filePath.toFile(), StandardCharsets.UTF_8.name());
        while (lineIterator.hasNext()) {
          String line = lineIterator.nextLine();
          if (isNotEmpty(line)) {
            if (predicate.test(line)) {
              filePaths.add(Paths.get(getRelativePath(filePath, path)));
            }

            break;
          }
        }
      }
    }

    return filePaths;
  }

  private static void addFiles(List<FileData> fileList, Path path, String basePath) {
    String filePath = getRelativePath(path, basePath);
    byte[] fileBytes = getFileBytes(path);

    fileList.add(FileData.builder().filePath(filePath).fileBytes(fileBytes).build());
  }

  private static byte[] getFileBytes(Path path) {
    byte[] fileBytes;

    try {
      fileBytes = Files.readAllBytes(path);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }

    return fileBytes;
  }

  private static String getRelativePath(Path path, String basePath) {
    Path fileAbsolutePath = path.toAbsolutePath();
    Path baseAbsolutePath = Paths.get(basePath).toAbsolutePath();

    return baseAbsolutePath.relativize(fileAbsolutePath).toString();
  }
}
