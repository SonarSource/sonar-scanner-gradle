#FROM gradle:5.6.1-jdk8
#FROM gradle:4.6-jdk8
#FROM maven:3.6-jdk-8
FROM gcr.io/ci-cd-215716/base:latest

USER root

RUN apt-get update && apt-get -y install xvfb


# Back to the user of the base image
USER gradle

