/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.buildcleaner;

import io.harness.buildcleaner.common.SymbolDependencyMap;
import io.harness.buildcleaner.javaparser.ClassMetadata;
import io.harness.buildcleaner.javaparser.ClasspathParser;
import io.harness.buildcleaner.javaparser.PackageParser;
import io.harness.buildcleaner.proto.ProtoBuildMapper;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;

@Slf4j
public class NonPrimitivesParser {
  private CommandLine options;
  private PackageParser packageParser;
  private static final String BUILD_CLEANER_INDEX_FILE_NAME = ".build-cleaner-index";
  private SymbolDependencyMap harnessSymbolMap;

  public NonPrimitivesParser(String[] args) {
    log.info(" Rcvd args : " + args);
    this.options = getCommandLineOptions(args);
    log.info(" workspace : " + workspace());
    this.packageParser = new PackageParser(workspace());
    try {
      log.info("Building harness symbol map");
      this.harnessSymbolMap = buildHarnessSymbolMap();
      log.info("Built harness symbol map");
    } catch (ClassNotFoundException ex) {
      log.error(" Rcvd an exception CNFE : " + ex);
    } catch (IOException ex) {
      log.error(" Rcvd an exception IOE - line 51 : " + ex);
    }
  }

  public List<String> findImplementation(Class<?> interfaze, ClassLoader classLoader) {
    List<String> implementations = new ArrayList<>();
    String path = harnessSymbolMap.getSymbolToTargetMap().get(interfaze.getName());
    String absPath = "/Users/raghavendramurali/harness-core/" + path;
    File directory = new File(absPath);
    if (directory.exists() && directory.isDirectory()) {
      try {
        File[] files = directory.listFiles();
        for (File file : files) {
          if (!file.isFile()) {
            continue;
          }
          String fileName = file.getName();
          log.info(" Checking if fileName : " + fileName);
          if (fileName.endsWith(".java")) {
            String clsName = interfaze.getPackageName() + "." + fileName.substring(0, fileName.length() - 5);
            Class<?> parsedClass = classLoader.loadClass(clsName);
            if (parsedClass.getName() != interfaze.getName()) {
              if (interfaze.isAssignableFrom(parsedClass)) {
                log.info("Class : " + parsedClass.getName() + " implements interface : " + interfaze.getName());
                implementations.add(parsedClass.getName());
              }
            }
          }
        }
      } catch (NullPointerException e) {
        log.error(" Rcvd an exception NPE : " + e);
      } catch (ClassNotFoundException e) {
        log.error(" Rcvd an exception CNFE : " + e);
      }
    }

    return implementations;
  }

