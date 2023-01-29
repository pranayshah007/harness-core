/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;

@Slf4j
public class YamlPropertyLoaderFactory extends DefaultPropertySourceFactory {
  @Override
  public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {
    if (resource == null) {
      return super.createPropertySource(name, resource);
    }
    try {
      return new YamlPropertySourceLoader().load(resource.getResource().getFilename(), resource.getResource()).get(0);
    } catch (Exception e) {
      log.error("File not found {0}", resource.getResource().getFilename(), e);
    }
    return super.createPropertySource(name, resource);
  }
}
