package io.harness.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.pms.data.OrchestrationMap;

import dev.morphia.annotations.Entity;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.bson.Document;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(PIPELINE)
@Value
@Builder
@FieldNameConstants(innerTypeName = "PlanExecutionExpansionKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "planExecutionExpansions", noClassnameStored = true)
@org.springframework.data.mongodb.core.mapping.Document("planExecutionExpansions")
@TypeAlias("planExecutionExpansion")
public class PlanExecutionExpansion {
  Document expandedJson;
  String planExecutionId;
}