  public Set<String> getNonPrimitiveVariableTypes(String filePath, String clsName) throws IOException {
    // Read the Java file content
    log.info(" Parsing file : " + filePath);
    Set<String> nonPrimitiveVariableTypes = new TreeSet<>();
    try {
      String fileContent = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);

      // Compile the Java file
      JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
      StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
      JavaFileObject javaFileObject = new NonPrimitivesParser.JavaSourceFromString(clsName, fileContent);
      Iterable<? extends JavaFileObject> compilationUnits = List.of(javaFileObject);
      compiler.getTask(null, fileManager, null, null, null, compilationUnits).call();

      // Parse the compiled classes and extract non-primitive variable types
      ClassLoader classLoader = ClassLoader.getSystemClassLoader();
      Class<?> parsedClass = null;

      parsedClass = classLoader.loadClass(clsName);
      for (var field : parsedClass.getDeclaredFields()) {
        log.info(" field is : " + field);
        if (!field.getType().isPrimitive()) {
          nonPrimitiveVariableTypes.add(field.getType().getSimpleName());
        }
      }

      for (var method : parsedClass.getDeclaredMethods()) {
        String annotatedReturnType = method.getAnnotatedReturnType().toString();
        if (annotatedReturnType.contains("<")) {
          int indexOfStart = annotatedReturnType.indexOf("<");
          String subS = annotatedReturnType.substring(indexOfStart + 1);
          subS = subS.substring(0, subS.length() - 1);
          log.info(" Annotated return type is : " + subS);
          nonPrimitiveVariableTypes.add(subS);
        }
        if (!method.getReturnType().isPrimitive()) {
          nonPrimitiveVariableTypes.add(method.getReturnType().getName());
        }
        var params = method.getParameterTypes();
        for (var param : params) {
          if (!param.isPrimitive()) {
            nonPrimitiveVariableTypes.add(param.getName());
          }
        }
      }
      nonPrimitiveVariableTypes.addAll(findImplementation(parsedClass, classLoader));

    } catch (IOException e) {
      log.error(" Rcvd an exception IOE - line 131 : " + e);
    } catch (ClassNotFoundException e) {
      log.error(" Rcvd an exception CNFE : " + e);
    }
    return nonPrimitiveVariableTypes;
  }

  static class JavaSourceFromString extends SimpleJavaFileObject {
    final String code;

    JavaSourceFromString(String name, String code) {
      super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
      this.code = code;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return code;
    }
  }

  public Set<String> getAllImportNonPrimitives(String classPath) {
    CompilationUnit cu;
    JavaParser javaParser = packageParser.getJavaParser();
    log.info(" The classPath is : " + classPath);
    Set<String> usedTypes = new TreeSet<>();
    try {
      ParseResult<CompilationUnit> result = javaParser.parse(Paths.get(classPath));
      if (result.isSuccessful()) {
        cu = result.getResult().get();
      } else {
        throw new ParseProblemException(result.getProblems());
      }
    } catch (Exception exception) {
      return usedTypes;
    }

    cu.findAll(ImportDeclaration.class).forEach(id -> {
      String name = id.getNameAsString();
      if ((name.startsWith("io.harness") || name.startsWith("software.wings")) && (!name.contains("annotation"))) {
        if (id.isAsterisk()) {
          usedTypes.add(name);
        } else if (id.isStatic()) {
          String staticPackage = name.substring(0, name.lastIndexOf('.'));
          usedTypes.add(staticPackage);
        } else {
          usedTypes.add(name);
        }
      }
    });

    return usedTypes;
  }

  public static void main(String[] args) throws IOException {
    log.info("Starting App : " + args);
    Set<String> finalParams = new TreeSet<>();
    NonPrimitivesParser nonPrimitivesParser = new NonPrimitivesParser(args);
    Queue<String> queue = new LinkedList<>();
    // queue.add("io.harness.delegate.k8s.K8sRequestHandler");
    queue.add("io.harness.delegate.task.k8s.K8sTaskNG");
    while (!queue.isEmpty()) {
      String nonPrim = queue.remove();
      if ((nonPrim.startsWith("io.harness") || nonPrim.startsWith("software.wings"))
          && (!finalParams.contains(nonPrim))) {
        log.info("Class is : " + nonPrim);
        if (!nonPrimitivesParser.harnessSymbolMap.getSymbolToTargetMap().containsKey(nonPrim)) {
          continue;
        }
        finalParams.add(nonPrim);
        String clsPath = nonPrimitivesParser.harnessSymbolMap.getSymbolToTargetMap().get(nonPrim) + "/";
        int lastDotIndx = nonPrim.lastIndexOf(".");
        if (lastDotIndx > 0) {
          String cls = nonPrim.substring(lastDotIndx + 1);
          String newPath = "/Users/raghavendramurali/harness-core/" + clsPath + cls + ".java";
          try {
            Set<String> newParams = nonPrimitivesParser.getNonPrimitiveVariableTypes(newPath, nonPrim);
            queue.addAll(newParams);
          } catch (Exception ex) {
          }
        }
      }
    }

    try {
      File file = new File("/Users/raghavendramurali/ng_task_pattern.txt");
      if (!file.exists()) {
        file.createNewFile();
      }

      FileWriter writer = new FileWriter(file);

      for (String param : finalParams) {
        log.info(" param is : " + param);
        // int lastIndex = param.lastIndexOf(".");
        // String subS = param.substring(lastIndex + 1);
        // String pattern = subS + "\\.class,\\s[0-9]*" + System.lineSeparator();
        String pattern = param + System.lineSeparator();
        writer.write(pattern);
      }

      writer.close();
    } catch (IOException e) {
    }
  }

  protected SymbolDependencyMap buildHarnessSymbolMap() throws IOException, ClassNotFoundException {
    final var harnessSymbolMap = initDependencyMap();

    // if symbol map exists and no options specified then don't update it
    if (!harnessSymbolMap.getSymbolToTargetMap().isEmpty() && !options.hasOption("indexSourceGlob")) {
      return harnessSymbolMap;
    }

    // log.info("Creating index using sources matching: {}", indexSourceGlob());

    // Parse proto and BUILD files to construct Proto specific java symbols to proto target map.
    final ProtoBuildMapper protoBuildMapper = new ProtoBuildMapper(workspace());
    protoBuildMapper.protoToBuildTargetDependencyMap(indexSourceGlob(), harnessSymbolMap);

    // Parse java classes.
    final ClasspathParser classpathParser = packageParser.getClassPathParser();
    classpathParser.parseClasses(indexSourceGlob(), assumedPackagePrefixesWithBuildFile());

    // Update symbol dependency map with the parsed java code.
    final Set<ClassMetadata> fullyQualifiedClassNames = classpathParser.getFullyQualifiedClassNames();
    for (ClassMetadata metadata : fullyQualifiedClassNames) {
      harnessSymbolMap.addSymbolTarget(metadata.getFullyQualifiedClassName(), metadata.getBuildModulePath());
    }

    harnessSymbolMap.serializeToFile(indexFilePath().toString());
    // log.info("Index creation complete.");

    return harnessSymbolMap;
  }

  @NonNull
  private SymbolDependencyMap initDependencyMap() throws IOException, ClassNotFoundException {
    if (indexFileExists() && !options.hasOption("overrideIndex")) {
      // log.info("Loading the existing index file {} to init dependency map", indexFilePath());
      return SymbolDependencyMap.deserializeFromFile(indexFilePath().toString());
    } else {
      return new SymbolDependencyMap();
    }
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

  private String indexSourceGlob() {
    return options.hasOption("indexSourceGlob") ? options.getOptionValue("indexSourceGlob") : "**/src/**/*";
  }

  private Set<String> assumedPackagePrefixesWithBuildFile() {
    return options.hasOption("assumedPackagePrefixesWithBuildFile")
        ? new HashSet<String>(Arrays.asList(options.getOptionValue("assumedPackagePrefixesWithBuildFile").split(",")))
        : new HashSet<String>();
  }

  private CommandLine getCommandLineOptions(String[] args) {
    Options options = new Options();
    CommandLineParser parser = new DefaultParser();

    options.addOption(new Option(null, "workspace", true, "Workspace root"));
    options.addOption(new Option(null, "module", true, "Relative path of the module from the workspace"));
    options.addOption(new Option(null, "assumedPackagePrefixesWithBuildFile", true,
        "Comma separate list of module prefixes for which we can assume BUILD file to be present. "
            + "Set to 'all' if need same behavior for all folders"));

    CommandLine commandLineOptions = null;
    try {
      commandLineOptions = parser.parse(options, args);
    } catch (ParseException e) {
      log.error(" Rcvd parse excpt : " + e.toString());
      System.exit(3);
    }
    return commandLineOptions;
  }
}
