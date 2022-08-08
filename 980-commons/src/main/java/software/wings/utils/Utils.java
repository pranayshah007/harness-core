/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.exception.InvalidRequestException;

import software.wings.beans.NameValuePair;
import software.wings.beans.NameValuePair.Yaml;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author rktummala on 10/11/17
 */
@Slf4j
public class Utils {
  private static final String MULTIPLE_FILES_DELIMITER = ",";
  private static final String UNDERSCORE = "_";
  private static final String DASH = "-";

  public static String generatePath(String delimiter, boolean endsWithDelimiter, String... elements) {
    StringBuilder builder = new StringBuilder();
    for (String element : elements) {
      builder.append(element);
      builder.append(delimiter);
    }

    if (endsWithDelimiter) {
      return builder.toString();
    } else {
      return builder.substring(0, builder.length() - 1);
    }
  }

  public static Map<String, Object> toProperties(List<NameValuePair> nameValuePairList) {
    // do not use Collectors.toMap, as it throws NPE if any of the value is null
    // here we do expect value to be null in some cases.
    return nameValuePairList.stream().collect(
        HashMap::new, (m, v) -> m.put(v.getName(), v.getValue()), HashMap::putAll);
  }

  public static List<NameValuePair.Yaml> getSortedNameValuePairYamlList(List<NameValuePair.Yaml> yamlList) {
    if (isEmpty(yamlList)) {
      return yamlList;
    }

    return yamlList.stream()
        .sorted(new Comparator<Yaml>() {
          @Override
          public int compare(Yaml o1, Yaml o2) {
            return o1.getName().compareTo(o2.getName());
          }
        })
        .collect(toList());
  }

  public static <T extends Enum<T>> T getEnumFromString(Class<T> enumClass, String stringValue) {
    if (enumClass != null && stringValue != null) {
      try {
        return Enum.valueOf(enumClass, stringValue.trim().toUpperCase());
      } catch (IllegalArgumentException ex) {
        try {
          return Enum.valueOf(enumClass, stringValue.trim());
        } catch (IllegalArgumentException enumNotFound) {
          throw new InvalidRequestException(format("Cannot find the value: %s", stringValue.trim()));
        }
      }
    }
    return null;
  }

  public static String normalize(String input) {
    return input.replace('/', '_');
  }

  public static String getStringFromEnum(Enum enumObject) {
    if (enumObject != null) {
      return enumObject.name();
    }
    return null;
  }

  /**
   * This method gets the default name, checks if another entry exists with the same name, if exists, it parses and
   * extracts the revision and creates a name with the next revision.
   */
  public static String getNameWithNextRevision(List<String> existingNames, String defaultName) {
    String existingName = "";
    int maxRevision = -1;
    for (String existing : existingNames) {
      int revision;
      if (existing.equals(defaultName)) {
        revision = 0;
      } else if (existing.startsWith(defaultName)) {
        String rev = existing.substring(defaultName.length() + 1);
        try {
          revision = Integer.parseInt(rev);
        } catch (NumberFormatException ex) {
          revision = -1;
        }
      } else {
        revision = -1;
      }
      if (revision > maxRevision) {
        maxRevision = revision;
        existingName = existing;
      }
    }

    if (isBlank(existingName)) {
      return defaultName;
    }

    int revision = maxRevision + 1;
    return defaultName + "-" + revision;
  }

  public static Type[] getParameterizedTypes(Object object) {
    Type superclassType = object.getClass().getGenericSuperclass();
    if (!ParameterizedType.class.isAssignableFrom(superclassType.getClass())) {
      return null;
    }
    return ((ParameterizedType) superclassType).getActualTypeArguments();
  }

  public static String escapifyString(String input) {
    String str = input.replaceAll("`", "\\\\`").replaceAll("\"", "\\\\\"");

    if (str.endsWith("\\")) {
      str = str.substring(0, str.length() - 1) + "\\\\";
    }
    return str;
  }

  public static String urlEncode(String decoded) {
    String encoded = decoded;
    try {
      if (decoded != null) {
        encoded = URLEncoder.encode(decoded, StandardCharsets.UTF_8.name());
      }
    } catch (UnsupportedEncodingException e) {
      // Should not happen and ignore.
    }
    return encoded;
  }

  public static String urlDecode(String encoded) {
    String decoded = encoded;
    try {
      if (encoded != null) {
        decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8.name());
      }
    } catch (UnsupportedEncodingException e) {
      // Should not happen and ignore.
    }
    return decoded;
  }

  public static boolean isJSONValid(String jsonInString) {
    try {
      final ObjectMapper mapper = new ObjectMapper();
      mapper.readTree(jsonInString);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  public static <T> List<T> safe(List<T> list) {
    return list == null ? Collections.emptyList() : list;
  }

  public static String emptyIfNull(String value) {
    return value == null ? "" : value;
  }

  private static String normalizeMultipleFilesFilePath(String filePath) {
    // Transform from filePath <,file1,file2,file3,> to <file1,file2,file3>
    return Arrays.stream(filePath.split(MULTIPLE_FILES_DELIMITER))
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.joining(MULTIPLE_FILES_DELIMITER));
  }

  private static boolean validateFilePath(String value, String originalValue) {
    // expressions like <${valid}, ${missingValue}> could lead to result like <value, null>
    if (isEmpty(value) || value.equals("null")) {
      throw new InvalidRequestException(
          "Invalid file path '" + value + "' after resolving value '" + originalValue + "'");
    }

    return true;
  }

  public static List<String> splitCommaSeparatedFilePath(@NotNull String filePath) {
    String renderedFilePath = normalizeMultipleFilesFilePath(filePath);
    return Arrays.stream(renderedFilePath.split(MULTIPLE_FILES_DELIMITER))
        .map(String::trim)
        .filter(value -> validateFilePath(value, filePath))
        .collect(Collectors.toList());
  }

  public static String normalizeIdentifier(String identifier) {
    return UNDERSCORE + identifier.replaceAll("[^a-zA-Z0-9_$]", UNDERSCORE);
  }

  public static String uuidToIdentifier(String uuid) {
    return UNDERSCORE + uuid.replaceAll(DASH, UNDERSCORE);
  }
}
