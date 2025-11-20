```groovy
docker build -t registry.cn-beijing.aliyuncs.com/exe/sm-ai-manus:base-rj-1 -f .\Dockerfile.multiarch .

pnpm build:dev

mvn clean package  "-Dmaven.test.failure.ignore=true" "-Dspring-javaformat.skip=true"

docker build -t registry.cn-beijing.aliyuncs.com/exe/sm-ai-manus:prod-rj-6 -f .\Dockerfile-backend .

docker push registry.cn-beijing.aliyuncs.com/exe/sm-ai-manus:dev-rj-2
```

版本信息

1.3.X
```
registry.cn-beijing.aliyuncs.com/exe/sm-ai-manus:dev-rj-2
```

1.4.X
```
registry.cn-beijing.aliyuncs.com/exe/sm-ai-manus:dev-rj-4
```
