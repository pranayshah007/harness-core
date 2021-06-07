package io.harness.query.shapedetector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;
import org.bson.Document;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
public class QueryHashInfo {
  QueryHashKey queryHashKey;
  Document queryDoc;

  @Value
  @Builder
  public static class QueryHashKey {
    String collectionName;
    String queryHash;
  }
}
