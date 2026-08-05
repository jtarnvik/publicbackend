# GENERATED FILE — do not edit build.just directly.
#
# The maven-antrun-plugin (execution `generate-justfile`, bound to process-resources) copies
# build.just.template to build.just with the project version substituted. Edit the template,
# then run `mvn process-resources`.
#
# build.just is committed on purpose: the deploy machine checks out a tag and runs `just build`
# immediately, so the jar name for that exact version has to be present in the tag.

jar_version := "1.2.0"
jar_name := "publicbackend-" + jar_version + ".jar"

# Clean, build and assemble release/
build_release: clean mvn_build release

# Compile, test and run checkstyle (checkstyle is bound to the verify phase in pom.xml)
mvn_build:
  mvn verify

# Collect the artifacts the deployment needs into release/
release:
  mkdir -p release
  cp target/{{jar_name}} release/publicbackend.jar
  echo "Version    : {{jar_version}}" > release/Version-{{jar_version}}.txt
  echo "Build Date : $(date)" >> release/Version-{{jar_version}}.txt
  echo "Git Commit : $(git rev-parse --short HEAD || echo \"N/A\")" >> release/Version-{{jar_version}}.txt

clean:
  rm -rf release
  mvn clean
