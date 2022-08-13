package io.harness.buildcleaner.bazel;

import static io.harness.buildcleaner.bazel.WriteUtil.INDENTATION;

import com.google.common.collect.ImmutableSortedSet;
import java.util.Set;

public class JavaBinary {
  private final String name;
  private final String visibility;
  private final String mainClass;
  private ImmutableSortedSet<String> srcs;
  private ImmutableSortedSet<String> runTimeDeps;
  private ImmutableSortedSet<String> deps;

  public JavaBinary(String name, String visibility, String mainClass, Set<String> runTimeDeps, Set<String> deps) {
    this.name = name;
    this.visibility = visibility;
    this.mainClass = mainClass;
    this.runTimeDeps = ImmutableSortedSet.copyOf(runTimeDeps);
    this.deps = ImmutableSortedSet.copyOf(deps);
  }

  public String toString() {
    StringBuilder response = new StringBuilder();
    response.append("java_binary(\n");

    // Add name.
    response.append(INDENTATION).append("name = \"").append(name).append("\",\n");

    // Add main_class.
    response.append(INDENTATION).append("main_class = \"").append(mainClass).append("\",\n");

    // Add visibility.
    response.append(INDENTATION).append("visibility = [\"//visibility:public\"],\n");

    // Add runtime_deps.
    WriteUtil.updateResponseWithSet(runTimeDeps, "runtime_deps", response, true);

    // Add deps.
    WriteUtil.updateResponseWithSet(deps, "deps", response, true);

    response.append(")");
    return response.toString();
  }
}
