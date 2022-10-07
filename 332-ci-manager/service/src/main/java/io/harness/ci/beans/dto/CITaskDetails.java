/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.beans.dto;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn("harnessci")
@Entity(value = "citaskdetails", noClassnameStored = true)
@Document("citaskdetails")
@HarnessEntity(exportable = true)
@TypeAlias("ciTaskDetails")
public class CITaskDetails {
  @Id @org.mongodb.morphia.annotations.Id String uuid;
  String taskId;
  String accountId;
  String stageExecutionId;
  String delegateId;
  String taskType;
}
