package io.harness.cdng.manifest.resources.dtos;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.cdng.manifest.yaml.ManifestAttributes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;

@Getter
public class HelmManifestInternalDTO {
  @JsonProperty("identifier") String identifier;
  @JsonProperty("type") String type;
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  ManifestAttributes spec;
}
