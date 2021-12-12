package io.harness.ng.core.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@OwnedBy(PL)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "ProjectFilter", description = "This is the Project Filter defined in Harness")
public class ProjectFilterDTO {
  @Schema(description = "Search Term") String searchTerm;
  @Schema(description = "Set of Organization Identifiers") Set<String> orgIdentifiers;
  @Schema(description = "This field denotes if project filter has module") Boolean hasModule;
  @Schema(description = "Module Type") ModuleType moduleType;
  @Schema(description = "List of Identifiers") List<String> identifiers;
}
