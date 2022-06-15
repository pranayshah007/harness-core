// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package client

import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/json"
	"errors"

	"fmt"
	"io"
	"io/ioutil"
	"net/http"
	"time"

	"github.com/cenkalti/backoff/v4"
	logger "github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/harness/harness-core/product/ci/ti-service/types"
	"go.uber.org/zap"
)

var _ Client = (*HTTPClient)(nil)

const (
	dbEndpoint    = "/reports/write?accountId=%s&orgId=%s&projectId=%s&pipelineId=%s&buildId=%s&stageId=%s&stepId=%s&report=%s&repo=%s&sha=%s&commitLink=%s"
	testEndpoint  = "/tests/select?accountId=%s&orgId=%s&projectId=%s&pipelineId=%s&buildId=%s&stageId=%s&stepId=%s&repo=%s&sha=%s&source=%s&target=%s"
	cgEndpoint    = "/tests/uploadcg?accountId=%s&orgId=%s&projectId=%s&pipelineId=%s&buildId=%s&stageId=%s&stepId=%s&repo=%s&sha=%s&source=%s&target=%s&timeMs=%d"
	agentEndpoint = "/agents/link?language=%s&os=%s&arch=%s&framework=%s&version=%s&buildenv=%s"
)

// defaultClient is the default http.Client.
var defaultClient = &http.Client{
	CheckRedirect: func(*http.Request, []*http.Request) error {
		return http.ErrUseLastResponse
	},
}

// NewHTTPClient returns a new HTTPClient.
func NewHTTPClient(endpoint, accountID, token string, skipverify bool) *HTTPClient {
	client := &HTTPClient{
		Endpoint:   endpoint,
		AccountID:  accountID,
		Token:      token,
		SkipVerify: skipverify,
	}
	if skipverify {
		client.Client = &http.Client{
			CheckRedirect: func(*http.Request, []*http.Request) error {
				return http.ErrUseLastResponse
			},
			Transport: &http.Transport{
				Proxy: http.ProxyFromEnvironment,
				TLSClientConfig: &tls.Config{
					InsecureSkipVerify: true,
				},
			},
		}
	}
	return client
}

// HTTPClient provides an http service client.
type HTTPClient struct {
	Client     *http.Client
	Endpoint   string // Example: http://localhost:port
	Token      string
	AccountID  string
	SkipVerify bool
}

// Write writes test results to the TI server
func (c *HTTPClient) Write(ctx context.Context, org, project, pipeline, build, stage, step, report, repo, sha, commitLink string, tests []*types.TestCase) error {
	path := fmt.Sprintf(dbEndpoint, c.AccountID, org, project, pipeline, build, stage, step, report, repo, sha, commitLink)
	ctx = context.WithValue(ctx, "reqId", sha)
	_, err := c.do(ctx, c.Endpoint+path, "POST", &tests, nil)
	return err
}

func (c *HTTPClient) DownloadLink(ctx context.Context, language, os, arch, framework, version, env string) ([]types.DownloadLink, error) {
	path := fmt.Sprintf(agentEndpoint, language, os, arch, framework, version, env)
	var resp []types.DownloadLink
	ctx = context.WithValue(ctx, "reqId", "")
	_, err := c.do(ctx, c.Endpoint+path, "GET", nil, &resp)
	return resp, err
}

// SelectTests returns a list of tests which should be run intelligently
func (c *HTTPClient) SelectTests(ctx context.Context, org, project, pipeline, build, stage, step, repo, sha, source, target, body string) (types.SelectTestsResp, error) {
	path := fmt.Sprintf(testEndpoint, c.AccountID, org, project, pipeline, build, stage, step, repo, sha, source, target)
	var resp types.SelectTestsResp
	var e types.SelectTestsReq
	err := json.Unmarshal([]byte(body), &e)
	if err != nil {
		return types.SelectTestsResp{}, err
	}
	ctx = context.WithValue(ctx, "reqId", sha)
	_, err = c.do(ctx, c.Endpoint+path, "POST", &e, &resp)
	return resp, err
}

// UploadCg uploads avro encoded callgraph to server
func (c *HTTPClient) UploadCg(ctx context.Context, org, project, pipeline, build, stage, step, repo, sha, source, target string, timeMs int64, cg []byte) error {
	path := fmt.Sprintf(cgEndpoint, c.AccountID, org, project, pipeline, build, stage, step, repo, sha, source, target, timeMs)
	ctx = context.WithValue(ctx, "reqId", sha)
	backoff := createBackoff(45 * 60 * time.Second)
	_, err := c.retry(ctx, c.Endpoint+path, "POST", &cg, nil, false, backoff)
	return err
}

func (c *HTTPClient) retry(ctx context.Context, method, path string, in, out interface{}, isOpen bool, b backoff.BackOff) (*http.Response, error) {
	for {
		var res *http.Response
		var err error
		if !isOpen {
			res, err = c.do(ctx, method, path, in, out)
		} else {
			res, err = c.open(ctx, method, path, in.(io.Reader))
		}

		// do not retry on Canceled or DeadlineExceeded
		if err := ctx.Err(); err != nil {
			logger.FromContext(ctx).Errorw("http: context canceled", "path", path, zap.Error(err))
			return res, err
		}

		duration := b.NextBackOff()

		if res != nil {
			// Check the response code. We retry on 5xx-range
			// responses to allow the server time to recover, as
			// 5xx's are typically not permanent errors and may
			// relate to outages on the server side.
			if res.StatusCode >= 500 {
				logger.FromContext(ctx).Errorw("http: ti-server error: reconnect and retry", "path", path, zap.Error(err))
				if duration == backoff.Stop {
					return nil, err
				}
				time.Sleep(duration)
				continue
			}
		} else if err != nil {
			logger.FromContext(ctx).Errorw("http: request error. Retrying ...", "path", path, zap.Error(err))
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
func (c *HTTPClient) do(ctx context.Context, path, method string, in, out interface{}) (*http.Response, error) {
	var r io.Reader

	if in != nil {
		buf := new(bytes.Buffer)
		json.NewEncoder(buf).Encode(in)
		r = buf
	}

	req, err := http.NewRequestWithContext(ctx, method, path, r)
	if err != nil {
		return nil, err
	}

	// the request should include the secret shared between
	// the agent and server for authorization.
	req.Header.Add("X-Harness-Token", c.Token)
	// adding sha as request-id for logging context
	sha := ctx.Value("reqId").(string)
	if len(sha) != 0 {
		req.Header.Add("X-Request-ID", sha)
	}
	res, err := c.client().Do(req)
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
			out := new(Error)
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

// helper function to open an http request
func (c *HTTPClient) open(ctx context.Context, path, method string, body io.Reader) (*http.Response, error) {
	req, err := http.NewRequestWithContext(ctx, method, path, body)
	if err != nil {
		return nil, err
	}
	req.Header.Add("X-Harness-Token", c.Token)
	return c.client().Do(req)
}

// client is a helper function that returns the default client
// if a custom client is not defined.
func (c *HTTPClient) client() *http.Client {
	if c.Client == nil {
		return defaultClient
	}
	return c.Client
}

func createInfiniteBackoff() *backoff.ExponentialBackOff {
	return createBackoff(0)
}

func createBackoff(maxElapsedTime time.Duration) *backoff.ExponentialBackOff {
	exp := backoff.NewExponentialBackOff()
	exp.MaxElapsedTime = maxElapsedTime
	return exp
}
