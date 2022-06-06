package io.harness.filestore.dto.filter;

import io.harness.EntityType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferencedByDTO {
  EntityType type;
  String name;
}
