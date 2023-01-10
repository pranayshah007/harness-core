// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package client

import (
	"bytes"
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/ioutil"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/cenkalti/backoff/v4"
	"github.com/harness/harness-core/product/log-service/logger"
)

const (
	apiKeyEndpoint   = "/ng/api/token/validate?accountIdentifier=%s"
	authAPIKeyHeader = "x-api-key"
)

// defaultClient is the default http.Client.
var defaultClient = &http.Client{
	CheckRedirect: func(*http.Request, []*http.Request) error {
		return http.ErrUseLastResponse
	},
}

// NewHTTPClient returns a new HTTPClient.
func NewHTTPClient(endpoint string, skipverify bool, additionalCertsDir string) *HTTPClient {
	client := &HTTPClient{
		Endpoint:   endpoint,
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
	} else if additionalCertsDir != "" {
		// If additional certs are specified, we append them to the existing cert chain

		// Use the system certs if possible
		rootCAs, _ := x509.SystemCertPool()
		if rootCAs == nil {
			rootCAs = x509.NewCertPool()
		}

		fmt.Printf("additional certs dir to allow: %s\n", additionalCertsDir)

		files, err := os.ReadDir(additionalCertsDir)
		if err != nil {
			fmt.Errorf("could not read directory %s, error: %s", additionalCertsDir, err)
			client.Client = clientWithRootCAs(skipverify, rootCAs)
			return client
		}

		// Go through all certs in this directory and add them to the global certs
		for _, f := range files {
			path := filepath.Join(additionalCertsDir, f.Name())
			fmt.Printf("trying to add certs at: %s to root certs\n", path)
			// Create TLS config using cert PEM
			rootPem, err := os.ReadFile(path)
			if err != nil {
				fmt.Errorf("could not read certificate file (%s), error: %s", path, err.Error())
				continue
			}
			// Append certs to the global certs
			ok := rootCAs.AppendCertsFromPEM(rootPem)
			if !ok {
				fmt.Errorf("error adding cert (%s) to pool, error: %s", path, err.Error())
				continue
			}
			fmt.Printf("successfully added cert at: %s to root certs", path)
		}
		client.Client = clientWithRootCAs(skipverify, rootCAs)
	}
	return client
}

func clientWithRootCAs(skipverify bool, rootCAs *x509.CertPool) *http.Client {
	// Create the HTTP Client with certs
	config := &tls.Config{
		InsecureSkipVerify: skipverify,
	}
	if rootCAs != nil {
		config.RootCAs = rootCAs
	}
	return &http.Client{
		CheckRedirect: func(*http.Request, []*http.Request) error {
			return http.ErrUseLastResponse
		},
		Transport: &http.Transport{
			Proxy:           http.ProxyFromEnvironment,
			TLSClientConfig: config,
		},
	}
}

// HTTPClient provides an http service client.
type HTTPClient struct {
	Client     *http.Client
	Endpoint   string // Example: http://localhost:port
	SkipVerify bool
}

// Validate apikey of an account for auth.
func (c *HTTPClient) ValidateApiKey(ctx context.Context, accountID, routingId, apiKey string) error {
	path := fmt.Sprintf(apiKeyEndpoint, accountID)
	if routingId != "" {
		path = path + "&routingId=" + routingId
	}

	backoff := createBackoff(60 * time.Second)
	payload := strings.NewReader(apiKey)
	out, err := c.retry(ctx, c.Endpoint+path, "POST", apiKey, payload, nil, true, backoff)
	if err == nil && out.StatusCode != http.StatusOK {
		err = fmt.Errorf("Error Authenticating Apikey, Response: ", out.StatusCode)
	}
	return err
}

func createBackoff(maxElapsedTime time.Duration) *backoff.ExponentialBackOff {
	exp := backoff.NewExponentialBackOff()
	exp.MaxElapsedTime = maxElapsedTime
	return exp
}

func (c *HTTPClient) retry(ctx context.Context, path, method, apiKey string, in, out interface{}, isOpen bool, b backoff.BackOff) (*http.Response, error) {
	for {
		var res *http.Response
		var err error
		if !isOpen {
			res, err = c.do(ctx, path, method, apiKey, in, out)
		} else {
			res, err = c.open(ctx, path, method, apiKey, in.(io.Reader))
		}

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
func (c *HTTPClient) do(ctx context.Context, path, method, apiKey string, in, out interface{}) (*http.Response, error) {
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
	if apiKey != "" {
		req.Header.Add(authAPIKeyHeader, apiKey)
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
func (c *HTTPClient) open(ctx context.Context, path, method, apiKey string, body io.Reader) (*http.Response, error) {
	req, err := http.NewRequestWithContext(ctx, method, path, body)
	if err != nil {
		return nil, err
	}
	if apiKey != "" {
		req.Header.Add(authAPIKeyHeader, apiKey)
	}
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
