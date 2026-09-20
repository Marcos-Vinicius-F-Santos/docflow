FROM quay.io/minio/mc:latest@sha256:a7fe349ef4bd8521fb8497f55c6042871b2ae640607cf99d9bede5e9bdf11727 AS minio-client

FROM alpine:3.20@sha256:d9e853e87e55526f6b2917df91a2115c36dd7c696a35be12163d44e6e2a4b6bc

RUN apk add --no-cache curl postgresql-client

COPY --from=minio-client /usr/bin/mc /usr/local/bin/mc
COPY app-user-policy.json /usr/local/share/docflow/app-user-policy.json
COPY bootstrap.sh /usr/local/bin/docflow-bootstrap
RUN chmod 0755 /usr/local/bin/docflow-bootstrap

ENTRYPOINT ["/usr/local/bin/docflow-bootstrap"]
