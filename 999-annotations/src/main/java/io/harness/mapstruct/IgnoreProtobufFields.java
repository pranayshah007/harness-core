/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mapstruct;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.mapstruct.Mapping;

@Retention(RetentionPolicy.CLASS)
@Mapping(target = "mergeUnknownFields", ignore = true)
@Mapping(target = "mergeFrom", ignore = true)
@Mapping(target = "clearOneof", ignore = true)
@Mapping(target = "clearField", ignore = true)
@Mapping(target = "allFields", ignore = true)
@Mapping(target = "unknownFields", ignore = true)
public @interface IgnoreProtobufFields {}
