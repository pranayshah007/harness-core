/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.spotinst.model;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@RecasterAlias("io.harness.spotinst.model.ElastiGroup")
public class ElastiGroup implements Cloneable {
  private String id;
  private String name;
  private ElastiGroupCapacity capacity;

  @Override
  public ElastiGroup clone() {
    return ElastiGroup.builder()
        .id(id)
        .name(name)
        .capacity(ElastiGroupCapacity.builder()
                      .target(capacity.getTarget())
                      .minimum(capacity.getMinimum())
                      .maximum(capacity.getMaximum())
                      .build())
        .build();
  }
}
