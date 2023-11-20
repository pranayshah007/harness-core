/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class JacksonUtils {
  private JacksonUtils() {}

  private static final ObjectMapper mapper = new ObjectMapper();

  public static <T> List<T> readValue(String entities, Class<?> clazz) {
    try {
      Class<?> clz = Class.forName(clazz.getName());
      JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, clz);
      return mapper.readValue(entities, type);
    } catch (ClassNotFoundException | JsonProcessingException e) {
      log.error("Error in readValue json string to corresponding list<clazz> pojo's. Error = {}", e.getMessage(), e);
      throw new UnexpectedException(
          "Error in readValue json string to corresponding list<clazz> pojo's. Error = " + e.getMessage());
    }
  }

  public static <T> List<T> convert(Object entities, Class<?> clazz) {
    return convert(mapper, entities, clazz);
  }

  public static <T> List<T> convert(ObjectMapper mapper, Object entities, Class<?> clazz) {
    try {
      Class<?> clz = Class.forName(clazz.getName());
      JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, clz);
      return mapper.convertValue(entities, type);
    } catch (ClassNotFoundException e) {
      log.error("Error in convert json string to corresponding list<clazz> pojo's. Error = {}", e.getMessage(), e);
      throw new UnexpectedException(
          "Error in convert json string to corresponding list<clazz> pojo's. Error = " + e.getMessage());
    }
  }

  public static String write(Object obj) {
    try {
      return mapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      log.error("Error in convert object to string. Error = {}", e.getMessage(), e);
      throw new UnexpectedException(e.getMessage());
    }
  }
}
