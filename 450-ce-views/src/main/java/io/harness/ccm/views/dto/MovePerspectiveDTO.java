package io.harness.ccm.views.dto;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MovePerspectiveDTO {
  String newFolderId;
  List<String> perspectiveIds;
}
