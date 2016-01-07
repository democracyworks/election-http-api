FROM quay.io/democracyworks/didor:latest

RUN mkdir -p /usr/src/election-http-api
WORKDIR /usr/src/election-http-api

COPY project.clj /usr/src/election-http-api/

RUN lein deps

COPY . /usr/src/election-http-api

RUN lein test
RUN lein immutant war --name election-http-api --destination target --nrepl-port=14567 --nrepl-start --nrepl-host=0.0.0.0
