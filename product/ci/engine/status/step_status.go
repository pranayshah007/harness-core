// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package status

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"time"

	"github.com/cenkalti/backoff/v4"
	"github.com/gogo/protobuf/jsonpb"
	pb "github.com/harness/harness-core/910-delegate-task-grpc-service/src/main/proto/io/harness/task/service"
	callbackpb "github.com/harness/harness-core/920-delegate-service-beans/src/main/proto/io/harness/callback"
	delegateSvcpb "github.com/harness/harness-core/920-delegate-service-beans/src/main/proto/io/harness/delegate"
	delegatepb "github.com/harness/harness-core/955-delegate-beans/src/main/proto/io/harness/delegate"
	"github.com/harness/harness-core/commons/go/lib/delegate-task-grpc-service/grpc"
	"github.com/harness/harness-core/commons/go/lib/utils"
	"github.com/harness/harness-core/product/ci/common/external"
	"github.com/harness/harness-core/product/ci/engine/output"
	enginepb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/harness/harness-core/product/ci/engine/status/payloads"
	"github.com/pkg/errors"
	delegateClient "github.com/wings-software/dlite/client"
	delegate "github.com/wings-software/dlite/delegate"
	"go.uber.org/zap"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/durationpb"
)

const (
	delegateSvcEndpointEnv = "DELEGATE_SERVICE_ENDPOINT"
	restEnabledEnv         = "HARNESS_LE_STATUS_REST_ENABLED"
	delegateSvcTokenEnv    = "DELEGATE_SERVICE_TOKEN"
	delegateSvcIDEnv       = "DELEGATE_SERVICE_ID"
	delegateTokenKey       = "token"
	delegateSvcIDKey       = "serviceId"
	timeoutSecs            = 300 // timeout for delegate send task status rpc calls
	statusRetries          = 5
	accountSecretEnv       = "ACCOUNT_SECRET"
	managerSvcEndpointEnv  = "MANAGER_HOST_AND_PORT"
	delegateID             = "DELEGATE_ID"
	LEStatusType           = "CI_LE_STATUS"
)

var (
	newTaskServiceClient = grpc.NewTaskServiceClient
)

// SendStepStatus sends the step status to delegate task service.
func SendStepStatus(ctx context.Context, stepID, endpoint, managerSvcEndpoint, delegateID, accountKey, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
	status pb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *enginepb.Artifact, log *zap.SugaredLogger) error {
	start := time.Now()
	restEnabled, ok := os.LookupEnv(restEnabledEnv)
	if ok {
		log.Info(fmt.Sprintf("%s env is set with value: %s ", restEnabledEnv, restEnabled))
	}

	var err error
	if restEnabled == "true" {
		log.Info("Sending step status via REST", "stepID: ", stepID, "taskID: ", taskID)
		err = sendStatusHTTP(ctx, stepID, delegateID, accountKey, accountID, taskID, status.String(), endpoint, managerSvcEndpoint, numRetries, timeTaken, errMsg, stepOutput, artifact, log)
		if err != nil {
			log.Warn("Error sending status with http, Sending step status via GRPC as fallback. err:", err, "stepID: ", stepID, "taskID: ", taskID)
			err = sendStatusGrpc(ctx, endpoint, stepID, accountID, callbackToken, taskID, numRetries, timeTaken, status, errMsg, stepOutput, artifact, log)
		}
	} else {
		log.Info("Sending step status via GRPC", "stepID: ", stepID, "taskID: ", taskID)
		err = sendStatusGrpc(ctx, endpoint, stepID, accountID, callbackToken, taskID, numRetries, timeTaken, status, errMsg, stepOutput, artifact, log)
	}
	if err != nil {
		log.Errorw(
			"Failed to send/execute delegate task status",
			"callback_token", callbackToken,
			"step_output", stepOutput,
			"step_status", status.String(),
			"step_error", errMsg,
			"elapsed_time_ms", utils.TimeSince(start),
			"step_id", stepID,
			"task_id", taskID,
			zap.Error(err),
		)
		return err
	}
	log.Infow("Successfully sent the step status", "step_id", stepID, "step_status", status.String(), "task_id", taskID, "elapsed_time_ms", utils.TimeSince(start))
	return nil
}

func sendStatusGrpc(ctx context.Context, endpoint, stepID, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
	status pb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *enginepb.Artifact, log *zap.SugaredLogger) error {
	arg := getRequestArg(stepID, accountID, callbackToken, taskID, numRetries, timeTaken, status, errMsg, stepOutput, artifact, log)
	err := sendStatusWithRetries(ctx, endpoint, arg, log)
	if err == nil {
		log.Warn("Successfully sent the step status via GRPC", "stepID: ", stepID, "taskID: ", taskID)
	}
	return err
}

