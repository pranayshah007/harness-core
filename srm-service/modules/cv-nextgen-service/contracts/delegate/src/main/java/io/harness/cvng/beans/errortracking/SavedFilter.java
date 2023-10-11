package io.harness.cvng.beans.errortracking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
public class SavedFilter {
  private Long id;
  private String filterName;
  private String filterDescription;
  private long harnessProjectId;
  private List<EventStatus> statuses;
  private List<EventType> eventTypes;
  private String searchTerm;
}
