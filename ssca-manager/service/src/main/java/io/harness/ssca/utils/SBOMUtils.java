/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.utils;

import io.harness.exception.InvalidArgumentsException;
import io.harness.ssca.beans.CyclonedxDTO;
import io.harness.ssca.beans.SbomDTO;
import io.harness.ssca.beans.SpdxDTO;
import io.harness.ssca.normalize.SbomFormat;

import com.google.gson.Gson;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Strings;
@Slf4j
public class SBOMUtils {
  public static String EXTERNAL_REF_CATEGORY_PURL = "PACKAGE-MANAGER";
  public static char EXTERNAL_REF_LOCATOR_DELIM_PRIMARY = ':';
  public static char EXTERNAL_REF_LOCATOR_DELIM_SECONDAY = '/';
  public static char PACKAGE_VERSION_DELIM = '.';
  public static String PACKAGE_VERSION_EPOCH_DELIM = ":";
  public static String LICENSE_REF_DELIM = "LicenseRef-";
  public static List<String> NO_ASSERTION_LIST = List.of("NO_ASSERTION", "NOASSERTION");

  public static List<Integer> getVersionInfo(String packageVersion) {
    int major = -1;
    int minor = -1;
    int patch = -1;
    if (packageVersion == null) {
      return Arrays.asList(major, minor, patch);
    }
    String[] splitVersion = Strings.split(packageVersion, PACKAGE_VERSION_DELIM);
    if (splitVersion.length < 3) {
      return Arrays.asList(major, minor, patch);
    }
    if (packageVersion.contains(PACKAGE_VERSION_EPOCH_DELIM)) {
      splitVersion = packageVersion.split(PACKAGE_VERSION_EPOCH_DELIM, 2);
      splitVersion = splitVersion[1].split("\\.", 3);
    } else {
      splitVersion = packageVersion.split("\\.", 3);
    }

    if (splitVersion[2].contains("-")) {
      splitVersion[2] = Strings.split(splitVersion[2], '-')[0];
    }
    if (splitVersion.length != 3) {
      return Arrays.asList(major, minor, patch);
    }
    major = isVersionInt(splitVersion[0]);
    minor = isVersionInt(splitVersion[1]);
    patch = isVersionInt(splitVersion[2]);

    if (major == -1 || minor == -1 || patch == -1) {
      major = -1;
      minor = -1;
      patch = -1;
      return Arrays.asList(major, minor, patch);
    }

    return Arrays.asList(major, minor, patch);
  }

  private static int isVersionInt(String version) {
    try {
      int res = Integer.parseInt(version);
      return res;
    } catch (Exception e) {
      return -1;
    }
  }

  public static SbomDTO getSbomDTO(byte[] sbomData, String format) {
    if (format.equals(SbomFormat.SPDX_JSON.getName())) {
      SpdxDTO spdxDTO = new Gson().fromJson(new String(sbomData), SpdxDTO.class);
      return spdxDTO;
    } else if (format.equals(SbomFormat.CYCLONEDX.getName())) {
      CyclonedxDTO cyclonedxDTO = new Gson().fromJson(new String(sbomData), CyclonedxDTO.class);
      return cyclonedxDTO;
    } else {
      throw new InvalidArgumentsException(String.format("Invalid format: %s", format));
    }
  }

  public static String getSbomVersion(SbomDTO sbomDTO) {
    if (sbomDTO.getType() == SbomFormat.SPDX_JSON) {
      return ((SpdxDTO) sbomDTO).getSpdxVersion();
    } else if (sbomDTO.getType() == SbomFormat.CYCLONEDX) {
      return ((CyclonedxDTO) sbomDTO).getSpecVersion();
    } else {
      throw new InvalidArgumentsException(String.format("Invalid format: %s", sbomDTO.getType()));
    }
  }

  public static List<String> processExpression(String expression) {
    expression = expression.replaceAll("[(]", "( ");
    expression = expression.replaceAll("[)]", " )");

    String[] tokens = expression.split(" ");
    List<String> licenses = new ArrayList<>();
    for (String token : tokens) {
      if (!token.equals("(") && !token.equals(")") && !token.equals("AND") && !token.equals("OR")
          && !token.equals("WITH")) {
        licenses.add(token);
      }
    }
    return licenses;
  }

  public static ZonedDateTime parseDateTime(String input) {
    DateTimeFormatter[] formatters = {
        DateTimeFormatter.ISO_DATE_TIME, // ISO 8601 / RFC3339 format
        DateTimeFormatter.ISO_LOCAL_DATE_TIME, // ISO 8601 date and time without time zone
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss") // Custom format
    };

    ZonedDateTime zonedDateTime = null;
    for (DateTimeFormatter formatter : formatters) {
      try {
        zonedDateTime = ZonedDateTime.parse(input, formatter);
        break;
      } catch (DateTimeParseException e) {
        log.info(String.format("Skipping format %s", formatter.toString()));
      }
    }

    if (zonedDateTime != null) {
      return zonedDateTime;
    } else {
      log.error(String.format("Unable to parse the input date and time string %s", input));
      return null;
    }
  }
}