// getRequestArg returns arguments for send status rpc
func getRequestArg(stepID, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
	status pb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *enginepb.Artifact, log *zap.SugaredLogger) *pb.SendTaskStatusRequest {
	var stepOutputMap map[string]string
	if stepOutput != nil {
		stepOutputMap = stepOutput.Output.Variables
	}

	req := &pb.SendTaskStatusRequest{
		AccountId: &delegatepb.AccountId{
			Id: accountID,
		},
		TaskId: &delegateSvcpb.TaskId{
			Id: taskID,
		},
		CallbackToken: &callbackpb.DelegateCallbackToken{
			Token: callbackToken,
		},
		TaskStatusData: &pb.TaskStatusData{
			StatusData: &pb.TaskStatusData_StepStatus{
				StepStatus: &pb.StepStatus{
					NumRetries:          numRetries,
					TotalTimeTaken:      durationpb.New(timeTaken),
					StepExecutionStatus: status,
					ErrorMessage:        errMsg,
					Output: &pb.StepStatus_StepOutput{
						StepOutput: &pb.StepMapOutput{
							Output: stepOutputMap,
						},
					},
					Artifact: artifact,
				},
			},
		},
	}
	log.Infow("Sending step status", "step_id", stepID, "status", status.String(), "request_arg", msgToStr(req, log))
	return req
}

func sendStatusWithRetries(ctx context.Context, endpoint string, request *pb.SendTaskStatusRequest,
	log *zap.SugaredLogger) error {
	statusUpdater := func() error {
		start := time.Now()
		err := sendRequest(ctx, endpoint, request, log)
		if err != nil {
			log.Errorw(
				"failed to send step status",
				"endpoint", endpoint,
				"elapsed_time_ms", utils.TimeSince(start),
				zap.Error(err),
			)
			return err
		}
		return nil
	}
	b := utils.WithMaxRetries(utils.NewExponentialBackOffFactory(), statusRetries).NewBackOff()
	err := backoff.Retry(statusUpdater, b)
	if err != nil {
		return errors.Wrap(err, fmt.Sprintf("failed to send status"))
	}
	return nil
}

// sendRequest sends the step status to delegate service
func sendRequest(ctx context.Context, endpoint string, request *pb.SendTaskStatusRequest, log *zap.SugaredLogger) error {
	ctx, cancel := context.WithTimeout(ctx, time.Second*time.Duration(timeoutSecs))
	defer cancel()

	// If endpoint passed is empty, use the endpoint from environment variable
	if endpoint == "" {
		var ok bool
		endpoint, ok = os.LookupEnv(delegateSvcEndpointEnv)
		if !ok {
			return backoff.Permanent(errors.New(fmt.Sprintf("%s environment variable is not set", delegateSvcEndpointEnv)))
		}
	}

	token, ok := os.LookupEnv(delegateSvcTokenEnv)
	if !ok {
		return backoff.Permanent(errors.New(fmt.Sprintf("%s token is not set", delegateSvcTokenEnv)))
	}
	serviceID, ok := os.LookupEnv(delegateSvcIDEnv)
	if !ok {
		return backoff.Permanent(errors.New(fmt.Sprintf("%s delegate service ID is not set", delegateSvcIDEnv)))
	}

	c, err := newTaskServiceClient(endpoint, log)
	if err != nil {
		return backoff.Permanent(errors.Wrap(err, fmt.Sprintf("Could not create delegate task service client for %s", endpoint)))
	}
	defer c.CloseConn()

	md := metadata.Pairs(
		delegateTokenKey, token,
		delegateSvcIDKey, serviceID,
	)
	ctx = metadata.NewOutgoingContext(ctx, md)
	response, err := c.Client().SendTaskStatus(ctx, request)
	if err != nil {
		if _, ok := status.FromError(err); ok {
			return err
		}
		return backoff.Permanent(err)
	}

	if !response.GetSuccess() {
		return fmt.Errorf("failed to update step status at delegate agent side: %s", endpoint)
	}
	return nil
}

func msgToStr(msg *pb.SendTaskStatusRequest, log *zap.SugaredLogger) string {
	m := jsonpb.Marshaler{}
	jsonMsg, err := m.MarshalToString(msg)
	if err != nil {
		log.Errorw("failed to convert task status request to json", zap.Error(err))
		return msg.String()
	}
	return jsonMsg
}

