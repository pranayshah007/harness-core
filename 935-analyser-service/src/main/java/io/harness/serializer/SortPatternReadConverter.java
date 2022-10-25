/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.query.SortPattern;

import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@OwnedBy(HarnessTeam.PIPELINE)
@ReadingConverter
public class SortPatternReadConverter implements Converter<String, SortPattern> {
  @Override
  public SortPattern convert(String jsonString) {
    if (jsonString == null) {
      return null;
    }
    Map<String, Object> objectMap = JsonUtils.asMap(jsonString);
    return new SortPattern(objectMap);
  }
}
