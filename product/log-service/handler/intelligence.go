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
	debugLogChars        = 200
	genAIPlainTextPrompt = `
Provide error message, root cause and remediation from the below logs preserving the markdown format.
Remediation is required in the response - error message and root cause can be truncated if needed, but make sure to preserve the markdown format. %s


Logs:
` + "```" + `
%s
%s
` + "```"

	genAIAzurePlainTextPrompt = `
Provide error message, root cause and remediation from the below logs preserving the markdown format.
Remediation is required in the response - error message and root cause can be truncated if needed, but make sure to preserve the markdown format. %s

Logs:
` + "```" + `
%s
%s
` + "```" + `

Provide your output in the following format:
` + "```" + `
## Error message
<Error message>

## Root cause
<Root cause>

## Remediation
<Remediation>
` + "```"

	genAIJSONPrompt = `
Provide error message, root cause and remediation from the below logs. Remediation is required in the response - error message and root cause can be truncated if needed, but make sure to preserve the markdown format. Return list of json object with three keys using the following format {"error", "cause", "remediation"}. %s

Logs:
` + "```" + `
%s
%s
` + "```"

	genAIBisonJSONPrompt = `
I have a set of logs. The logs contain error messages. I want you to find the error messages in the logs, and suggest root cause and remediation or fix suggestions. Remediation is required in the response - error message and root cause can be truncated if needed, but make sure to preserve the markdown format. I want you to give me the response in JSON format, no text before or after the JSON. Example of response:
[
	{
		"error": "error_1",
		"cause": "cause_1",
		"remediation": "fix line 2 of the command"
	},
	{
		"error": "error_2",
		"cause": "cause_2",
		"remediation": "fix line 5 of the command"
	}
]
%s

Here is the logs, remember to give the response only in json format like the example provided above, no text before or after the json object:
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
	osParam              = "os"
	archParam            = "arch"
	pluginParam          = "plugin"

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

		logger.FromRequest(r).WithField("keys", keys).
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("api: rca call received, fetching logs")

		logs, err := fetchLogs(ctx, store, keys, cfg.GenAI.MaxInputPromptLen)
		if err != nil {
			WriteNotFound(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("latency", time.Since(st)).
				WithField("keys", keys).
				Errorln("api: could not fetch logs for rca call")
			return
		}

		stepType := r.FormValue(stepTypeParam)
		command := r.FormValue(commandParam)
		errSummary := r.FormValue(errSummaryParam)

		logger.FromRequest(r).WithField("keys", keys).
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("api: fetched logs for rca call, initiating call to ml service")

		genAISvcURL := cfg.GenAI.Endpoint
		genAISvcSecret := cfg.GenAI.ServiceSecret
		provider := cfg.GenAI.Provider
		useJSONResponse := cfg.GenAI.UseJSONResponse
		report, prompt, err := retrieveLogRCA(ctx, genAISvcURL, genAISvcSecret,
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
			WithField("command", trim(command, debugLogChars)).
			WithField("step_type", stepType).
			WithField("prompt", prompt).
			WithField("error_summary", errSummary).
			WithField("time", time.Now().Format(time.RFC3339)).
			WithField("response.rca", report.Rca).
			WithField("response.results", report.Results).
			Infoln("api: successfully retrieved RCA")
		WriteJSON(w, report, 200)
	}
}

func retrieveLogRCA(ctx context.Context, endpoint, secret, provider,
	logs string, useJSONResponse bool, r *http.Request) (
	*RCAReport, string, error) {
	promptTmpl := genAIPlainTextPrompt
	if useJSONResponse {
		promptTmpl = genAIJSONPrompt
		if provider == vertexAIProvider {
			promptTmpl = genAIBisonJSONPrompt
		}
	} else {
		if provider == azureAIProvider {
			promptTmpl = genAIAzurePlainTextPrompt
		}
	}

	prompt := generatePrompt(r, logs, promptTmpl)
	client := genAIClient{endpoint: endpoint, secret: secret}

	response, isBlocked, err := predict(ctx, client, provider, prompt)
	if err != nil {
		return nil, prompt, err
	}
	if isBlocked {
		return nil, prompt, errors.New("received blocked response from genAI")
	}
	if useJSONResponse {
		report, err := parseGenAIResponse(response)
		return report, prompt, err
	}
	return &RCAReport{Rca: response}, prompt, nil
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
	os := r.FormValue(osParam)
	arch := r.FormValue(archParam)
	plugin := r.FormValue(pluginParam)

	platformCtx := ""
	if os != "" && arch != "" {
		platformCtx = fmt.Sprintf("%s %s ", os, arch)
	}
	stepCtx := ""
	if infra != "" {
		stepCtx += fmt.Sprintf("Logs are generated on %s%s %s.\n", platformCtx, infra, getStepTypeContext(stepType, infra))
	}
	if command != "" {
		stepCtx += fmt.Sprintf("Logs are generated by running command:\n```\n%s\n```", command)
	} else if plugin != "" {
		pluginType := ""
		if stepType == "Plugin" {
			pluginType = "drone plugin"
		} else if stepType == "Action" {
			pluginType = "github action"
		} else if stepType == "Bitrise" {
			pluginType = "bitrise plugin"
		}

		if pluginType != "" {
			stepCtx += fmt.Sprintf("The logs below were generated when running %s %s", pluginType, plugin)
		}
	}
	errSummaryCtx := ""
	if errSummary != "" && !matchKnownPattern(errSummary) {
		errSummaryCtx += errSummary
	}

	prompt := fmt.Sprintf(promptTempl, stepCtx, logs, errSummaryCtx)
	return prompt
}

func getStepTypeContext(stepType, infra string) string {
	switch stepType {
	case "liteEngineTask":
		if infra == "vm" {
			return "while initializing the virtual machine in Harness CI"
		}
		return "while creating a Pod in Kubernetes cluster for running Harness CI builds."
	case "BuildAndPushACR":
		return "while building and pushing the image to Azure Container Registry in Harness CI"
	case "BuildAndPushECR":
		return "while building and pushing the image to Elastic Container Registry in Harness CI"
	case "BuildAndPushGCR":
		return "while building and pushing the image to Google Container Registry in Harness CI"
	case "BuildAndPushDockerRegistry":
		return "while building and pushing the image to docker registry in Harness CI"
	case "GCSUpload":
		return "while uploading the files to GCS in Harness CI"
	case "S3Upload":
		return "while uploading the files to S3 in Harness CI"
	case "SaveCacheGCS":
		return "while saving the files to GCS in Harness CI"
	case "SaveCacheS3":
		return "while saving the files to S3 in Harness CI"
	case "RestoreCacheGCS":
		return "while restoring the files from GCS in Harness CI"
	case "RestoreCacheS3":
		return "while restoring the files from S3 in Harness CI"
	case "ArtifactoryUpload":
		return "while uploading the files to Jfrog artifactory in Harness CI"
	case "JiraUpdate":
		return "while updating the Jira ticket in Harness"
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

// given a string s, print the first n and the last n characters
func trim(s string, n int) string {
	length := len(s)
	if length <= 2*n {
		return s
	} else {
		return s[:n] + "..." + s[length-n:]
	}
}
