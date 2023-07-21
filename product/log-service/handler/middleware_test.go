// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"context"
	"net/http"
	"net/http/httptest"
	"net/url"
	"testing"
	"time"

	"github.com/dchest/authcookie"
	"github.com/harness/harness-core/product/platform/client"
	"github.com/stretchr/testify/assert"

	"github.com/harness/harness-core/product/log-service/cache/memory"
	"github.com/harness/harness-core/product/log-service/config"
	"github.com/harness/harness-core/product/log-service/entity"
)

type MockHandler struct{}

func (*MockHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {}

func TestTokenGenerationMiddleware_Success(t *testing.T) {
	var config config.Config
	globalToken := "token"
	config.Auth.GlobalToken = globalToken
	v := url.Values{}
	v.Add("accountID", "account")
	header := http.Header{}
	header.Add(authHeader, globalToken)
	httpReq := &http.Request{Form: v, Header: header}
	ngClient := client.NewHTTPClient(config.Platform.BaseURL, false, "")
	fn := TokenGenerationMiddleware(config, true, ngClient)
	mockHandler := &MockHandler{}
	handlerFunc := fn(mockHandler)
	writer := httptest.NewRecorder()
	handlerFunc.ServeHTTP(writer, httpReq)
	assert.Equal(t, writer.Code, 200)
}

func TestTokenGenerationMiddleware_TokenInURL_Success(t *testing.T) {
	var config config.Config
	globalToken := "token"
	config.Auth.GlobalToken = globalToken
	v := url.Values{}
	v.Add("accountID", "account")
	v.Add(authHeader, globalToken)
	header := http.Header{}
	httpReq := &http.Request{Form: v, Header: header}
	ngClient := client.NewHTTPClient(config.Platform.BaseURL, false, "")
	fn := TokenGenerationMiddleware(config, true, ngClient)
	mockHandler := &MockHandler{}
	handlerFunc := fn(mockHandler)
	writer := httptest.NewRecorder()
	handlerFunc.ServeHTTP(writer, httpReq)
	assert.Equal(t, writer.Code, 200)
}

func TestTokenGenerationMiddleware_IncorrectToken(t *testing.T) {
	var config config.Config
	globalToken := "token"
	config.Auth.GlobalToken = globalToken
	v := url.Values{}
	v.Add("accountID", "account")
	header := http.Header{}
	header.Add(authHeader, "incorrect_token")
	httpReq := &http.Request{Form: v, Header: header}
	ngClient := client.NewHTTPClient(config.Platform.BaseURL, false, "")
	fn := TokenGenerationMiddleware(config, true, ngClient)
	mockHandler := &MockHandler{}
	handlerFunc := fn(mockHandler)
	writer := httptest.NewRecorder()
	handlerFunc.ServeHTTP(writer, httpReq)
	assert.Equal(t, writer.Code, 400)
}

func TestTokenGenerationMiddleware_AccountIDAbsent(t *testing.T) {
	var config config.Config
	globalToken := "token"
	config.Auth.GlobalToken = globalToken
	header := http.Header{}
	header.Add(authHeader, "token")
	httpReq := &http.Request{Header: header}
	ngClient := client.NewHTTPClient(config.Platform.BaseURL, false, "")
	fn := TokenGenerationMiddleware(config, true, ngClient)
	mockHandler := &MockHandler{}
	handlerFunc := fn(mockHandler)
	writer := httptest.NewRecorder()
	handlerFunc.ServeHTTP(writer, httpReq)
	assert.Equal(t, writer.Code, 400)
}

func TestTokenGenerationMiddleware_SkipAccountIDCheck(t *testing.T) {
	var config config.Config
	globalToken := "token"
	config.Auth.GlobalToken = globalToken
	header := http.Header{}
	header.Add(authHeader, "token")
	httpReq := &http.Request{Header: header}
	ngClient := client.NewHTTPClient(config.Platform.BaseURL, false, "")
	fn := TokenGenerationMiddleware(config, false, ngClient)
	mockHandler := &MockHandler{}
	handlerFunc := fn(mockHandler)
	writer := httptest.NewRecorder()
	handlerFunc.ServeHTTP(writer, httpReq)
	assert.Equal(t, writer.Code, 200)
}

func TestAuthMiddleware_Success(t *testing.T) {
	var config config.Config
	logSecret := "secret"
	accountID := "account"
	config.Auth.LogSecret = logSecret
	cookie := authcookie.NewSinceNow(accountID, 1*time.Hour, []byte(logSecret))
	header := http.Header{}
	v := url.Values{}
	v.Add("accountID", accountID)
	v.Add("key", "key")
	header.Add(authHeader, cookie)
	httpReq := &http.Request{Form: v, Header: header}
	ngClient := client.NewHTTPClient(config.Platform.BaseURL, false, "")
	fn := AuthMiddleware(config, ngClient, false)
	mockHandler := &MockHandler{}
	handlerFunc := fn(mockHandler)
	writer := httptest.NewRecorder()
	handlerFunc.ServeHTTP(writer, httpReq)
	assert.Equal(t, writer.Code, 200)
}

func TestAuthMiddleware_TokenInURL_Success(t *testing.T) {
	var config config.Config
	logSecret := "secret"
	accountID := "account"
	config.Auth.LogSecret = logSecret
	cookie := authcookie.NewSinceNow(accountID, 1*time.Hour, []byte(logSecret))
	header := http.Header{}
	v := url.Values{}
	v.Add("accountID", accountID)
	v.Add("key", "key")
	v.Add(authHeader, cookie)
	httpReq := &http.Request{Form: v, Header: header}
	ngClient := client.NewHTTPClient(config.Platform.BaseURL, false, "")
	fn := AuthMiddleware(config, ngClient, false)
	mockHandler := &MockHandler{}
	handlerFunc := fn(mockHandler)
	writer := httptest.NewRecorder()
	handlerFunc.ServeHTTP(writer, httpReq)
	assert.Equal(t, writer.Code, 200)
}

func TestAuthMiddleware_IncorrectSecret(t *testing.T) {
	var config config.Config
	logSecret := "secret"
	incorrectLogSecret := "notsecret"
	accountID := "account"
	config.Auth.LogSecret = logSecret
	// Generate cookie with a different secret
	cookie := authcookie.NewSinceNow(accountID, 1*time.Hour, []byte(incorrectLogSecret))
	header := http.Header{}
	v := url.Values{}
	v.Add("accountID", accountID)
	v.Add("key", "key")
	header.Add(authHeader, cookie)
	httpReq := &http.Request{Form: v, Header: header}
	ngClient := client.NewHTTPClient(config.Platform.BaseURL, false, "")
	fn := AuthMiddleware(config, ngClient, false)
	mockHandler := &MockHandler{}
	handlerFunc := fn(mockHandler)
	writer := httptest.NewRecorder()
	handlerFunc.ServeHTTP(writer, httpReq)
	assert.Equal(t, writer.Code, 400)
}

func TestAuthMiddleware_IncorrectAccount(t *testing.T) {
	var config config.Config
	logSecret := "secret"
	incorrectaccountID := "notaccount"
	accountID := "account"
	config.Auth.LogSecret = logSecret
	// Generate cookie with a different account
	cookie := authcookie.NewSinceNow(incorrectaccountID, 1*time.Hour, []byte(logSecret))
	header := http.Header{}
	v := url.Values{}
	v.Add("accountID", accountID)
	v.Add("key", "key")
	header.Add(authHeader, cookie)
	httpReq := &http.Request{Form: v, Header: header}
	ngClient := client.NewHTTPClient(config.Platform.BaseURL, false, "")
	fn := AuthMiddleware(config, ngClient, false)
	mockHandler := &MockHandler{}
	handlerFunc := fn(mockHandler)
	writer := httptest.NewRecorder()
	handlerFunc.ServeHTTP(writer, httpReq)
	assert.Equal(t, writer.Code, 400)
}

func TestAuthMiddleware_NoKeyPresent(t *testing.T) {
	var config config.Config
	logSecret := "secret"
	incorrectaccountID := "notaccount"
	accountID := "account"
	config.Auth.LogSecret = logSecret
	// Generate cookie with a different account
	cookie := authcookie.NewSinceNow(incorrectaccountID, 1*time.Hour, []byte(logSecret))
	header := http.Header{}
	v := url.Values{}
	v.Add("accountID", accountID)
	header.Add(authHeader, cookie)
	httpReq := &http.Request{Form: v, Header: header}
	ngClient := client.NewHTTPClient(config.Platform.BaseURL, false, "")
	fn := AuthMiddleware(config, ngClient, false)
	mockHandler := &MockHandler{}
	handlerFunc := fn(mockHandler)
	writer := httptest.NewRecorder()
	handlerFunc.ServeHTTP(writer, httpReq)
	assert.Equal(t, writer.Code, 400)
}

func TestValidatePrefixRequest_success(t *testing.T) {
	path := &url.URL{
		Host: "localhost/blob/prefix/",
		Path: "accountID=accId&prefix=accountId:accId/path1:path/runSequence:9/pipeline:level0",
	}
	header := http.Header{}
	httpReq := &http.Request{URL: path, Header: header}
	fn := ValidatePrefixRequest()
	mockHandler := &MockHandler{}
	handlerFunc := fn(mockHandler)
	writer := httptest.NewRecorder()
	handlerFunc.ServeHTTP(writer, httpReq)
	assert.Equal(t, writer.Code, 200)
}

func TestValidatePrefixRequest_error(t *testing.T) {
	path := &url.URL{
		Host: "localhost/blob/prefix/",
		Path: "accountID=accId&prefix=accountId:accId/path1:path",
	}
	header := http.Header{}
	httpReq := &http.Request{URL: path, Header: header}
	fn := ValidatePrefixRequest()
	mockHandler := &MockHandler{}
	handlerFunc := fn(mockHandler)
	writer := httptest.NewRecorder()
	handlerFunc.ServeHTTP(writer, httpReq)
	assert.Equal(t, writer.Code, 400)
}

func TestRequiredQueryParams_success(t *testing.T) {
	path := "accountID=accId&prefix=accountId:accId/path1:path/runSequence:9/pipeline:level0"
	url := &url.URL{
		Host:     "localhost/blob/prefix/",
		Path:     path,
		RawQuery: path,
	}

	header := http.Header{}
	httpReq := &http.Request{URL: url, Header: header}
	fn := RequiredQueryParams("accountID", "prefix")
	mockHandler := &MockHandler{}
	handlerFunc := fn(mockHandler)
	writer := httptest.NewRecorder()
	handlerFunc.ServeHTTP(writer, httpReq)
	assert.Equal(t, writer.Code, 200)
}

func TestRequiredQueryParams_error(t *testing.T) {
	path := "prefix=accountId:accId/path1:path/runSequence:9/pipeline:level0"
	url := &url.URL{
		Host:     "localhost/blob/prefix/",
		Path:     path,
		RawQuery: path,
	}

	header := http.Header{}
	httpReq := &http.Request{URL: url, Header: header}
	fn := RequiredQueryParams("accountID", "prefix")
	mockHandler := &MockHandler{}
	handlerFunc := fn(mockHandler)
	writer := httptest.NewRecorder()
	handlerFunc.ServeHTTP(writer, httpReq)
	assert.Equal(t, writer.Code, 404)
}

func TestCacheRequest_whenCacheDidntExists(t *testing.T) {
	path := "accountID=accId&prefix=accountId:accId/path1:path/runSequence:9/pipeline:level0"
	url := &url.URL{
		Host:     "localhost/blob/prefix/",
		Path:     path,
		RawQuery: path,
	}

	cache := memory.New()

	header := http.Header{}
	httpReq := &http.Request{URL: url, Header: header}
	fn := CacheRequest(cache)
	mockHandler := &MockHandler{}
	handlerFunc := fn(mockHandler)
	writer := httptest.NewRecorder()
	handlerFunc.ServeHTTP(writer, httpReq)
	assert.Equal(t, writer.Code, 200)
}

func TestCacheRequest_whenCacheExistsWithStatusQueued(t *testing.T) {
	prefix := "accountId:accId/path1:path/runSequence:9/pipeline:level0"
	path := "accountID=accId&prefix=" + prefix
	url := &url.URL{
		Host:     "localhost/blob/prefix/",
		Path:     path,
		RawQuery: path,
	}

	info := entity.ResponsePrefixDownload{
		Value:  "link",
		Status: entity.QUEUED,
	}

	cache := memory.New()
	cache.Create(context.Background(), prefix, info, time.Hour)

	header := http.Header{}
	httpReq := &http.Request{URL: url, Header: header}
	fn := CacheRequest(cache)
	mockHandler := &MockHandler{}
	handlerFunc := fn(mockHandler)
	writer := httptest.NewRecorder()
	handlerFunc.ServeHTTP(writer, httpReq)
	assert.Equal(t, writer.Code, 200)
}

func TestCacheRequest_whenCacheExistsWithStatusInProgress(t *testing.T) {
	prefix := "accountId:accId/path1:path/runSequence:9/pipeline:level0"
	path := "accountID=accId&prefix=" + prefix
	url := &url.URL{
		Host:     "localhost/blob/prefix/",
		Path:     path,
		RawQuery: path,
	}

	info := entity.ResponsePrefixDownload{
		Value:  "link",
		Status: entity.IN_PROGRESS,
	}

	cache := memory.New()
	cache.Create(context.Background(), prefix, info, time.Hour)

	header := http.Header{}
	httpReq := &http.Request{URL: url, Header: header}
	fn := CacheRequest(cache)
	mockHandler := &MockHandler{}
	handlerFunc := fn(mockHandler)
	writer := httptest.NewRecorder()
	handlerFunc.ServeHTTP(writer, httpReq)
	assert.Equal(t, writer.Code, 200)
}

func TestCacheRequest_whenCacheExistsWithStatusSuccess(t *testing.T) {
	prefix := "accountId:accId/path1:path/runSequence:9/pipeline:level0"
	path := "accountID=accId&prefix=" + prefix
	url := &url.URL{
		Host:     "localhost/blob/prefix/",
		Path:     path,
		RawQuery: path,
	}

	info := entity.ResponsePrefixDownload{
		Value:  "link",
		Status: entity.SUCCESS,
	}

	cache := memory.New()
	cache.Create(context.Background(), prefix, info, time.Hour)

	header := http.Header{}
	httpReq := &http.Request{URL: url, Header: header}
	fn := CacheRequest(cache)
	mockHandler := &MockHandler{}
	handlerFunc := fn(mockHandler)
	writer := httptest.NewRecorder()
	handlerFunc.ServeHTTP(writer, httpReq)
	assert.Equal(t, writer.Code, 200)
}

func TestCacheRequest_whenCacheExistsWithStatusError(t *testing.T) {
	prefix := "accountId:accId/path1:path/runSequence:9/pipeline:level0"
	path := "accountID=accId&prefix=" + prefix
	url := &url.URL{
		Host:     "localhost/blob/prefix/",
		Path:     path,
		RawQuery: path,
	}

	info := entity.ResponsePrefixDownload{
		Value:   "link",
		Status:  entity.ERROR,
		Message: "err: generic error",
	}

	cache := memory.New()
	cache.Create(context.Background(), prefix, info, time.Hour)

	header := http.Header{}
	httpReq := &http.Request{URL: url, Header: header}
	fn := CacheRequest(cache)
	mockHandler := &MockHandler{}
	handlerFunc := fn(mockHandler)
	writer := httptest.NewRecorder()
	handlerFunc.ServeHTTP(writer, httpReq)
	assert.Equal(t, writer.Code, 200)
	assert.Equal(t, false, cache.Exists(context.Background(), path))
}
