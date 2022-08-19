package io.harness.buildcleaner.bazel;

import java.util.SortedSet;

public class WriteUtil {
  public static final String INDENTATION = "    ";

  public static void updateResponseWithSet(SortedSet<String> collection, String name, StringBuilder response) {
    response.append(INDENTATION).append(name).append(" = [");
    if (collection.size() > 1) {
      response.append("\n");
    }
    for (String entity : collection) {
      if (collection.size() > 1) {
        response.append(INDENTATION).append(INDENTATION);
      }
      response.append("\"").append(entity).append("\"");
      if (collection.size() > 1) {
        response.append(",\n");
      }
    }
    if (collection.size() > 1) {
      response.append(INDENTATION);
    }
    response.append("],\n");
  }
}
