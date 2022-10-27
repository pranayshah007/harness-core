// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package artifact

import (
	"encoding/json"
	"fmt"
	"io/ioutil"

	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/pkg/errors"
)

type (
	// basic structs
	Image struct {
		Image  string `json:"image"`
		Digest string `json:"digest"`
	}
	FileArtifact struct {
		Name string `json:"name"`
		Url  string `json:"url"`
	}

	// artifact data kinds
	DockerV1Data struct {
		RegistryType string  `json:"registryType"`
		RegistryUrl  string  `json:"registryUrl"`
		Images       []Image `json:"images"`
	}
	FileArtifactV1Data struct {
		FileArtifacts []FileArtifact `json:"fileArtifacts"`
	}

	// Artifact formats
	DockerArtifactV1 struct {
		Kind string       `json:"kind"`
		Data DockerV1Data `json:"data"`
	}
	FileArtifactV1 struct {
		Kind string             `json:"kind"`
		Data FileArtifactV1Data `json:"data"`
	}

	Artifact struct {
		*DockerArtifactV1
		*FileArtifactV1
	}
)

func GetArtifactProtoFromFile(artifactFilePath string) (*pb.Artifact, error) {
	var artifactProto = &pb.Artifact{}
	if artifactFilePath != "" {
		artifactObject, err := ParseArtifactFromFile(artifactFilePath)
		if err != nil {
			return nil, err
		} else {
			artifactProto, err = artifactObject.ToArtifactProto()
			if err != nil {
				return nil, err
			}
		}
	}
	return artifactProto, nil
}

func ParseArtifactFromFile(filePath string) (*Artifact, error) {
	json, err := ioutil.ReadFile(filePath)
	if err != nil {
		return nil, errors.Wrap(err, fmt.Sprintf("failed to read artifact file: %s", filePath))
	}
	artifact := &Artifact{}
	err = artifact.UnmarshalJSON(json)
	if err != nil {
		return nil, errors.Wrap(err, fmt.Sprintf("failed to unmarshal artifact json string: %s", json))
	}
	return artifact, nil
}

func (o *Artifact) ToArtifactProto() (*pb.Artifact, error) {
	switch {
	case o.DockerArtifactV1 != nil:
		var imageMetadataList []*pb.DockerImageMetadata
		for _, v := range o.DockerArtifactV1.Data.Images {
			imageMetadataList = append(imageMetadataList, &pb.DockerImageMetadata{
				Image:  v.Image,
				Digest: v.Digest,
			})
		}
		dockerArtifactMetadata := &pb.DockerArtifactMetadata{
			RegistryType: o.DockerArtifactV1.Data.RegistryType,
			RegistryUrl:  o.DockerArtifactV1.Data.RegistryUrl,
			Metadata: o.DockerArtifactV1.Data.Metadata,
			DockerImages: imageMetadataList,
		}
		return &pb.Artifact{
			Metadata: &pb.Artifact_DockerArtifact{dockerArtifactMetadata},
		}, nil
	case o.FileArtifactV1 != nil:
		var fileArtifactList []*pb.FileArtifact
		for _, v := range o.FileArtifactV1.Data.FileArtifacts {
			fileArtifactList = append(fileArtifactList, &pb.FileArtifact{
				Name: v.Name,
				Url:  v.Url,
			})
		}
		fileArtifactMetadata := &pb.FileArtifactMetadata{
			FileArtifacts: fileArtifactList,
		}
		return &pb.Artifact{
			Metadata: &pb.Artifact_FileArtifact{fileArtifactMetadata},
		}, nil
	default:
		return nil, fmt.Errorf("unrecognized kind value %p", o)
	}
}

func (o *Artifact) UnmarshalJSON(data []byte) error {
	var getKind struct{ Kind string }
	if err := json.Unmarshal(data, &getKind); err != nil {
		return err
	}
	switch getKind.Kind {
	case "docker/v1":
		o.DockerArtifactV1 = &DockerArtifactV1{}
		return json.Unmarshal(data, o.DockerArtifactV1)
	case "fileUpload/v1":
		o.FileArtifactV1 = &FileArtifactV1{}
		return json.Unmarshal(data, o.FileArtifactV1)
	default:
		return fmt.Errorf("unrecognized kind value %q", getKind.Kind)
	}

}

func (t Artifact) MarshalJSON() ([]byte, error) {
	switch {
	case t.DockerArtifactV1 != nil:
		return json.Marshal(t.DockerArtifactV1)
	case t.FileArtifactV1 != nil:
		return json.Marshal(t.FileArtifactV1)
	default:
		return json.Marshal(nil)
	}
}
