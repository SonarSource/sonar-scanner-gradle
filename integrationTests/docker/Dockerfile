FROM gcr.io/ci-cd-215716/jdk8:latest

USER root

RUN apt-get update && apt-get -y install xvfb

# Back to the user of the base image
USER sonarsource

