// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

// Package secret loops through all the environment variables of a given struct.
// If a variable is tagged with "secret: true", we use the secret manager
// to perform the resolution if the secret manager is enabled.
package secret

import (
	"context"
	"errors"
	"fmt"
	"reflect"

	secretmanager "cloud.google.com/go/secretmanager/apiv1"
	"cloud.google.com/go/secretmanager/apiv1/secretmanagerpb"
	"github.com/sirupsen/logrus"
	"google.golang.org/api/option"
)

// Resolve looks through all fields of any given struct. We try to resolve fields
// annotated with "secret: true". If secret manager is enabled, the resolution
// happens via secret manager (the value becomes the key that we look for). If
// secret manager is not enabled, thd value is returned as is.
// If the field "version" is set, it reads that specific version for the secret
// manager.
func Resolve(ctx context.Context, enableSecretManager bool,
	gcpProject, gcpCredsPath string, s interface{}) error {
	// Resolution is needed only if secret manager is enabled
	if !enableSecretManager {
		return nil
	}

	// Check that GCP project and path to GCP creds are set
	if gcpProject == "" {
		return errors.New("missing GCP project info")
	}
	if gcpCredsPath == "" {
		return errors.New("missing GCP creds info")
	}
	c, err := secretmanager.NewClient(ctx, option.WithCredentialsFile(gcpCredsPath))
	if err != nil {
		return err
	}
	defer c.Close()

	v := reflect.Indirect(reflect.ValueOf(s))

	err = ResolveSecrets(v, gcpProject, c)
	if err != nil {
		return err
	}
	return nil
}

// ResolveSecrets iterates through all elements in the struct and tries to use the secret manager
// wherever the annotation "secret: true" is present. If the secret does not get resolved, it
// returns an error.
func ResolveSecrets(v reflect.Value, gcpProject string, c *secretmanager.Client) error {
	for i := 0; i < v.NumField(); i++ {
		field := v.Field(i)

		if field.Kind() == reflect.Struct {

			if field.CanInterface() {
				st := reflect.TypeOf(field.Interface())

				for i := 0; i < field.NumField(); i++ {
					field := field.Field(i)
					st := st.Field(i)

					if st.Tag.Get("secret") != "true" {
						continue
					}

					if field.Kind().String() != "string" {
						return fmt.Errorf("%s is of %s type not string. cannot use secret resolution",
							st.Name, field.Kind().String())
					}
					version := st.Tag.Get("version")
					if version == "" {
						version = "latest"
					}
					value := field.String()
					req := &secretmanagerpb.AccessSecretVersionRequest{
						Name: fmt.Sprintf("projects/%s/secrets/%s/versions/%s", gcpProject, value, version),
					}
					result, err := c.AccessSecretVersion(context.Background(), req)
					if err != nil {
						return err
					}
					logrus.Infoln("successfully resolved secret: ", st.Name)
					field.SetString(string(result.GetPayload().GetData()))

				}
			}
		}

	}
	return nil
}
