/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.yaml;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.WRAPPER_OBJECT;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;
import static io.harness.annotations.dev.HarnessTeam.CDC;

@OwnedBy(CDC)
@Data
@Builder
@FieldNameConstants(innerTypeName = "BasicServiceKeys")
@JsonTypeInfo(use = NAME, include = WRAPPER_OBJECT)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonTypeName("service")
public class BasicService {
    @EntityName String name;
    @EntityIdentifier String identifier;

    String description;
    Map<String, String> tags;

    String orgIdentifier;
    String projectIdentifier;
}
