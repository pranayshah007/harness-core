package io.harness.buildcleaner.bazel;

import static io.harness.buildcleaner.bazel.WriteUtil.INDENTATION;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Set;

public class JavaLibrary {
  private final String name;
  private final String visibility;
  private final String srcsGlob;
  private ImmutableSortedSet<String> runTimeDeps;
  private ImmutableSortedSet<String> deps;

  public JavaLibrary(String name, String visibility, String srcsGlob, Set<String> deps) {
    this.name = name;
    this.visibility = visibility;
    this.srcsGlob = srcsGlob;
    this.deps = ImmutableSortedSet.copyOf(deps);
  }

  public String getName() {
    return this.name;
  }

  /* Returns the deps section as a s string. Eg:
    deps = [
      "dependency1",
      "dependency2",
    ]
  */
  public String getDepsSection() {
    StringBuilder response = new StringBuilder();
    WriteUtil.updateResponseWithSet(this.deps, "deps", response, false);
    return response.toString();
  }

  public ImmutableSet<String> getDeps() {
    return (ImmutableSet<String>) this.deps;
  }

  public String toString() {
    StringBuilder response = new StringBuilder();
    response.append("java_library(\n");

    // Add name.
    response.append(INDENTATION).append("name = \"").append(name).append("\",\n");

    // Add srcs.
    response.append(INDENTATION).append("srcs = glob([\"").append(srcsGlob).append("\"]),\n");

    // Add visibility.
    response.append(INDENTATION).append("visibility = [\"//visibility:public\"],\n");

    // Add deps.
    WriteUtil.updateResponseWithSet(deps, "deps", response, true);

    response.append(")");
    return response.toString();
  }
}
