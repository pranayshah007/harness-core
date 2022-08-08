/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml;

import java.io.BufferedReader;
import java.io.StringReader;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;

@Slf4j
public class YamlUtils {
  public static String cleanupYaml(String yaml) {
    // instead of removing the first line - we should remove any line that starts with two exclamation points
    return cleanUpDoubleExclamationLines(yaml);
  }

  private static String cleanUpDoubleExclamationLines(String content) {
    StringBuilder sb = new StringBuilder();

    BufferedReader bufReader = new BufferedReader(new StringReader(content));

    String line;

    try {
      while ((line = bufReader.readLine()) != null) {
        String trimmedLine = line.trim();

        // check for line starting with two exclamation points
        if (trimmedLine.length() >= 2 && trimmedLine.charAt(0) == '!' && trimmedLine.charAt(1) == '!') {
          continue;
        } else {
          // we need to remove lines BUT we have to add the dash to the NEXT line!
          if (trimmedLine.length() >= 4 && trimmedLine.charAt(0) == '-' && trimmedLine.charAt(1) == ' '
              && trimmedLine.charAt(2) == '!' && trimmedLine.charAt(3) == '!') {
            line = bufReader.readLine();
            if (line != null) {
              char[] chars = line.toCharArray();

              for (int i = 0; i < chars.length; i++) {
                if (chars[i] != ' ') {
                  if (i >= 2) {
                    chars[i - 2] = '-';
                    sb.append(new String(chars)).append('\n');
                    break;
                  }
                }
              }
            }
            continue;
          }
        }

        sb.append(line).append('\n');
      }
    } catch (Exception e) {
      log.error("", e);
    }

    return sb.toString();
  }

  public static DumperOptions getDumperOptions() {
    DumperOptions dumpOpts = new DumperOptions();
    // dumpOpts.setPrettyFlow(true);
    dumpOpts.setPrettyFlow(false); // keeps the empty square brackets together
    dumpOpts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    dumpOpts.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
    dumpOpts.setSplitLines(false);
    dumpOpts.setIndent(2);

    return dumpOpts;
  }
}
