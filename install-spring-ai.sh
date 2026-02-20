#!/bin/bash

# Script to install Spring AI 2.0.0-M2 JARs to local Maven repository
# This resolves the RetryTemplate class loading issue

SPRING_AI_DIR="/Users/nkadiri/Downloads/spring-ai-2.0.0-M2"
VERSION="2.0.0-M2"

echo "Installing Spring AI $VERSION JARs to Maven local repository..."

# Check if directory exists
if [ ! -d "$SPRING_AI_DIR" ]; then
    echo "Error: Directory $SPRING_AI_DIR not found"
    echo "Please ensure the Spring AI JARs are in this directory"
    exit 1
fi

# Install each JAR file found
cd "$SPRING_AI_DIR"
for jar in *.jar; do
    if [ -f "$jar" ]; then
        # Extract artifact name from JAR filename (remove version and .jar)
        ARTIFACT=$(echo "$jar" | sed "s/-$VERSION.jar//")

        echo "Installing $jar as org.springframework.ai:$ARTIFACT:$VERSION"

        mvn install:install-file \
            -Dfile="$jar" \
            -DgroupId=org.springframework.ai \
            -DartifactId="$ARTIFACT" \
            -Dversion="$VERSION" \
            -Dpackaging=jar \
            -DgeneratePom=true
    fi
done

echo "Installation complete!"
echo "You can now run: cd /Users/nkadiri/Documents/project/Java/chat-demo && ./gradlew clean bootRun"
