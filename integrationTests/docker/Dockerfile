# JDK-8 is used because Gradle 4.6 used by the wrapper is not compatible with JDK 11
FROM us.gcr.io/sonarqube-team/base:j8-latest

USER root

RUN apt-get update && apt-get -y install xvfb

ENV SDK_TOOLS=sdk-tools-linux-4333796.zip

#Installing android sdk
RUN echo "Installing android-sdk" \
  && mkdir --parent /opt/android-sdk-linux/{add-ons,platforms,platform-tools,temp} \
  && cd /opt/android-sdk-linux \
  && curl --remote-name https://dl.google.com/android/repository/$SDK_TOOLS \
  && unzip $SDK_TOOLS \
  && rm $SDK_TOOLS

ENV ANDROID_HOME=/opt/android-sdk-linux  PATH=$PATH:/opt/android-sdk-linux/tools:/opt/android-sdk-linux/platform-tools:/opt/android-sdk-linux/tools/bin
RUN sed '/^CLASSPATH=/a CLASSPATH=/usr/share/java/jaxb-api.jar:/usr/share/java/jaxb-impl.jar:/usr/share/java/jaxb-core.jar:/usr/share/java/jaxb-jxc.jar:/usr/share/java/jaxb-xjc.jar:/usr/share/java/javax.activation.jar:"$CLASSPATH"' -i /opt/android-sdk-linux/tools/bin/sdkmanager /opt/android-sdk-linux/tools/bin/avdmanager \
  && yes |sdkmanager "platform-tools" "extras;android;m2repository" \
  && yes |sdkmanager --licenses

RUN chmod -R 755 $ANDROID_HOME \
  && chown -R sonarsource: $ANDROID_HOME

# Back to the user of the base image
USER sonarsource