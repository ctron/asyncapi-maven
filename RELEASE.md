# Performing a release

This is a short note on how to perform a release.

## Build

    mvn release:prepare release:perform

## Update the documentation

    git checkout <tag>
    # update site.xml if necessary
    mvn site -Ppages
