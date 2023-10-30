/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.serializer;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
public class CgEntityIdDeserializer extends KeyDeserializer {
  @Override
  public CgEntityId deserializeKey(String s, DeserializationContext deserializationContext) throws IOException {
    String id = null;
    NGMigrationEntityType type = null;
    if (StringUtils.isNotBlank(s)) {
      id = s.substring(14, s.indexOf(','));
      type = NGMigrationEntityType.valueOf(s.substring(s.indexOf("type=") + 5, s.length() - 1));
    }
    return CgEntityId.builder().id(id).type(type).build();
  }
}
