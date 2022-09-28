/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SizeUtils {
  public static int convertNonSeriaziableObjectToBytes(Object object) {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    ObjectMapper mapper = new ObjectMapper();
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    try {
      mapper.writeValue(os, object);
    } catch (IOException e) {
      log.error("Exception occurred while calculating the size");
    }

    return os.size();
  }
}
