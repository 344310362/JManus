```groovy
pnpm build:dev

mvn clean package  "-Dmaven.test.failure.ignore=true" "-Dspring-javaformat.skip=true"

docker build -t registry.cn-beijing.aliyuncs.com/exe/sm-ai-manus:dev-rj-2 -f .\Dockerfile-backend .

docker push registry.cn-beijing.aliyuncs.com/exe/sm-ai-manus:dev-rj-2
```