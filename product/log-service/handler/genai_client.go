// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"io/ioutil"
	"net/http"
	"time"

	"github.com/cenkalti/backoff/v4"
	"github.com/golang-jwt/jwt/v5"
	"github.com/harness/harness-core/product/log-service/logger"
	"github.com/pkg/errors"
)

const (
	completionEndpoint = "/complete"
	chatEndpoint       = "/chat"
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
	genAICompletionResponse struct {
		Text    string `json:"text"`
		Blocked bool   `json:"blocked"`
	}

	modelParams struct {
		Temperature     float64 `json:"temperature"`
		MaxOutputTokens int     `json:"max_output_tokens"`
		TopP            float64 `json:"top_p"`
		TopK            int     `json:"top_k"`
	}
)

// Error represents a json-encoded API error.
type genAIErr struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
}

func (e *genAIErr) Error() string {
	return e.Message
}

// HTTPClient provides an http service client.
type genAIClient struct {
	endpoint string // Example: http://localhost:port
	secret   string // GenAI service secret for authorization
}

// Complete sends a request for completing prompt to the GenAI service.
func (c *genAIClient) Complete(ctx context.Context, prompt string,
	temperature float64, topP int, topK int, maxOutputTokens int) (*genAICompletionResponse, error) {
	path := completionEndpoint

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
	out := new(genAICompletionResponse)
	backoff := createBackoff(time.Minute)
	_, err := c.retry(ctx, c.endpoint+path, "POST", in, out, backoff)
	return out, err
}

func (c *genAIClient) retry(ctx context.Context, method, path string, in, out interface{}, b backoff.BackOff) (*http.Response, error) {
	for {
		res, err := c.do(ctx, method, path, in, out)

		// do not retry on Canceled or DeadlineExceeded
		if err := ctx.Err(); err != nil {
			logger.FromContext(ctx).WithError(err).WithField("path", path).Errorln("http: context canceled")
			return res, err
		}

		duration := b.NextBackOff()

		if res != nil {
			// Check the response code. We retry on 5xx-range
			// responses to allow the server time to recover, as
			// 5xx's are typically not permanent errors and may
			// relate to outages on the server side.

			if res.StatusCode >= 500 {
				logger.FromContext(ctx).WithError(err).WithField("path", path).Warnln("http: log-service server error: reconnect and retry")
				if duration == backoff.Stop {
					return nil, err
				}
				time.Sleep(duration)
				continue
			}
		} else if err != nil {
			logger.FromContext(ctx).WithError(err).WithField("path", path).Warnln("http: request error. Retrying ...")
			if duration == backoff.Stop {
				return nil, err
			}
			time.Sleep(duration)
			continue
		}
		return res, err
	}
}

// do is a helper function that posts a signed http request with
// the input encoded and response decoded from json.
func (c *genAIClient) do(ctx context.Context, path, method string, in, out interface{}) (*http.Response, error) {
	var r io.Reader

	if in != nil {
		buf := new(bytes.Buffer)
		json.NewEncoder(buf).Encode(in)
		r = buf
	}

	req, err := http.NewRequestWithContext(ctx, method, path, r)
	if err != nil {
		return nil, errors.Wrap(err, "failed to create request")
	}

	// Set the request headers
	req.Header.Set("Content-Type", "application/json")

	token, err := generateAuthToken(c.secret)
	if err != nil {
		return nil, errors.Wrap(err, "failed to generate auth token")
	}
	req.Header.Set("Authorization", "Bearer "+token)
	// Create an HTTP client
	client := &http.Client{}

	// the request should include the secret shared between
	// the agent and server for authorization.
	res, err := client.Do(req)
	if res != nil {
		defer func() {
			// drain the response body so we can reuse
			// this connection.
			io.Copy(ioutil.Discard, io.LimitReader(res.Body, 4096))
			res.Body.Close()
		}()
	}
	if err != nil {
		return res, err
	}

	// if the response body return no content we exit
	// immediately. We do not read or unmarshal the response
	// and we do not return an error.
	if res.StatusCode == 204 {
		return res, nil
	}

	// else read the response body into a byte slice.
	body, err := ioutil.ReadAll(res.Body)
	if err != nil {
		return res, err
	}

	if res.StatusCode > 299 {
		// if the response body includes an error message
		// we should return the error string.
		if len(body) != 0 {
			out := new(genAIErr)
			if err := json.Unmarshal(body, out); err != nil {
				return res, out
			}
			return res, errors.New(
				string(body),
			)
		}
		// if the response body is empty we should return
		// the default status code text.
		return res, errors.New(
			http.StatusText(res.StatusCode),
		)
	}
	if out == nil {
		return res, nil
	}
	return res, json.Unmarshal(body, out)
}

func createBackoff(maxElapsedTime time.Duration) *backoff.ExponentialBackOff {
	exp := backoff.NewExponentialBackOff()
	exp.MaxElapsedTime = maxElapsedTime
	return exp
}

func generateAuthToken(secret string) (string, error) {
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"sub": "LOG_SVC",
		"iat": time.Now().Unix(),
		"exp": time.Now().Add(time.Hour).Unix(),
	})

	// Sign and get the complete encoded token as a string using the secret
	return token.SignedString([]byte(secret))
}
