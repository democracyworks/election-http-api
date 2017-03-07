FROM quay.io/democracyworks/clojure-yourkit:lein-2.7.1

RUN mkdir -p /usr/src/election-http-api
WORKDIR /usr/src/election-http-api

COPY project.clj /usr/src/election-http-api/

ARG env=production

RUN lein with-profile $env deps

COPY . /usr/src/election-http-api

RUN lein with-profiles $env,test test
RUN lein with-profile $env uberjar

CMD java ${JVM_OPTS:--XX:+UseG1GC} \
    -javaagent:resources/jars/com.newrelic.agent.java/newrelic-agent.jar \
    $YOURKIT_AGENT_OPTION \
    -jar target/election-http-api.jar
