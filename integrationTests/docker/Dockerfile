FROM gcr.io/ci-cd-215716/jdk8:latest

USER root

RUN apt-get update && apt-get -y install xvfb


ENV SDK_TOOLS=sdk-tools-linux-4333796.zip

# Installing openjdk 11
#RUN apt-get -y update && apt-get install --no-install-recommends -y wget unzip openjdk-11-jdk-headless libjaxb-java && rm /var/lib/apt/lists/* /var/cache/apt/* -rf

#Installing android sdk
RUN echo "Installing android-sdk" \
  && cd /opt && mkdir android-sdk-linux && cd android-sdk-linux && mkdir add-ons platforms platform-tools temp \
  && cd /opt/android-sdk-linux && wget https://dl.google.com/android/repository/$SDK_TOOLS && unzip $SDK_TOOLS && rm $SDK_TOOLS \
  && ANDROID_HOME=/opt/android-sdk-linux  PATH=$PATH:/opt/android-sdk-linux/tools:/opt/android-sdk-linux/platform-tools:/opt/android-sdk-linux/tools/bin \
  && sed '/^CLASSPATH=/a CLASSPATH=/usr/share/java/jaxb-api.jar:/usr/share/java/jaxb-impl.jar:/usr/share/java/jaxb-core.jar:/usr/share/java/jaxb-jxc.jar:/usr/share/java/jaxb-xjc.jar:/usr/share/java/javax.activation.jar:"$CLASSPATH"' -i /opt/android-sdk-linux/tools/bin/sdkmanager /opt/android-sdk-linux/tools/bin/avdmanager \
  && yes |sdkmanager "platform-tools" "extras;android;m2repository" \
  && chmod 755 /opt/android-sdk-linux

# Back to the user of the base image
USER sonarsource

