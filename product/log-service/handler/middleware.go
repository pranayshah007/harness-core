// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"context"
	"errors"
	"fmt"
	"net/http"

	"github.com/dchest/authcookie"
	"github.com/harness/harness-core/product/log-service/config"
	"github.com/harness/harness-core/product/platform/client"
)

const authHeader = "X-Harness-Token"
const authAPIKeyHeader = "x-api-key"
const routingIDparam = "routingId"

// TokenGenerationMiddleware is middleware to ensure that the incoming request is allowed to
// invoke token-generation endpoints.
func TokenGenerationMiddleware(config config.Config, validateAccount bool, ngClient *client.HTTPClient) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if validateAccount {
				accountID := r.FormValue(accountIDParam)
				if accountID == "" {
					WriteBadRequest(w, errors.New("no account ID in query params"))
					return
				}
			}

			//Get X-api-key from header, if not then check for token
			inputApiKey := r.Header.Get(authAPIKeyHeader)
			if inputApiKey != "" {
				err := doApiKeyAuthentication(inputApiKey, r.FormValue(accountIDParam), r.FormValue(routingIDparam), ngClient)
				if err != nil {
					WriteBadRequest(w, errors.New("apikey in request not authorized for receiving tokens"))
					return
				}
			} else {
				// Try to get token from the header or the URL param
				inputToken := r.Header.Get(authHeader)
				if inputToken == "" {
					inputToken = r.FormValue(authHeader)
				}

				if inputToken == "" {
					WriteBadRequest(w, errors.New("no token or x-api-key in header"))
					return
				}

				if inputToken != config.Auth.GlobalToken {
					// Error: invalid token
					WriteBadRequest(w, errors.New("token in request not authorized for receiving tokens"))
					return
				}
			}

			next.ServeHTTP(w, r)
		})
	}
}

// GetAuthFunc is middleware to ensure that the incoming request is allowed to access resources
// at the specific accountID
func AuthMiddleware(config config.Config, ngClient *client.HTTPClient) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {

			accountID := r.FormValue(accountIDParam)
			if accountID == "" {
				WriteBadRequest(w, errors.New("no account ID in query params"))
				return
			}

			// Try to get token from the header or the URL param
			inputApiKey := r.Header.Get(authAPIKeyHeader)
			if inputApiKey != "" {
				err := doApiKeyAuthentication(inputApiKey, r.FormValue(accountIDParam), r.FormValue(routingIDparam), ngClient)
				if err != nil {
					WriteBadRequest(w, errors.New("apikey in request not authorized for receiving tokens"))
					return
				}
			} else {
				inputToken := r.Header.Get(authHeader)
				if inputToken == "" {
					inputToken = r.FormValue(authHeader)
				}

				if inputToken == "" {
					WriteBadRequest(w, errors.New("no token in header"))
					return
				}
				// accountID in token should be same as accountID in URL
				secret := []byte(config.Auth.LogSecret)
				login := authcookie.Login(inputToken, secret)
				if login == "" || login != accountID {
					WriteBadRequest(w, errors.New(fmt.Sprintf("operation not permitted for accountID: %s", accountID)))
					return
				}
			}

			// Validate that a key / keys field is present in the request
			if r.FormValue(keyParam) == "" && r.FormValue(keysParam) == "" {
				WriteBadRequest(w, errors.New("no key exists in the URL"))
				return
			}

			next.ServeHTTP(w, r)
		})
	}
}

func doApiKeyAuthentication(inputApiKey, accountID, routingId string, ngClient *client.HTTPClient) error {
	err := ngClient.ValidateApiKey(context.Background(), accountID, routingId, inputApiKey)
	return err
}
