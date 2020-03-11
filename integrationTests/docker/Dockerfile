#------------------------------------------------------------------------------
# All the tools required to build sonar-enterprise, including execution
# of integration tests.
#
# Build from the basedir:
#   docker build -f private/docker/Dockerfile-build -t sonar-enterprise-build .
#
# Verify the content of the image by running a shell session in it:
#   docker run -it sonar-enterprise-build bash
#
# CirrusCI builds the image when needed. No need to manually upload it
# to Google Cloud Container Registry. See section "gke_container" of .cirrus.yml
#------------------------------------------------------------------------------

FROM gradle:5.6.1-jdk11

USER root

# Avoiding JVM Delays Caused by Random Number Generation (https://docs.oracle.com/cd/E13209_01/wlcp/wlss30/configwlss/jvmrand.html)
RUN sed -i 's|securerandom.source=file:/dev/random|securerandom.source=file:/dev/urandom|g' $JAVA_HOME/conf/security/java.security

RUN apt-get update && apt-get -y install xvfb


# Back to the user of the base image
USER gradle

