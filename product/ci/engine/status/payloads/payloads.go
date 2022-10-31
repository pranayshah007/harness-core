// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package payloads

type (
	DelegateInfo struct {
		ID         string `json:"id"`
		InstanceID string `json:"instance_id"`
		Token      string `json:"token"`
	}
	StepStatusTaskResponseData struct {
		DelegateMetaInfo DelegateMetaInfo `json:"delegateMetaInfo"`
		StepStatus       StepStatusConf   `json:"stepStatus"`
	}

	DelegateMetaInfo struct {
		ID       string `json:"id"`
		HostName string `json:"host_name"`
	}

	StepStatusConf struct {
		NumberOfRetries        int32                 `json:"numberOfRetries"`
		TotalTimeTakenInMillis int64                 `json:"totalTimeTakenInMillis"`
		StepExecutionStatus    StepStatus            `json:"stepExecutionStatus"`
		ArtifactMetadata       *ArtifactMetadataConf `json:"artifactMetadata"`
		StepOutput             *Output               `json:"output"`
		Error                  string                `json:"error"`
	}

	Output struct {
		Output map[string]string `json:"map"`
	}
	ArtifactMetadataConf struct {
		Type ArtifactMetadataType `json:"type,omitempty"`
		Spec interface{}          `json:"spec"`
	}
	DockerArtifactMetadata struct {
		Type                      string                     `json:"type"`
		RegistryType              string                     `json:"registryType"`
		RegistryUrl               string                     `json:"registryUrl"`
		DockerArtifactDescriptors []DockerArtifactDescriptor `json:"dockerArtifacts"`
	}
	DockerArtifactDescriptor struct {
		ImageName string `json:"imageName"`
		Digest    string `json:"digest"`
	}
	FileArtifactMetadata struct {
		Type                    string                   `json:"type"`
		FileArtifactDescriptors []FileArtifactDescriptor `json:"fileArtifactDescriptors"`
	}
	FileArtifactDescriptor struct {
		Name string `json:"name"`
		URL  string `json:"url"`
	}
)
type StepStatus string

const (
	Success StepStatus = "SUCCESS"
	Failure StepStatus = "FAILURE"
	Running StepStatus = "RUNNING"
	Queued  StepStatus = "QUEUED"
	Skipped StepStatus = "SKIPPED"
	Aborted StepStatus = "ABORTED"
)

type ArtifactMetadataType string

const (
	DockerArtifact StepStatus = "DOCKER_ARTIFACT_METADATA"
	FileArtifact   StepStatus = "FILE_ARTIFACT_METADATA"
)
