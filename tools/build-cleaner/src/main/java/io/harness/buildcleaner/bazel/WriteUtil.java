package io.harness.buildcleaner.bazel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedSet;

import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

public class WriteUtil {
  public static final String INDENTATION = "    ";

  public static void updateResponseWithSet(SortedSet<String> collection, String name, StringBuilder response,
                                           boolean leadingIndent) {
    if (leadingIndent) {
      response.append(INDENTATION);
    }
    response.append(name).append(" = [");
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

  public static void writeUpdatedFile(Path filePath, String update) throws IOException {
    Files.writeString(filePath, update, TRUNCATE_EXISTING);
  }
}
