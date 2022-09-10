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
