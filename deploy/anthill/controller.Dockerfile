# Anthill game-controller + a Java 17 JRE so it can spawn the (Java) Brain/Out
# dedicated server as a child process.
#
# The stock anthillplatform/anthill-game-controller image is Debian buster
# (EOL, max openjdk-11 via apt), but the server is built for Java 17. So instead
# of apt we copy a pinned Temurin 17 JRE — the same runtime bin/server uses on
# Linux, so the libGDX desktop natives the headless server loads are already
# known-good there.
#
# Build:  docker build -f controller.Dockerfile -t brainout/anthill-game-controller:jre17 .
# Used by docker-compose.override.yml in the anthill-dev/dev stack.

FROM eclipse-temurin:17-jre AS jre

FROM anthillplatform/anthill-game-controller:latest

COPY --from=jre /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="/opt/java/openjdk/bin:${PATH}"

# The controller mounts /tmp as a noexec tmpfs, but the online server loads a
# native JZMQ .so from java.io.tmpdir. Provide an exec-able tmp dir on the image
# layer; the deployment's run.sh points -Djava.io.tmpdir here.
RUN mkdir -p /opt/brainout-tmp && chmod 1777 /opt/brainout-tmp

# sanity: fail the build if java isn't runnable
RUN java -version
