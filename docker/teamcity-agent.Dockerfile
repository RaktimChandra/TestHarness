# TeamCity agent able to run this project's builds (teamcity.yml): JDK 21, Docker CLI (the
# host's daemon is mounted), Google Chrome for the UI and framework tests.
FROM jetbrains/teamcity-agent:2025.07-linux-sudo
USER root
RUN apt-get update \
 && apt-get install -y --no-install-recommends openjdk-21-jdk-headless wget ca-certificates \
 && wget -q -O /tmp/chrome.deb https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
 && apt-get install -y --no-install-recommends /tmp/chrome.deb \
 && rm -rf /tmp/chrome.deb /var/lib/apt/lists/*
# The DSL requires env.JDK_21_0 and uses it as JAVA_HOME for the Maven Wrapper.
ENV JDK_21_0=/usr/lib/jvm/java-21-openjdk-amd64
USER buildagent
