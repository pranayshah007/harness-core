FROM harness/ubi-base-image:8.8_gcr

USER root

RUN ./setup.sh

RUN microdnf module enable -y nginx:1.20
RUN microdnf install nginx -y

COPY nginx.conf /etc/nginx/nginx.conf

COPY storage/harness-download /www/storage/harness-download
COPY storage/wingsdelegates /www/storage/wingsdelegates
COPY storage/wingswatchers /www/storage/wingswatchers

RUN chown 65534:65534 -R /var/log/nginx
RUN chown 65534:65534 /etc/nginx/nginx.conf

USER 65534

CMD nginx -c "/etc/nginx/nginx.conf" -g "daemon off;"