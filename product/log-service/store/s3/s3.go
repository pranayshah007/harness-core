// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

// Package S3 provides a log storage driver backed by
// S3 or a S3-compatible storage system.
package s3

import (
	"context"
	"io"
	"path"
	"strings"
	"time"

	"github.com/aws/aws-sdk-go/aws/credentials"
	"github.com/harness/harness-core/product/log-service/store"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/s3"
	"github.com/aws/aws-sdk-go/service/s3/s3manager"
)

var _ store.Store = (*Store)(nil)

// Store provides a log storage driver backed by S3 or a
// S3 compatible store.
type Store struct {
	bucket  string
	prefix  string
	acl     string
	session *session.Session
}

// NewEnv returns a new S3 log store from the environment.
func NewEnv(bucket, prefix, endpoint string, pathStyle bool, accessKeyID, accessSecretKey, region, acl string) *Store {
	disableSSL := false

	if endpoint != "" {
		disableSSL = !strings.HasPrefix(endpoint, "https://")
	}

	return &Store{
		bucket: bucket,
		prefix: prefix,
		acl:    acl,
		session: session.Must(
			session.NewSession(&aws.Config{
				Region:           aws.String(region),
				Endpoint:         aws.String(endpoint),
				DisableSSL:       aws.Bool(disableSSL),
				S3ForcePathStyle: aws.Bool(pathStyle),
				Credentials:      credentials.NewStaticCredentials(accessKeyID, accessSecretKey, ""),
			}),
		),
	}
}

// New returns a new S3 log store.
func New(session *session.Session, bucket, prefix string) *Store {
	return &Store{
		bucket:  bucket,
		prefix:  prefix,
		session: session,
	}
}

// Download downloads a log stream from the S3 datastore.
func (s *Store) Download(ctx context.Context, key string) (io.ReadCloser, error) {
	svc := s3.New(s.session)
	keyWithPrefix := path.Join("/", s.prefix, key)
	out, err := svc.GetObject(&s3.GetObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String(keyWithPrefix),
	})
	if err != nil {
		return nil, err
	}
	return out.Body, nil
}

// DownloadLink creates a pre-signed link that can be used to
// download the logs to the S3 datastore.
func (s *Store) DownloadLink(ctx context.Context, key string, expire time.Duration) (string, error) {
	svc := s3.New(s.session)
	keyWithPrefix := path.Join("/", s.prefix, key)
	req, _ := svc.GetObjectRequest(&s3.GetObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String(keyWithPrefix),
	})
	return req.Presign(expire)
}

// Upload uploads the log stream from Reader r to the
// S3 datastore.
func (s *Store) Upload(ctx context.Context, key string, r io.Reader) error {
	uploader := s3manager.NewUploader(s.session, func(u *s3manager.Uploader) {
		u.PartSize = 32 * 1024 * 1024 // 32MB per part
	})
	keyWithPrefix := path.Join("/", s.prefix, key)
	input := &s3manager.UploadInput{
		ACL:    aws.String(s.acl),
		Bucket: aws.String(s.bucket),
		Key:    aws.String(keyWithPrefix),
		Body:   r,
	}
	_, err := uploader.Upload(input)
	return err
}

// UploadLink creates a pre-signed link that can be used to
// upload the logs to the S3 datastore.
func (s *Store) UploadLink(ctx context.Context, key string, expire time.Duration) (string, error) {
	svc := s3.New(s.session)
	keyWithPrefix := path.Join("/", s.prefix, key)
	req, _ := svc.PutObjectRequest(&s3.PutObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String(keyWithPrefix),
	})
	return req.Presign(expire)
}

// Delete purges the log stream from the S3 datastore.
func (s *Store) Delete(ctx context.Context, key string) error {
	svc := s3.New(s.session)
	keyWithPrefix := path.Join("/", s.prefix, key)
	_, err := svc.DeleteObject(&s3.DeleteObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String(keyWithPrefix),
	})
	return err
}

// Ping pings the store for readiness
func (s *Store) Ping() error {
	svc := s3.New(s.session)

	// Check if the bucket exists
	_, err := svc.HeadBucket(&s3.HeadBucketInput{
		Bucket: aws.String(s.bucket),
	})
	if err != nil {
		return err
	}

	return nil
}
