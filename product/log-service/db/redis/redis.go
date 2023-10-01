// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package redis

import (
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"io/ioutil"

	"github.com/sirupsen/logrus"

	"github.com/redis/go-redis/v9"
)

const (
	// max. number of concurrent connections that Redis can handle. This limit is set to 10k by default on the latest
	// Redis servers. To increase it, make sure it gets increased on the server side as well.
	connectionPool = 5000
)

func NewConnection(endpoint, password string, useTLS, useSentinel bool, certPathForTLS string, masterName string, sentinelAddrs []string) *redis.Cmdable {
	var client redis.Cmdable
	if useSentinel {
		// Create Sentinel instance
		client = redis.NewFailoverClient(&redis.FailoverOptions{
			MasterName:    masterName,
			SentinelAddrs: sentinelAddrs,
			Password:      password,
		})
	} else {
		// Create Redis instance
		opt := &redis.Options{
			Addr:     endpoint,
			Password: password,
			DB:       0,
			PoolSize: connectionPool,
		}
		if useTLS {
			newTlSConfig, err := newTlSConfig(certPathForTLS)
			if err != nil {
				logrus.Fatalf("could not get TLS config: %s", err)
				return nil
			}
			opt.TLSConfig = newTlSConfig
		}
		client = redis.NewClient(opt)
	}

	return &client
}

func newTlSConfig(certPathForTLS string) (*tls.Config, error) {
	// Create TLS config using cert PEM
	rootPem, err := ioutil.ReadFile(certPathForTLS)
	if err != nil {
		return nil, fmt.Errorf("could not read certificate file (%s), error: %s", certPathForTLS, err.Error())
	}

	roots := x509.NewCertPool()
	ok := roots.AppendCertsFromPEM(rootPem)
	if !ok {
		return nil, fmt.Errorf("error adding cert (%s) to pool, error: %s", certPathForTLS, err.Error())
	}
	return &tls.Config{RootCAs: roots}, nil
}
