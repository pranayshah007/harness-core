/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.stepstatus.artifact;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@AllArgsConstructor
class DockerArtifactV1 implements Artifact {
  @JsonProperty("data") private DockerV1Data data;

  @Override
  public Kind getKind() {
    return Kind.DOCKER;
  }

  @Override
  public ArtifactMetadata toArtifactMetadata() {
    return ArtifactMetadata.builder()
        .type(ArtifactMetadataType.DOCKER_ARTIFACT_METADATA)
        .spec(DockerArtifactMetadata.builder()
                  .registryType(data.registryType)
                  .registryUrl(data.registryUrl)
                  .dockerArtifacts(
                      data.images.stream()
                          .map(i -> DockerArtifactDescriptor.builder().imageName(i.image).digest(i.digest).build())
                          .collect(Collectors.toList()))
                  .build())
        .build();
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  static class DockerV1Data {
    private String registryType;
    private String registryUrl;
    private List<Image> images;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Image {
      private String image;
      private String digest;
    }
  }
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class FileArtifactV1 implements Artifact {
  @JsonProperty("data") private FileArtifactV1Data data;

  @Override
  public Kind getKind() {
    return Kind.FILE_UPLOAD;
  }

  @Override
  public ArtifactMetadata toArtifactMetadata() {
    return ArtifactMetadata.builder()
        .type(ArtifactMetadataType.FILE_ARTIFACT_METADATA)
        .spec(
            FileArtifactMetadata.builder()
                .fileArtifactDescriptors(data.fileArtifacts.stream()
                                             .map(f -> FileArtifactDescriptor.builder().url(f.url).name(f.name).build())
                                             .collect(Collectors.toList()))
                .build())
        .build();
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  static class FileArtifactV1Data {
    private List<FileArtifact> fileArtifacts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FileArtifact {
      private String name;
      private String url;
    }
  }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
  @JsonSubTypes.Type(value = DockerArtifactV1.class, name = "docker/v1")
  , @JsonSubTypes.Type(value = FileArtifactV1.class, name = "fileUpload/v1")
})
public interface Artifact {
  @TypeAlias("kind")
  enum Kind {
    @JsonProperty("docker/v1") DOCKER("docker/v1"),
    @JsonProperty("fileUpload/v1") FILE_UPLOAD("fileUpload/v1");

    private final String yamlName;

    Kind(String yamlName) {
      this.yamlName = yamlName;
    }

    @JsonValue
    public String getYamlName() {
      return yamlName;
    }
  }

  Kind getKind();
  ArtifactMetadata toArtifactMetadata();
}
