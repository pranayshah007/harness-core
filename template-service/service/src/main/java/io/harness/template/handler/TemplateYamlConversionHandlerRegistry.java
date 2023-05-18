/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.handler;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ngexception.NGTemplateException;

import com.google.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@OwnedBy(HarnessTeam.CDC)
@Singleton
public class TemplateYamlConversionHandlerRegistry {
  private final Map<String, YamlConversionHandler> registry = new ConcurrentHashMap<>();

  public void register(String templateType, YamlConversionHandler yamlConversionHandler) {
    if (registry.containsKey(templateType)) {
      throw new NGTemplateException("YamlConversionHandler already Registered with type: " + templateType);
    }
    registry.put(templateType, yamlConversionHandler);
  }

  public YamlConversionHandler obtain(String templateType) {
    if (registry.containsKey(templateType)) {
      return registry.get(templateType);
    }
    throw new NGTemplateException("No yamlConversionHandler registered for type: " + templateType);
  }
}
