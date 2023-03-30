// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

// Package bolt provides an embedded log datastore backed
// by a Bolt database. This package should be used for
// local testing purposes only and is not appropriate for
// production use.
package bolt

import (
	"bytes"
	"context"
	"errors"
	"io"
	"io/ioutil"
	"time"

	"github.com/harness/harness-core/product/log-service/store"

	bolt "go.etcd.io/bbolt"
)

var _ store.Store = (*Store)(nil)

// default bucket name
var bucket = []byte("ids")

// ErrNotFound is returned when the key is not found
// in the Bolt key value store.
var ErrNotFound = errors.New("bolt: key not found")

// Store provides an embedded log store using BoltDB as the
// backend. This is intended for testing purposes only and
// should not be used in production.
type Store struct {
	db *bolt.DB
}

// New open or creates a Bolt database at the provided path
// and returns a log Store backed by the database.
func New(path string) (*Store, error) {
	db, err := bolt.Open(path, 0600, nil)
	if err != nil {
		return nil, err
	}
	return newDatabase(db), nil
}

// newDatabase returns a new Store backed by an existing
// Bolt database.
func newDatabase(db *bolt.DB) *Store {
	db.Update(func(tx *bolt.Tx) error {
		tx.CreateBucketIfNotExists(bucket)
		return nil
	})
	return &Store{db}
}

// Close closes the Bolt datastore.
func (s *Store) Close() error {
	return s.db.Close()
}

// Download downloads the log file from the Bolt datastore.
func (s *Store) Download(ctx context.Context, key string) (io.ReadCloser, error) {
	var buf *bytes.Buffer
	var err = s.db.View(func(tx *bolt.Tx) error {
		b := tx.Bucket(bucket)
		v := b.Get([]byte(key))
		if v == nil {
			return ErrNotFound
		}
		buf = bytes.NewBuffer(v)
		return nil
	})
	return ioutil.NopCloser(buf), err
}

// DownloadLink is a no-op
func (s *Store) DownloadLink(ctx context.Context, key string, expire time.Duration) (string, error) {
	return "", errors.New("not implemented")
}

// Upload uploads the log file to the Bolt datastore.
func (s *Store) Upload(ctx context.Context, key string, r io.Reader) error {
	buf, err := ioutil.ReadAll(r)
	if err != nil {
		return err
	}
	return s.db.Update(func(tx *bolt.Tx) error {
		b := tx.Bucket(bucket)
		err := b.Put([]byte(key), buf)
		return err
	})
}

// UploadLink is a no-op
func (s *Store) UploadLink(ctx context.Context, key string, expire time.Duration) (string, error) {
	return "", errors.New("not implemented")
}

// Delete deletes the log file from the Bolt datastore.
func (s *Store) Delete(ctx context.Context, key string) error {
	return s.db.Update(func(tx *bolt.Tx) error {
		b := tx.Bucket(bucket)
		err := b.Delete([]byte(key))
		return err
	})
}

// Ping pings the store for readiness
func (s *Store) Ping() error {
	return errors.New("not implemented")
}
