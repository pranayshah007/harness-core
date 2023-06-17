/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.serializer.jackson;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

// This module handles the edge cases when we need a string to be surrounded by quotes in the compiled yaml.
@OwnedBy(HarnessTeam.PIPELINE)
public class EdgeCaseRegexModule extends SimpleModule {
  public EdgeCaseRegexModule() {
    addSerializer(String.class, new EdgeCaseRegexStringSerializer());
    addSerializer(TextNode.class, new EdgeCaseRegexTextSerializer());
    addSerializer(ArrayNode.class, new EdgeCaseRegexArraySerializer());
    addSerializer(ObjectNode.class, new EdgeCaseRegexObjectSerializer());
  }
}
