package io.harness.cdng.artifact.resources.googleartifactregistry.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
public class RegionGar {
  String name;
  String value;
  public RegionGar(String name, String value) {
    this.name = name;
    this.value = value;
  }
}
