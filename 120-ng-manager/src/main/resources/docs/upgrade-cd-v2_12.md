# You will need to provide a path to your service manifests (i.e. kubernetes manifests)

      manifests:
        - manifest:
            identifier: nginx
            type: K8sManifest
            spec:
              store:
                type: Github
                spec:
                  connectorRef: ProductManagementRohan
                  gitFetchType: Branch
                  paths:
                    - traffic-shifting-nginx/backend/deployment.yaml
                    - traffic-shifting-nginx/backend/service.yaml
                    - traffic-shifting-nginx/backend/nginx.yaml
                    - traffic-shifting-nginx/frontend/ui.yaml
                  repoName: Product-Management
                  branch: main
              valuesPaths:
                - traffic-shifting-nginx/values.yaml
              skipResourceVersioning: false
              