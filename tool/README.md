# SENG4430_Project_Group5

```
              ____----------- _____
\~~~~~~~~~~/~_--~~~------~~~~~     \
 `---`\  _-~      |                   \
   _-~  <_         |                     \[]
 / ___     ~~--[""] |      ________-------'_
> /~` \    |-.   `\~~.~~~~~                _ ~ - _
 ~|  ||\%  |       |    ~  ._                ~ _   ~ ._
   `_//|_%  \      |          ~  .              ~-_   /\
          `--__     |    _-____  /\               ~-_ \/.
               ~--_ /  ,/ -~-_ \ \/          _______---~/
                   ~~-/._<   \ \`~~~~~~~~~~~~~     ##--~/
                         \    ) |`------##---~~~~-~  ) )
                          ~-_/_/                  ~~ ~~
```

# Family

# Build Tool

The following expects you have `cd`'d into the `/tool` directory.

To generate the `.jar` use:

`mvn clean package`

This generates the runnable fat jar:

`target/quality-auditor-tool-1.0.0-jar-with-dependencies.jar`

## Reliability audit

```
java -jar target/quality-auditor-tool-1.0.0-jar-with-dependencies.jar \
  --project <name> \
  --source <path-to-java-source-root> \
  --spotbugs-report <path-to-spotbugsXml.xml>
```

## Security audit (isolated)

```
java -jar target/quality-auditor-tool-1.0.0-jar-with-dependencies.jar security \
  --project <name> \
  --dependency-report <path-to-dependency-check-report.json>
```

Note: if you don't want to install maven just use the VSCode Maven extension and use that instead.

https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html
