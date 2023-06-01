// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"bytes"
	"context"
	"encoding/json"
	"io/ioutil"
	"net/http"
	"time"

	"github.com/cenkalti/backoff/v4"
	"github.com/harness/harness-core/product/log-service/logger"
	"github.com/pkg/errors"
)

const (
	modelProvider = "vertexai"
	modelName     = "text-bison"
)

type (
	genAICompletionInput struct {
		Prompt          string      `json:"prompt"`
		Provider        string      `json:"provider"`
		ModelName       string      `json:"model_name"`
		ModelParameters modelParams `json:"model_parameters"`
	}

	modelParams struct {
		Temperature     float64 `json:"temperature"`
		MaxOutputTokens int     `json:"max_output_tokens"`
		TopP            float64 `json:"top_p"`
		TopK            int     `json:"top_k"`
	}
)

func genAIPredictWithRetries(ctx context.Context, endpoint, prompt string,
	temperature float64, topP int, topK int, maxOutputTokens int) (
	string, error) {
	b := backoff.NewExponentialBackOff()
	b.MaxElapsedTime = time.Minute

	url := endpoint + "/complete"
	for {
		res, err := genAIPredict(ctx, url, prompt, temperature,
			topP, topK, maxOutputTokens)
		if err == nil {
			return res, nil
		}

		// do not retry on Canceled or DeadlineExceeded
		if err := ctx.Err(); err != nil {
			logger.FromContext(ctx).WithError(err).Errorln("http: context canceled")
			return "", err
		}

		duration := b.NextBackOff()
		if err != nil {
			if duration == backoff.Stop {
				return "", err
			}
		}
		time.Sleep(duration)
	}
}

// calls generative AI service to generate results for the input prompt
func genAIPredict(ctx context.Context, url, prompt string, temperature float64,
	topP int, topK int, maxOutputTokens int) (string, error) {
	in := genAICompletionInput{
		Prompt:    prompt,
		Provider:  modelProvider,
		ModelName: modelName,
		ModelParameters: modelParams{
			Temperature:     temperature,
			MaxOutputTokens: maxOutputTokens,
			TopP:            float64(topP),
			TopK:            topK,
		},
	}

	buf := new(bytes.Buffer)
	if err := json.NewEncoder(buf).Encode(in); err != nil {
		return "", err
	}

	req, err := http.NewRequestWithContext(ctx, "POST", url, buf)
	if err != nil {
		return "", errors.Wrap(err, "failed to create request")
	}

	// Set the request headers
	req.Header.Set("Content-Type", "application/json")

	// Create an HTTP client
	client := &http.Client{}

	// Send the request
	res, err := client.Do(req)
	if err != nil {
		return "", errors.Wrap(err, "failed to send request")
	}
	defer res.Body.Close()

	// Read the response body
	body, err := ioutil.ReadAll(res.Body)
	if err != nil {
		return "", errors.Wrap(err, "failed to read response body")
	}

	if res.StatusCode > 299 {
		return "", errors.New(
			http.StatusText(res.StatusCode),
		)
	}

	return string(body), nil
}
