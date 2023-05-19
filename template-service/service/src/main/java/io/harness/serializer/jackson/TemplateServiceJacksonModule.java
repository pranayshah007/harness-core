/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.jackson;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.serializer.json.serializers.YamlOutputPropertiesSerializer;
import io.harness.pms.serializer.json.serializers.YamlPropertiesSerializer;

import com.fasterxml.jackson.databind.module.SimpleModule;

@OwnedBy(CDC)
public class TemplateServiceJacksonModule extends SimpleModule {
  public TemplateServiceJacksonModule() {
    addSerializer(YamlProperties.class, new YamlPropertiesSerializer());
    addSerializer(YamlOutputProperties.class, new YamlOutputPropertiesSerializer());
  }
}
