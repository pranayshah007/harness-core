package io.harness.cvng.beans.errortracking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorTrackingHitSummary {
  String versionId;
  Integer hitId;
  Integer requestId;
  OveropsEventType eventType;
  String eventName;
  Date firstSeen;
  List<String> stackTrace = new ArrayList<>();
}
