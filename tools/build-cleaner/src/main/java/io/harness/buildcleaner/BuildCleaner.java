/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.buildcleaner;

import io.harness.buildcleaner.bazel.BuildFile;
import io.harness.buildcleaner.bazel.JavaBinary;
import io.harness.buildcleaner.bazel.JavaLibrary;
import io.harness.buildcleaner.javaparser.ClassMetadata;
import io.harness.buildcleaner.javaparser.ClasspathParser;
import io.harness.buildcleaner.javaparser.PackageParser;

import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuildCleaner {
  private static final String DEFAULT_VISIBILITY = "//visibility:public";
  private static final String BUILD_CLEANER_INDEX_FILE_NAME = ".build-cleaner-index";

  private static final Logger logger = LoggerFactory.getLogger(BuildCleaner.class);
  private CommandLine options;
  private MavenManifest mavenManifest;
  private MavenManifest mavenManifestOverride;
  private PackageParser packageParser;

  BuildCleaner(String[] args) {
    this.options = getCommandLineOptions(args);

    Path mavenManifestFileLocation = mavenManifestFile();
    if (Files.exists(mavenManifestFileLocation)) {
      this.mavenManifest = MavenManifest.loadFromFile(mavenManifestFileLocation);
    }

    Path mavenManifestOverrideFileLocation = mavenManifestOverrideFile();
    if (Files.exists(mavenManifestOverrideFileLocation)) {
      this.mavenManifestOverride = MavenManifest.loadFromOverrideFile(mavenManifestOverrideFileLocation);
    }

    this.packageParser = new PackageParser(workspace());
  }

  public static void main(String[] args) throws IOException, ClassNotFoundException {
    new BuildCleaner(args).run();
  }

  public void run() throws IOException, ClassNotFoundException {
    logger.info("Workspace: " + workspace());

    // Create harness code index or load from an existing index.
    SymbolDependencyMap harnessSymbolMap = buildHarnessSymbolMap();
    logger.debug("Total Java classes found: " + harnessSymbolMap.getCacheSize());

    harnessSymbolMap.serializeToFile(indexFilePath().toString());
    logger.info("Index creation complete.");

    // If recursive option is set, generate build file for each folder inside.
    Files
        .find(workspace().resolve(module()), options.hasOption("recursive") ? Integer.MAX_VALUE : 0,
            (filePath, fileAttr) -> fileAttr.isDirectory())
        .forEach(path -> {
          try {
            Path modulePath = workspace().relativize(path);
            Optional<BuildFile> buildFile = generateBuildForModule(modulePath, harnessSymbolMap);

            if (buildFile.isPresent()) {
              logger.info("Writing Build file for Module: " + path);
              buildFile.get().writeToPackage(workspace().resolve(path));
            }
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  /**
   * Creates symbol to package path index using package parser. If an index file already exists, directly
   * load it.
   * @throws IOException
   * @throws ClassNotFoundException
   */
  @VisibleForTesting
  protected SymbolDependencyMap buildHarnessSymbolMap() throws IOException, ClassNotFoundException {
    SymbolDependencyMap harnessSymbolMap = new SymbolDependencyMap();

    if (indexFileExists() && !options.hasOption("overrideIndex")) {
      logger.info("Loading the already existing index file: " + indexFilePath().toString());
      return SymbolDependencyMap.deserializeFromFile(indexFilePath().toString());
    }
    logger.info("Creating index using sources matching: " + indexSourceGlob());

    ClasspathParser classpathParser = packageParser.getClassPathParser();
    classpathParser.parseClasses(indexSourceGlob(), options.hasOption("findBuildInParent"));

    // Add repository java source code index to the cache.
    Set<ClassMetadata> fullyQualifiedClassNames = classpathParser.getFullyQualifiedClassNames();
    for (ClassMetadata metadata : fullyQualifiedClassNames) {
      harnessSymbolMap.addSymbolTarget(metadata.getFullyQualifiedClassName(), metadata.getBuildModulePath());
    }

    return harnessSymbolMap;
  }

  /**
   * Generate build file for the path relative the workspace. Takes care of resolving symbols for
   * all source files matching the srcsGlob pattern.
   *
   * @param path relative to the workspace.
   * @param harnessSymbolMap having mapping from Harness classes to build paths.
   * @return optionally build file if source files are > 1.
   * @throws IOException
   * @throws FileNotFoundException
   */
  @VisibleForTesting
  protected Optional<BuildFile> generateBuildForModule(Path path, SymbolDependencyMap harnessSymbolMap)
      throws IOException, FileNotFoundException {
    // Setup classpath parser to get imports for java files for the module in context and try
    // resolving each of the imports.
    // Set "findBuildInParent" to false, as we only need import statements for the files in this folder
    // and don't care about the BUILD file paths.
    ClasspathParser classpathParser = this.packageParser.getClassPathParser();
    String parseClassPattern = path.toString().isEmpty() ? srcsGlob() : path.toString() + "/" + srcsGlob();
    classpathParser.parseClasses(parseClassPattern, false);

    Set<String> dependencies = new TreeSet<>();
    for (String importStatement : classpathParser.getUsedTypes()) {
      if (importStatement.startsWith("java.")) {
        continue;
      }

      Optional<String> resolvedSymbol = resolve(importStatement, harnessSymbolMap);

      // Skip the symbols from the same package. Resolved symbol starts with "//" and rest of it is just a path -
      // therefore, removing first two characters before comparing.
      if (resolvedSymbol.isPresent() && resolvedSymbol.get().substring(2).equals(path.toString())) {
        continue;
      }

      resolvedSymbol.ifPresent(dependencies::add);
      if (!resolvedSymbol.isPresent()) {
        logger.info("No build dependency found for " + importStatement);
      }
    }

    // Create build for the package.
    Set<String> sourceFiles = getSourceFiles(workspace().resolve(path));
    if (sourceFiles.isEmpty()) {
      return Optional.empty();
    }

    BuildFile buildFile = new BuildFile();
    JavaLibrary javaLibrary =
        new JavaLibrary(path.getFileName().toString(), DEFAULT_VISIBILITY, srcsGlob(), dependencies);
    buildFile.addJavaLibrary(javaLibrary);

    // Find main files in the folder and create java binary targets.
    for (String className : classpathParser.getMainClasses()) {
      JavaBinary javaBinary = new JavaBinary(className, DEFAULT_VISIBILITY,
          getPackageName(classpathParser) + "." + className, Collections.singleton(":" + path.getFileName().toString()),
          /*deps=*/Collections.emptySet());
      buildFile.addJavaBinary(javaBinary);
    }

    return Optional.of(buildFile);
  }

  /**
   * Find the dependency to include for the import statement.
   * @param importStatement to resolve.
   * @param harnessSymbolMap containing java symbols to build dependency map.
   * @return Optional build target name which has the import symbol.
   */
  private Optional<String> resolve(String importStatement, SymbolDependencyMap harnessSymbolMap) {
    Optional<String> resolvedSymbol = Optional.empty();

    // Look up symbol in the maven manifest override.
    if (mavenManifestOverride != null) {
      resolvedSymbol = mavenManifestOverride.getTarget(importStatement);
      if (resolvedSymbol.isPresent()) {
        return resolvedSymbol;
      }
    }

    // Look up symbol in the maven manifest.
    if (mavenManifest != null) {
      resolvedSymbol = mavenManifest.getTarget(importStatement);
      if (resolvedSymbol.isPresent()) {
        return resolvedSymbol;
      }
    }

    // Look up symbol in the harness symbol map.
    resolvedSymbol = harnessSymbolMap.getTarget(importStatement);
    if (resolvedSymbol.isPresent()) {
      return Optional.of("//" + resolvedSymbol.get());
    }

    return Optional.empty();
  }

  /**
   * Finds java source files present in the directory.
   * @param directory to search.
   * @return Set of java files in the input folder.
   * @throws IOException
   */
  private Set<String> getSourceFiles(Path directory) throws IOException {
    PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + directory + "/" + srcsGlob());
    Set<String> sourceFileNames = new HashSet<>();
    try (Stream<Path> paths =
             Files.find(directory, Integer.MAX_VALUE, (path, f) -> { return pathMatcher.matches(path); })) {
      paths.forEach(path -> sourceFileNames.add(path.getFileName().toString()));
    }
    return sourceFileNames;
  }

  private String getPackageName(ClasspathParser classpathParser) {
    Set<String> packageNames = classpathParser.getPackages();
    if (packageNames.size() == 0) {
      logger.error("No package name found for module: " + module());
      return "";
    }

    if (packageNames.size() > 1) {
      logger.error(
          "Package name not consistent across files in the module: " + module() + ". Found packages: " + packageNames);
    }

    return packageNames.stream().findFirst().get();
  }

  private boolean indexFileExists() {
    File f = new File(indexFilePath().toString());
    return f.exists();
  }

  private Path indexFilePath() {
    return options.hasOption("indexFile") ? Paths.get(options.getOptionValue("indexFile"))
                                          : workspace().resolve(BUILD_CLEANER_INDEX_FILE_NAME);
  }

  private Path workspace() {
    return options.hasOption("workspace") ? Paths.get(options.getOptionValue("workspace")) : Paths.get("");
  }

  private Path module() {
    return options.hasOption("module") ? Paths.get(options.getOptionValue("module")) : Paths.get("");
  }

  private String srcsGlob() {
    return options.hasOption("srcsGlob") ? options.getOptionValue("srcsGlob") : "*.java";
  }

  private Path mavenManifestFile() {
    return options.hasOption("mavenManifestFile") ? Paths.get(options.getOptionValue("mavenManifestFile"))
                                                  : Paths.get(workspace() + "/maven-manifest.json");
  }

  private Path mavenManifestOverrideFile() {
    return options.hasOption("mavenManifestOverrideFile")
        ? Paths.get(options.getOptionValue("mavenManifestOverrideFile"))
        : Paths.get(workspace() + "/maven-manifest-override.txt");
  }

  private String indexSourceGlob() {
    return options.hasOption("indexSourceGlob") ? options.getOptionValue("indexSourceGlob") : "**/src/**/*.java";
  }

  private CommandLine getCommandLineOptions(String[] args) {
    Options options = new Options();
    CommandLineParser parser = new DefaultParser();

    options.addOption(new Option(null, "workspace", true, "Workspace root"));
    options.addOption(new Option(
        null, "indexSourceGlob", true, "Pattern for source files to build index. Defaults to '**/src/main/**/*.java'"));
    options.addOption(new Option(null, "overrideIndex", true, "Override the existing index"));
    options.addOption(new Option(null, "module", true, "Relative path of the module from the workspace"));
    options.addOption(new Option(null, "srcsGlob", true, "Pattern to match for finding source files."));
    options.addOption(
        new Option(null, "indexFile", true, "Index file having cache, defaults to $workspace/.build-cleaner-index"));
    options.addOption(new Option(null, "mavenManifestFile", true,
        "Absolute path of the manifest file having java package to maven target mapping."
            + "Defaults to workspace/maven_manifest.json"));
    options.addOption(
        new Option(null, "mavenManifestOverrideFile", true, "Specify overrides for conflicting imports."));
    options.addOption(new Option(null, "recursive", true, "Generate BUILD files for all folders inside the module"));
    options.addOption(new Option(null, "findBuildInParent", true,
        "Don't assume build file is present in every folder, rather keep going up the tree until a build file is found. "
            + "This would not override the index file if already present."));

    CommandLine commandLineOptions = null;
    try {
      commandLineOptions = parser.parse(options, args);
    } catch (ParseException e) {
      logger.error("Command line parsing failed. {}", e.getMessage());
      System.exit(3);
    }
    return commandLineOptions;
  }
};
