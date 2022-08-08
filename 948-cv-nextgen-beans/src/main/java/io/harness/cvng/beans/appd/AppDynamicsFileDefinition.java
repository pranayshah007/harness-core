/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.appd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppDynamicsFileDefinition implements Comparable<AppDynamicsFileDefinition> {
  String name;
  FileType type;

  @Override
  public int compareTo(@NotNull AppDynamicsFileDefinition o) {
    return name.compareTo(o.name);
  }

  public enum FileType {
    @JsonProperty("leaf") @SerializedName("leaf") LEAF,
    @JsonProperty("folder") @SerializedName("folder") FOLDER
  }
}
