// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"regexp"
	"strings"
	"time"

	"github.com/aws/aws-sdk-go/aws/awserr"
	"github.com/aws/aws-sdk-go/service/s3"
	"github.com/harness/harness-core/product/log-service/config"
	"github.com/harness/harness-core/product/log-service/logger"
	"github.com/harness/harness-core/product/log-service/store"
	"github.com/harness/harness-core/product/log-service/store/bolt"
	"github.com/harness/harness-core/product/log-service/stream"
	"github.com/pkg/errors"
)

const (
	keysParam            = "keys"
	maxLogLineSize       = 500
	genAIPlainTextPrompt = `
Provide error message, root cause and remediation from the below logs preserving the markdown format. %s

Logs:
` + "```" + `
%s
%s
` + "```"

	genAIJSONPrompt = `
Provide error message, root cause and remediation from the below logs. Return list of json object with three keys using the following format {"error", "cause", "remediation"}. %s

Logs:
` + "```" + `
%s
%s
` + "```"

	genAITemperature     = 0.0
	genAITopP            = 1.0
	genAITopK            = 1
	genAIMaxOuptutTokens = 1024
	errSummaryParam      = "err_summary"
	infraParam           = "infra"
	stepTypeParam        = "step_type"
	commandParam         = "command"

	azureAIProvider  = "azureopenai"
	azureAIModel     = "gpt3"
	vertexAIProvider = "vertexai"
	vertexAIModel    = "text-bison"
)

const (
	genAIResponseJSONFirstChar rune = '['
	genAIResponseJSONLastChar  rune = ']'
)

type (
	RCAReport struct {
		Rca     string      `json:"rca"`
		Results []RCAResult `json:"detailed_rca"`
	}

	RCAResult struct {
		Error       string `json:"error"`
		Cause       string `json:"cause"`
		Remediation string `json:"remediation"`
	}
)

func HandleRCA(store store.Store, cfg config.Config) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		st := time.Now()
		h := w.Header()
		h.Set("Access-Control-Allow-Origin", "*")
		ctx := r.Context()

		keys, err := getKeys(r)
		if err != nil {
			WriteBadRequest(w, err)
			return
		}

		logs, err := fetchLogs(ctx, store, keys, cfg.GenAI.MaxInputPromptLen)
		if err != nil {
			WriteNotFound(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("latency", time.Since(st)).
				WithField("keys", keys).
				Errorln("api: cannot find logs")
			return
		}

		genAISvcURL := cfg.GenAI.Endpoint
		genAISvcSecret := cfg.GenAI.ServiceSecret
		provider := cfg.GenAI.Provider
		useJSONResponse := cfg.GenAI.UseJSONResponse
		report, err := retrieveLogRCA(ctx, genAISvcURL, genAISvcSecret,
			provider, logs, useJSONResponse, r)
		if err != nil {
			WriteInternalError(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("latency", time.Since(st)).
				WithField("keys", keys).
				Errorln("api: failed to predict RCA")
			return
		}

		logger.FromRequest(r).
			WithField("keys", keys).
			WithField("latency", time.Since(st)).
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("api: successfully retrieved RCA")
		WriteJSON(w, report, 200)
	}
}

func retrieveLogRCA(ctx context.Context, endpoint, secret, provider,
	logs string, useJSONResponse bool, r *http.Request) (
	*RCAReport, error) {
	promptTmpl := genAIPlainTextPrompt
	if useJSONResponse {
		promptTmpl = genAIJSONPrompt
	}
	prompt := generatePrompt(r, logs, promptTmpl)
	client := genAIClient{endpoint: endpoint, secret: secret}

	response, isBlocked, err := predict(ctx, client, provider, prompt)
	if err != nil {
		return nil, err
	}
	if isBlocked {
		return nil, errors.New("received blocked response from genAI")
	}
	if useJSONResponse {
		return parseGenAIResponse(response)
	}
	return &RCAReport{Rca: response}, nil
}

func predict(ctx context.Context, client genAIClient, provider, prompt string) (string, bool, error) {
	switch provider {
	case vertexAIProvider:
		response, err := client.Complete(ctx, vertexAIProvider, vertexAIModel, prompt,
			genAITemperature, genAITopP, genAITopK, genAIMaxOuptutTokens)
		if err != nil {
			return "", false, err
		}
		return response.Text, response.Blocked, nil
	case azureAIProvider:
		response, err := client.Chat(ctx, azureAIProvider, azureAIModel, prompt,
			genAITemperature, -1, -1, genAIMaxOuptutTokens)
		if err != nil {
			return "", false, err
		}
		return response.Text, response.Blocked, nil
	default:
		return "", false, fmt.Errorf("unsupported provider %s", provider)
	}
}

func generatePrompt(r *http.Request, logs, promptTempl string) string {
	stepType := r.FormValue(stepTypeParam)
	command := r.FormValue(commandParam)
	infra := r.FormValue(infraParam)
	errSummary := r.FormValue(errSummaryParam)

	stepCtx := ""
	if infra != "" {
		stepCtx += fmt.Sprintf("Logs are generated on %s %s.\n", infra, getStepTypeContext(stepType))
	}
	if command != "" {
		stepCtx += fmt.Sprintf("Logs are generated by running command:\n```\n%s\n```", command)
	}
	errSummaryCtx := ""
	if errSummary != "" && !matchKnownPattern(errSummary) {
		errSummaryCtx += errSummary
	}

	prompt := fmt.Sprintf(promptTempl, stepCtx, logs, errSummaryCtx)
	return prompt
}

