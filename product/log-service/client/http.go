// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package client

import (
	"bufio"
	"bytes"
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"

	"fmt"
	"io"
	"io/ioutil"
	"net/http"
	"time"

	"github.com/cenkalti/backoff/v4"
	"github.com/harness/harness-core/product/log-service/logger"
	"github.com/harness/harness-core/product/log-service/stream"
)

var _ Client = (*HTTPClient)(nil)

const (
	streamEndpoint       = "/stream?accountID=%s&key=%s"
	infoEndpoint         = "/info/stream"
	blobEndpoint         = "/blob?accountID=%s&key=%s"
	uploadLinkEndpoint   = "/blob/link/upload?accountID=%s&key=%s"
	downloadLinkEndpoint = "/blob/link/download?accountID=%s&key=%s"
)

// defaultClient is the default http.Client.
var defaultClient = &http.Client{
	CheckRedirect: func(*http.Request, []*http.Request) error {
		return http.ErrUseLastResponse
	},
}

// NewHTTPClient returns a new HTTPClient.
func NewHTTPClient(endpoint, accountID, token string, skipverify bool, additionalCertsDir string) *HTTPClient {
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
				fmt.Errorf("error adding cert (%s) to pool, please check format of the certs provided", path)
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
	Token      string // Per account token to validate against
	AccountID  string
	SkipVerify bool
}

// Upload uploads the file to remote storage.
func (c *HTTPClient) Upload(ctx context.Context, key string, r io.Reader) error {
	path := fmt.Sprintf(blobEndpoint, c.AccountID, key)
	backoff := createInfiniteBackoff()
	resp, err := c.retry(ctx, c.Endpoint+path, "POST", r, nil, true, backoff)
	if resp != nil {
		defer resp.Body.Close()
	}
	return err
}

// UploadLink returns a secure link that can be used to
// upload a file to remote storage.
func (c *HTTPClient) UploadLink(ctx context.Context, key string) (*Link, error) {
	path := fmt.Sprintf(uploadLinkEndpoint, c.AccountID, key)
	out := new(Link)
	backoff := createBackoff(60 * time.Second)
	_, err := c.retry(ctx, c.Endpoint+path, "POST", nil, out, false, backoff)
	return out, err
}

// Download downloads the file from remote storage.
func (c *HTTPClient) Download(ctx context.Context, key string) (io.ReadCloser, error) {
	path := fmt.Sprintf(blobEndpoint, c.AccountID, key)
	resp, err := c.open(ctx, c.Endpoint+path, "GET", nil)
	return resp.Body, err
}

// UploadUsingLink takes in a reader and a link object and uploads directly to
// remote storage.
func (c *HTTPClient) UploadUsingLink(ctx context.Context, link string, r io.Reader) error {
	backoff := createBackoff(60 * time.Second)
	_, err := c.retry(ctx, link, "PUT", r, nil, true, backoff)
	return err
}

// DownloadLink returns a secure link that can be used to
// download a file from remote storage.
func (c *HTTPClient) DownloadLink(ctx context.Context, key string) (*Link, error) {
	path := fmt.Sprintf(downloadLinkEndpoint, c.AccountID, key)
	out := new(Link)
	_, err := c.do(ctx, c.Endpoint+path, "POST", nil, out)
	return out, err
}

// Open opens the data stream.
func (c *HTTPClient) Open(ctx context.Context, key string) error {
	path := fmt.Sprintf(streamEndpoint, c.AccountID, key)
	backoff := createBackoff(10 * time.Second)
	_, err := c.retry(ctx, c.Endpoint+path, "POST", nil, nil, false, backoff)
	return err
}

// Close closes the data stream.
func (c *HTTPClient) Close(ctx context.Context, key string) error {
	path := fmt.Sprintf(streamEndpoint, c.AccountID, key)
	_, err := c.do(ctx, c.Endpoint+path, "DELETE", nil, nil)
	return err
}

// Write writes logs to the data stream.
func (c *HTTPClient) Write(ctx context.Context, key string, lines []*stream.Line) error {
	path := fmt.Sprintf(streamEndpoint, c.AccountID, key)
	_, err := c.do(ctx, c.Endpoint+path, "PUT", &lines, nil)
	return err
}

// Tail tails the data stream.
func (c *HTTPClient) Tail(ctx context.Context, key string) (<-chan string, <-chan error) {
	errc := make(chan error, 1)
	outc := make(chan string, 100)

	path := fmt.Sprintf(streamEndpoint, c.AccountID, key)
	res, err := c.open(ctx, c.Endpoint+path, "GET", nil)
	if err != nil {
		errc <- err
		return outc, errc
	}
	if res.StatusCode > 299 {
		errc <- errors.New("cannot stream repository")
		return outc, errc
	}
	go func(res *http.Response) {
		reader := bufio.NewReader(res.Body)
		defer res.Body.Close()
		for {
			line, err := reader.ReadBytes('\n')
			if err != nil {
				errc <- err
			}
			select {
			case <-ctx.Done():
				return
			default:
			}
			outc <- string(line)
		}
	}(res)
	return outc, errc
}

// Info returns the stream information.
func (c *HTTPClient) Info(ctx context.Context) (*stream.Info, error) {
	out := new(stream.Info)
	_, err := c.do(ctx, c.Endpoint+infoEndpoint, "GET", nil, out)
	return out, err
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