func sendStatusHTTP(ctx context.Context, stepID, delegateID, accountKey, accountID, taskID, status, delegateSvcEndpoint, managerSvcEndpoint string, numRetries int32, timeTaken time.Duration,
	errMsg string, stepOutput *output.StepOutput, artifact *enginepb.Artifact, log *zap.SugaredLogger) error {

	// If managerURL is passed is empty, use from environment variable
	if managerSvcEndpoint == "" {
		var ok bool
		managerSvcEndpoint, ok = os.LookupEnv(managerSvcEndpointEnv)
		if !ok {
			return fmt.Errorf("%s environment variable is not set: ", managerSvcEndpointEnv)
		}
	}
	//check if delegate svc endpoint is empty use from environment variable
	if delegateSvcEndpoint == "" {
		var ok bool
		delegateSvcEndpoint, ok = os.LookupEnv(delegateSvcEndpointEnv)
		if !ok {
			return fmt.Errorf("%s environment variable is not set: ", delegateSvcEndpointEnv)
		}
	}

	additionalCertsDir := external.GetAdditionalCertsDir()

	httpClient := delegate.New(managerSvcEndpoint, accountID, accountKey, false, additionalCertsDir)

	stepStatusTaskResponseData := getStepStatusPayload(ctx, stepID, accountID, delegateID, delegateSvcEndpoint, taskID, status, numRetries, timeTaken, errMsg, stepOutput, artifact)

	statusData, err := json.Marshal(stepStatusTaskResponseData)
	if err != nil {
		return fmt.Errorf("error Marshalling stepstatus data: ", err)
	}
	taskResponse := &delegateClient.TaskResponse{
		ID:   taskID,
		Data: statusData,
		Code: "OK",
		Type: LEStatusType, //the task type will be fixed
	}

	err = httpClient.SendStatus(ctx, delegateID, taskID, taskResponse)
	if err != nil {
		return err
	}
	return nil
}

func getStepStatusPayload(ctx context.Context, stepID, accountID, delegateID, delegateSvcEndpoint, taskID, status string, numRetries int32, timeTaken time.Duration,
	errMsg string, stepOutput *output.StepOutput, artifact *enginepb.Artifact) payloads.StepStatusTaskResponseData {

	// Add Artifact metadata specs
	var artifactMetadata payloads.ArtifactMetadataConf
	if artifact.GetDockerArtifact() != nil {
		//build docker artifacts
		imagesdata := artifact.GetDockerArtifact().DockerImages
		var dockerArtifactDescriptors []payloads.DockerArtifactDescriptor
		for _, data := range imagesdata {
			dockerArtifact := payloads.DockerArtifactDescriptor{
				ImageName: data.Image,
				Digest:    data.Digest,
			}
			dockerArtifactDescriptors = append(dockerArtifactDescriptors, dockerArtifact)
		}
		dockerArtifactMetadata := payloads.DockerArtifactMetadata{
			RegistryType:              artifact.GetDockerArtifact().RegistryType,
			RegistryUrl:               artifact.GetDockerArtifact().RegistryUrl,
			Type:                      "DockerArtifactMetadata",
			DockerArtifactDescriptors: dockerArtifactDescriptors,
		}
		artifactMetadata.Type = payloads.ArtifactMetadataType(payloads.DockerArtifact)
		artifactMetadata.Spec = dockerArtifactMetadata
	} else if artifact.GetFileArtifact() != nil {
		filedata := artifact.GetFileArtifact().FileArtifacts
		var fileArtifactDescriptors []payloads.FileArtifactDescriptor
		for _, data := range filedata {
			fileArtifact := payloads.FileArtifactDescriptor{
				Name: data.Name,
				URL:  data.Url,
			}
			fileArtifactDescriptors = append(fileArtifactDescriptors, fileArtifact)
		}
		fileArtifactmetadata := payloads.FileArtifactMetadata{
			Type:                    "FileArtifactMetadata",
			FileArtifactDescriptors: fileArtifactDescriptors,
		}
		artifactMetadata.Type = payloads.ArtifactMetadataType(payloads.FileArtifact)
		artifactMetadata.Spec = fileArtifactmetadata
	}

	var stepOutputData = &payloads.Output{}
	if stepOutput != nil {
		stepOutputData.Output = stepOutput.Output.Variables
	}
	stepStatusTaskResponseData := payloads.StepStatusTaskResponseData{
		DelegateMetaInfo: payloads.DelegateMetaInfo{
			ID:       delegateID,
			HostName: delegateSvcEndpoint,
		},
		StepStatus: payloads.StepStatusConf{
			NumberOfRetries:        numRetries,
			TotalTimeTakenInMillis: timeTaken.Milliseconds(),
			StepExecutionStatus:    payloads.StepStatus(status),
			StepOutput:             stepOutputData,
			Error:                  errMsg,
			ArtifactMetadata:       &artifactMetadata,
		},
	}
	return stepStatusTaskResponseData
}