func getStepTypeContext(stepType string) string {
	switch stepType {
	case "liteEngineTask":
		return "while creating the pod"
	case "BuildAndPushACR":
		return "on building and pushing the image to ACR"
	case "BuildAndPushECR":
		return "on building and pushing the image to ECR"
	case "BuildAndPushGCR":
		return "on building and pushing the image to GCR"
	case "BuildAndPushDockerRegistry":
		return "on building and pushing the image to docker registry"
	case "GCSUpload":
		return "on uploading the files to GCS"
	case "S3Upload":
		return "on uploading the files to S3"
	case "SaveCacheGCS":
		return "on saving the files to GCS"
	case "SaveCacheS3":
		return "on saving the files to S3"
	case "RestoreCacheGCS":
		return "on restoring the files from GCS"
	case "RestoreCacheS3":
		return "on restoring the files from S3"
	case "ArtifactoryUpload":
		return "on uploading the files to Jfrog artifactory"
	}
	return ""
}

func fetchLogs(ctx context.Context, store store.Store, key []string, maxLen int) (
	string, error) {
	logs := ""
	for _, k := range key {
		l, err := fetchKeyLogs(ctx, store, k)
		if err != nil {
			return "", err
		}
		logs += l
	}

	// Calculate the starting position for retrieving the last N characters
	startPos := len(logs) - maxLen
	if startPos < 0 {
		startPos = 0
	}

	// Retrieve the last N characters from the buffer
	result := logs[startPos:]
	return result, nil
}

// fetchKeyLogs fetches the logs from the store for a given key
func fetchKeyLogs(ctx context.Context, store store.Store, key string) (
	string, error) {
	out, err := store.Download(ctx, key)
	if out != nil {
		defer out.Close()
	}
	if err != nil {
		// If the key does not exist, return empty string
		// This happens when logs are empty for a step
		if err == bolt.ErrNotFound {
			return "", nil
		}
		if aerr, ok := err.(awserr.Error); ok {
			if aerr.Code() == s3.ErrCodeNoSuchKey {
				return "", nil
			}
		}
		return "", err
	}

	var logs string

	scanner := bufio.NewScanner(out)
	for scanner.Scan() {
		l := stream.Line{}
		if err := json.Unmarshal([]byte(scanner.Text()), &l); err != nil {
			return "", errors.Wrap(err, "failed to unmarshal log line")
		}

		logs += l.Message[:min(len(l.Message), maxLogLineSize)]
	}

	if err := scanner.Err(); err != nil {
		return "", err
	}
	return logs, nil
}

// parses the generative AI response into a RCAReport
func parseGenAIResponse(in string) (*RCAReport, error) {
	var rcaResults []RCAResult
	if err := json.Unmarshal([]byte(in), &rcaResults); err == nil {
		return &RCAReport{Results: rcaResults}, nil
	}

	// Response returned by the generative AI is not a valid json
	// Unmarshalled response is of type string. So, we need to unmarshal
	// it to string and then to []RCAReport
	var data interface{}
	if err := json.Unmarshal([]byte(in), &data); err != nil {
		return nil, errors.Wrap(err,
			fmt.Sprintf("response is not a valid json: %s", in))
	}
	switch value := data.(type) {
	case string:
		// Parse if response is a single RCA result
		var rcaResult RCAResult
		if err := json.Unmarshal([]byte(value), &rcaResult); err == nil {
			return &RCAReport{Results: []RCAResult{rcaResult}}, nil
		}

		v, err := jsonStringRetriever(value)
		if err != nil {
			return nil, err
		}
		var rcaResults []RCAResult
		if err := json.Unmarshal([]byte(v), &rcaResults); err != nil {
			return nil, errors.Wrap(err,
				fmt.Sprintf("response is not a valid json: %s", in))
		}
		return &RCAReport{Results: rcaResults}, nil
	case []RCAResult:
		return &RCAReport{Results: value}, nil
	default:
		return nil, fmt.Errorf("response is not a valid json: %v", value)
	}
}

// retrieves the JSON part of the generative AI response
// and trims the extra characters
func jsonStringRetriever(s string) (string, error) {
	firstIdx := strings.IndexRune(s, genAIResponseJSONFirstChar)
	if firstIdx == -1 {
		return "", fmt.Errorf("cannot find first character %c in %s", genAIResponseJSONFirstChar, s)
	}

	lastIndex := -1
	for i := len(s) - 1; i >= 0; i-- {
		if rune(s[i]) == genAIResponseJSONLastChar {
			lastIndex = i
			break
		}
	}
	if lastIndex == -1 {
		return "", fmt.Errorf("cannot find last character %c in %s", genAIResponseJSONLastChar, s)
	}

	return s[firstIdx : lastIndex+1], nil
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}

// matchKnownPattern checks if the error summary matches any of the known errors which do not
// add value to logs for RCA
func matchKnownPattern(s string) bool {
	if m, err := regexp.MatchString("exit status \\d+", s); err == nil && m {
		return true
	}
	if m, err := regexp.MatchString("1 error occurred: \\* exit status \\d+", s); err == nil && m {
		return true
	}
	if m, err := regexp.MatchString("Shell Script execution failed\\. Please check execution logs\\.", s); err == nil && m {
		return true
	}
	return false
}

func getKeys(r *http.Request) ([]string, error) {
	accountID := r.FormValue(accountIDParam)
	if accountID == "" {
		return nil, errors.New("accountID is required")
	}

	keysStr := r.FormValue(keysParam)
	if keysStr == "" {
		return nil, errors.New("keys field is required")
	}

	keys := make([]string, 0)
	for _, v := range strings.Split(keysStr, ",") {
		keys = append(keys, CreateAccountSeparatedKey(accountID, v))
	}
	return keys, nil
}
