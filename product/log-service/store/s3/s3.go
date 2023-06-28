// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

// Package S3 provides a log storage driver backed by
// S3 or a S3-compatible storage system.
package s3

import (
	"context"
	"crypto/tls"
	"io"
	"net/http"
	"path"
	"time"

	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/feature/s3/manager"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/aws/aws-sdk-go-v2/service/s3/types"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"

	"github.com/harness/harness-core/product/log-service/store"
)

var _ store.Store = (*Store)(nil)

// Store provides a log storage driver backed by S3 or a
// S3 compatible store.
type Store struct {
	bucket         string
	prefix         string
	acl            string
	service        *s3.Client
	presignService *s3.PresignClient
}

// NewEnv returns a new S3 log store from the environment.
func NewEnv(bucket, prefix, endpoint string, pathStyle bool, accessKeyID, accessSecretKey, region, acl string) *Store {
	if endpoint != "" {
		http.DefaultTransport.(*http.Transport).TLSClientConfig = &tls.Config{InsecureSkipVerify: true}
	}

	creds := credentials.NewStaticCredentialsProvider(accessKeyID, accessSecretKey, "")

	cfg, _ := config.LoadDefaultConfig(
		context.Background(),
		config.WithCredentialsProvider(creds),
		config.WithRegion(region),
		config.WithEndpointResolverWithOptions(
			aws.EndpointResolverWithOptionsFunc(
				func(service, reg string, options ...interface{}) (aws.Endpoint, error) {
					if service == s3.ServiceID && reg == region {
						return aws.Endpoint{
							URL:           endpoint,
							PartitionID:   "aws",
							SigningRegion: region,
						}, nil
					}
					return aws.Endpoint{}, &aws.EndpointNotFoundError{}
				},
			),
		),
	)

	svc := s3.NewFromConfig(cfg, func(o *s3.Options) {
		o.UsePathStyle = pathStyle
	})

	presign := s3.NewPresignClient(svc)

	return &Store{
		bucket:         bucket,
		prefix:         prefix,
		acl:            acl,
		service:        svc,
		presignService: presign,
	}
}

// New returns a new S3 log store.
func New(svc *s3.Client, bucket, prefix string) *Store {
	return &Store{
		bucket:  bucket,
		prefix:  prefix,
		service: svc,
	}
}

// Download downloads a log stream from the S3 datastore.
func (s *Store) Download(ctx context.Context, key string) (io.ReadCloser, error) {
	keyWithPrefix := path.Join("/", s.prefix, key)
	out, err := s.service.GetObject(ctx, &s3.GetObjectInput{
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
	keyWithPrefix := path.Join("/", s.prefix, key)
	req, _ := s.presignService.PresignGetObject(ctx, &s3.GetObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String(keyWithPrefix),
	}, func(opts *s3.PresignOptions) {
		opts.Expires = time.Duration(int64(expire.Seconds()) * int64(time.Second))
	})

	return req.URL, nil
}

// Upload uploads the log stream from Reader r to the
// S3 datastore.
func (s *Store) Upload(ctx context.Context, key string, r io.Reader) error {
	uploader := manager.NewUploader(s.service, func(u *manager.Uploader) {
		u.PartSize = 32 * 1024 * 1024 // 32MB per part
	})
	keyWithPrefix := path.Join("/", s.prefix, key)
	input := &s3.PutObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String(keyWithPrefix),
		Body:   r,
		ACL:    types.ObjectCannedACL(s.acl),
	}
	_, err := uploader.Upload(ctx, input)
	return err
}

// UploadLink creates a pre-signed link that can be used to
// upload the logs to the S3 datastore.
func (s *Store) UploadLink(ctx context.Context, key string, expire time.Duration) (string, error) {
	keyWithPrefix := path.Join("/", s.prefix, key)
	req, _ := s.presignService.PresignPutObject(ctx, &s3.PutObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String(keyWithPrefix),
	}, func(opts *s3.PresignOptions) {
		opts.Expires = time.Duration(int64(expire.Seconds()) * int64(time.Second))
	})

	return req.URL, nil
}

// Delete purges the log stream from the S3 datastore.
func (s *Store) Delete(ctx context.Context, key string) error {
	keyWithPrefix := path.Join("/", s.prefix, key)
	_, err := s.service.DeleteObject(ctx, &s3.DeleteObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String(keyWithPrefix),
	})
	return err
}

// Ping pings the store for readiness
func (s *Store) Ping() error {
	// Check if the bucket exists
	_, err := s.service.HeadBucket(context.TODO(), &s3.HeadBucketInput{
		Bucket: aws.String(s.bucket),
	})
	if err != nil {
		return err
	}

	return nil
}
