/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.MicroserviceInterfaceTool.calculateStringHash;
import static io.harness.MicroserviceInterfaceTool.log;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.HarnessStringUtils;

import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;

@OwnedBy(HarnessTeam.CDP)
class MicroserviceDelegateInterfaceTool {
  private static final Map<String, String> internalClassMap = new HashMap<>();
  private static final List<String> ignoreClassPrefix = Lists.newArrayList("java.lang", "java.util", "int", "float",
      "byte", "boolean", "char", "short", "long", "double", "void", "com.google.common.collect");
  static {
    internalClassMap.put("io.harness.cvng.beans.SplunkValidationResponse.Histogram.Bar",
        "io.harness.cvng.beans.SplunkValidationResponse$Histogram$Bar");
  }

  public static void main(String[] args) {
    try {
      log("inner class map " + internalClassMap);
      Set<String> kryoDependencies = new HashSet<>();
      List<String> lines = IOUtils.readLines(
          MicroserviceDelegateInterfaceTool.class.getClassLoader().getResourceAsStream("kryo-registrations.txt"),
          StandardCharsets.UTF_8);
      log("classes to process: ");
      lines.stream().filter(line -> isNotEmpty(line)).forEach(line -> {
        String className = line.split(":")[1];
        if (!ignoreClass(className)) {
          log(className);
          kryoDependencies.add(className);
        }
      });

      Map<String, String> classToHash = computeDelegateKryoHashes(kryoDependencies);
      log("classes to hash" + classToHash);
      List<String> sortedClasses = classToHash.keySet().stream().sorted(String::compareTo).collect(Collectors.toList());
      List<String> sortedHashes = sortedClasses.stream().map(classToHash::get).collect(Collectors.toList());
      String concatenatedHashes = HarnessStringUtils.join(",", sortedHashes);
      String codebaseHash = Hashing.sha256().hashString(concatenatedHashes, StandardCharsets.UTF_8).toString();
      String message = String.format("Codebase Hash:%s", codebaseHash);
      log(message);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private static boolean ignoreClass(String className) {
    for (String prefix : ignoreClassPrefix) {
      if (className.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  static Map<String, String> computeDelegateKryoHashes(Set<String> kryoDependencies) throws Exception {
    Map<String, String> classesToHash = new HashMap<>();
    for (String kryoDependency : kryoDependencies) {
      Class<?> loadedClass;
      try {
        if (internalClassMap.containsKey(kryoDependency)) {
          kryoDependency = internalClassMap.get(kryoDependency);
        }
        loadedClass = MicroserviceDelegateInterfaceTool.class.getClassLoader().loadClass(kryoDependency);
      } catch (ClassNotFoundException e) {
        log("could not load " + kryoDependency + " trying as inner class");
        int lastDot = kryoDependency.lastIndexOf(".");
        kryoDependency = kryoDependency.substring(0, lastDot) + "$" + kryoDependency.substring(lastDot + 1);
        log("inner class is " + kryoDependency);
        loadedClass = MicroserviceDelegateInterfaceTool.class.getClassLoader().loadClass(kryoDependency);
      }
      classesToHash.put(kryoDependency, calculateStringHash(loadedClass));
    }
    return classesToHash;
  }
}
