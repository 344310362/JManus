# MCP协议API

<cite>
**本文档引用的文件**   
- [McpController.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/controller/McpController.java)
- [McpService.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/service/McpService.java)
- [IMcpService.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/service/IMcpService.java)
- [McpConfigRepository.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/repository/McpConfigRepository.java)
- [McpConfigValidator.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/service/McpConfigValidator.java)
- [McpCacheManager.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/service/McpCacheManager.java)
- [McpServerRequestVO.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/model/vo/McpServerRequestVO.java)
- [McpServersRequestVO.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/model/vo/McpServersRequestVO.java)
- [McpConfigVO.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/model/vo/McpConfigVO.java)
- [McpServerConfig.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/model/vo/McpServerConfig.java)
- [McpConfigEntity.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/model/po/McpConfigEntity.java)
- [McpConfigStatus.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/model/po/McpConfigStatus.java)
- [McpConfigType.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/model/po/McpConfigType.java)
- [McpProperties.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/config/McpProperties.java)
</cite>

## 目录
1. [简介](#简介)
2. [核心API端点](#核心api端点)
3. [MCP配置JSON结构](#mcp配置json结构)
4. [验证规则](#验证规则)
5. [认证方式](#认证方式)
6. [状态管理](#状态管理)
7. [缓存机制](#缓存机制)
8. [最佳实践](#最佳实践)
9. [集成指南](#集成指南)
10. [故障排除](#故障排除)

## 简介
MCP（Model Control Protocol）协议API提供了一套完整的接口，用于管理和控制MCP服务器的配置、状态和连接。该API允许用户通过RESTful接口执行MCP服务器的增删改查操作，支持批量导入和单个配置管理。MCP服务器可以是通过STUDIO、SSE或STREAMING等不同连接类型与系统集成的外部服务。

MCP协议的核心功能包括：
- 通过`list`端点获取所有MCP服务器配置
- 通过`batchImportMcpServers`端点批量导入MCP服务器配置
- 通过`saveMcpServer`端点添加或更新单个MCP服务器
- 通过`remove`和`removeByName`端点删除MCP服务器
- 通过`enableMcpServer`和`disableMcpServer`端点控制MCP服务器的启用状态

该API设计遵循RESTful原则，使用标准的HTTP方法和状态码，确保了接口的易用性和可预测性。

**本文档引用的文件**   
- [McpController.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/controller/McpController.java#L1-L196)

## 核心API端点

### list端点
`list`端点用于获取所有MCP服务器的配置信息。该端点返回一个包含所有MCP服务器配置的列表，每个配置都以VO（Value Object）的形式呈现。

**请求信息**
- **HTTP方法**: GET
- **路径**: `/api/mcp/list`
- **认证**: 需要有效的认证令牌
- **请求参数**: 无

**响应信息**
- **成功响应 (200)**: 返回包含所有MCP服务器配置的JSON数组
- **错误响应**: 标准HTTP错误码

**请求/响应示例**
```json
// 请求
GET /api/mcp/list

// 成功响应示例
[
  {
    "id": 1,
    "mcpServerName": "example-server",
    "connectionType": "SSE",
    "connectionConfig": "{\"url\":\"https://example.com/mcp\",\"headers\":{}}",
    "toolNames": ["tool1", "tool2"]
  }
]
```

**本文档引用的文件**   
- [McpController.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/controller/McpController.java#L54-L59)
- [McpService.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/service/McpService.java#L266-L268)
- [McpConfigRepository.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/repository/McpConfigRepository.java#L30-L31)

### batchImportMcpServers端点
`batchImportMcpServers`端点用于批量导入MCP服务器配置。该端点接受一个包含多个MCP服务器配置的JSON对象，一次性导入所有配置。

**请求信息**
- **HTTP方法**: POST
- **路径**: `/api/mcp/batch-import`
- **认证**: 需要有效的认证令牌
- **请求体**: `McpServersRequestVO`对象

**请求体结构**
```json
{
  "mcpServers": {
    "server1": {
      "url": "https://server1.example.com/mcp",
      "headers": {
        "Authorization": "Bearer token1"
      },
      "status": "ENABLE"
    },
    "server2": {
      "command": "npx",
      "args": ["mcp-server", "--port", "8080"],
      "env": {
        "NODE_ENV": "production"
      },
      "status": "DISABLE"
    }
  }
}
```

**响应信息**
- **成功响应 (200)**: 返回成功导入的服务器数量
- **错误响应**: 
  - 400 Bad Request: JSON格式无效或缺少必需字段
  - 500 Internal Server Error: 导入过程中发生错误

**请求/响应示例**
```json
// 请求
POST /api/mcp/batch-import
Content-Type: application/json

{
  "mcpServers": {
    "test-server": {
      "url": "https://test.example.com/mcp",
      "headers": {
        "Authorization": "Bearer test-token"
      }
    }
  }
}

// 成功响应
"Successfully imported 1 MCP servers"

// 错误响应
"Invalid JSON format"
```

**本文档引用的文件**   
- [McpController.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/controller/McpController.java#L65-L79)
- [McpService.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/service/McpService.java#L71-L141)
- [McpServersRequestVO.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/model/vo/McpServersRequestVO.java)

### saveMcpServer端点
`saveMcpServer`端点用于添加或更新单个MCP服务器配置。该端点根据请求中的ID字段判断是执行添加还是更新操作。

**请求信息**
- **HTTP方法**: POST
- **路径**: `/api/mcp/server`
- **认证**: 需要有效的认证令牌
- **请求体**: `McpServerRequestVO`对象

**请求体结构**
```json
{
  "id": 1,
  "mcpServerName": "new-server",
  "connectionType": "SSE",
  "url": "https://new-server.example.com/mcp",
  "headers": {
    "Authorization": "Bearer new-token"
  },
  "status": "ENABLE"
}
```

**响应信息**
- **成功响应 (200)**: 返回操作成功的消息
- **错误响应**:
  - 400 Bad Request: 验证失败或参数错误
  - 404 Not Found: 更新时指定的ID不存在
  - 409 Conflict: 服务器名称已存在（添加时）

**请求/响应示例**
```json
// 添加新服务器请求
POST /api/mcp/server
Content-Type: application/json

{
  "mcpServerName": "my-server",
  "connectionType": "SSE",
  "url": "https://my-server.example.com/mcp"
}

// 成功响应
"MCP server added successfully"

// 更新现有服务器请求
POST /api/mcp/server
Content-Type: application/json

{
  "id": 1,
  "mcpServerName": "updated-server",
  "connectionType": "SSE",
  "url": "https://updated-server.example.com/mcp"
}

// 成功响应
"MCP server updated successfully"
```

**本文档引用的文件**   
- [McpController.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/controller/McpController.java#L85-L122)
- [McpService.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/service/McpService.java#L149-L213)
- [McpServerRequestVO.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/model/vo/McpServerRequestVO.java)

### remove和removeByName端点
`remove`和`removeByName`端点用于删除MCP服务器配置。`remove`端点通过ID删除服务器，而`removeByName`端点通过名称删除服务器。

**请求信息**
- **HTTP方法**: GET (remove), POST (removeByName)
- **路径**: `/api/mcp/remove?id={id}`, `/api/mcp/remove/{name}`
- **认证**: 需要有效的认证令牌
- **请求参数**: ID或名称

**响应信息**
- **成功响应 (200)**: 返回"Success"
- **错误响应**: 标准HTTP错误码

**请求/响应示例**
```json
// 通过ID删除
GET /api/mcp/remove?id=1

// 通过名称删除
POST /api/mcp/remove/test-server

// 成功响应
"Success"
```

**本文档引用的文件**   
- [McpController.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/controller/McpController.java#L128-L141)
- [McpService.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/service/McpService.java#L219-L259)

### enableMcpServer和disableMcpServer端点
`enableMcpServer`和`disableMcpServer`端点用于控制MCP服务器的启用状态。这些端点通过ID来标识要操作的服务器。

**请求信息**
- **HTTP方法**: POST
- **路径**: `/api/mcp/enable/{id}`, `/api/mcp/disable/{id}`
- **认证**: 需要有效的认证令牌
- **请求参数**: 服务器ID

**响应信息**
- **成功响应 (200)**: 返回操作成功的消息
- **错误响应**:
  - 400 Bad Request: 操作失败
  - 404 Not Found: 指定ID的服务器不存在

**请求/响应示例**
```json
// 启用服务器
POST /api/mcp/enable/1

// 成功响应
"MCP server enabled successfully"

// 禁用服务器
POST /api/mcp/disable/1

// 成功响应
"MCP server disabled successfully"
```

**本文档引用的文件**   
- [McpController.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/controller/McpController.java#L147-L193)
- [McpService.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/service/McpService.java#L303-L348)

## MCP配置JSON结构
MCP配置的JSON结构定义了MCP服务器的各种属性和连接参数。以下是详细的配置结构说明。

### 核心字段
| 字段名 | 类型 | 必需 | 描述 |
|-------|------|------|------|
| mcpServerName | string | 是 | MCP服务器的唯一名称 |
| connectionType | string | 是 | 连接类型：STUDIO, SSE, STREAMING |
| status | string | 否 | 服务器状态：ENABLE, DISABLE |

### 连接类型特定字段
根据`connectionType`的不同，需要提供相应的连接参数：

**STUDIO类型**
- `command`: 启动MCP服务器的命令（如npx, python等）
- `args`: 命令行参数数组
- `env`: 环境变量字典
- `headers`: HTTP请求头字典

**SSE/STREAMING类型**
- `url`: MCP服务器的URL地址
- `headers`: HTTP请求头字典

### 完整配置示例
```json
{
  "mcpServers": {
    "python-mcp": {
      "command": "python",
      "args": ["-m", "my_mcp_server", "--port", "8080"],
      "env": {
        "PYTHONPATH": "/path/to/mcp",
        "LOG_LEVEL": "INFO"
      },
      "headers": {
        "Authorization": "Bearer my-token",
        "Content-Type": "application/json"
      },
      "status": "ENABLE"
    },
    "node-mcp": {
      "url": "https://node-mcp.example.com/mcp",
      "headers": {
        "Authorization": "Bearer node-token"
      },
      "status": "DISABLE"
    }
  }
}
```

**本文档引用的文件**   
- [McpServerConfig.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/model/vo/McpServerConfig.java)
- [McpServerRequestVO.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/model/vo/McpServerRequestVO.java)

## 验证规则
MCP配置在保存前会经过严格的验证，确保配置的正确性和安全性。

### 通用验证规则
- **服务器名称**: 必须非空且唯一
- **连接类型**: 必须为有效的类型（STUDIO, SSE, STREAMING）
- **状态**: 如果提供，必须为有效的状态值（ENABLE, DISABLE）

### STUDIO类型验证
- `command`字段必须包含有效的可执行命令
- 不允许在`command`字段中包含参数，参数应放在`args`数组中
- 支持的常见命令包括：npx, uvx, npm, yarn, python, python3, node, nodejs等

### SSE/STREAMING类型验证
- `url`字段必须为有效的URL
- 协议必须为HTTP或HTTPS
- 主机名必须能够通过DNS解析
- 对于SSE连接，URL路径必须包含"sse"

### 验证流程
1. 检查基本字段的完整性和格式
2. 根据连接类型验证特定字段
3. 检查服务器名称的唯一性
4. 验证URL的可达性（DNS解析）
5. 确保环境变量和请求头的格式正确

**本文档引用的文件**   
- [McpConfigValidator.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/service/McpConfigValidator.java)
- [McpServerRequestVO.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/model/vo/McpServerRequestVO.java#L171-L181)

## 认证方式
MCP协议API使用标准的HTTP认证机制来保护API端点。

### 认证机制
- **Bearer Token**: API使用Bearer Token进行认证，客户端需要在请求头中包含`Authorization`字段
- **Token获取**: 通过系统的登录接口获取认证令牌

### 请求头示例
```http
Authorization: Bearer your-access-token-here
Content-Type: application/json
```

### 认证流程
1. 客户端通过登录接口获取认证令牌
2. 在每个API请求的`Authorization`头中包含该令牌
3. 服务器验证令牌的有效性
4. 如果令牌有效，处理请求；否则返回401 Unauthorized

**本文档引用的文件**   
- [McpController.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/controller/McpController.java)

## 状态管理
MCP服务器的状态管理是通过`status`字段和专门的启用/禁用端点实现的。

### 状态值
- **ENABLE**: 服务器处于启用状态，可以正常连接和使用
- **DISABLE**: 服务器处于禁用状态，不会尝试连接

### 状态转换
```mermaid
stateDiagram-v2
[*] --> ENABLE
ENABLE --> DISABLE : disableMcpServer
DISABLE --> ENABLE : enableMcpServer
ENABLE --> ENABLE : 状态已启用
DISABLE --> DISABLE : 状态已禁用
```

**本文档引用的文件**   
- [McpConfigStatus.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/model/po/McpConfigStatus.java)
- [McpService.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/service/McpService.java#L303-L348)

## 缓存机制
MCP系统采用双缓冲缓存机制来管理MCP服务器的连接，确保在配置更新时服务的连续性。

### 双缓冲缓存工作原理
```mermaid
graph TD
A[主动缓存] --> |提供服务| B[客户端请求]
C[后台缓存] --> |准备新配置| D[配置更新]
D --> |构建完成| C
E[缓存切换] --> |原子操作| F[关闭旧连接]
E --> |切换| A
E --> |切换| C
F --> |释放资源| G[旧客户端连接]
```

**本文档引用的文件**   
- [McpCacheManager.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/service/McpCacheManager.java)
- [McpService.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/service/McpService.java#L139-L140)

## 最佳实践
### 配置管理
- **命名规范**: 使用有意义且唯一的服务器名称，便于识别和管理
- **环境隔离**: 为不同环境（开发、测试、生产）使用不同的配置
- **版本控制**: 将MCP配置文件纳入版本控制系统

### 安全性
- **敏感信息**: 不要在配置中直接包含敏感信息，使用环境变量或密钥管理系统
- **访问控制**: 限制API的访问权限，只允许授权用户操作
- **定期审计**: 定期审查MCP服务器的配置和状态

### 性能优化
- **连接池**: 对于频繁使用的MCP服务器，考虑使用连接池
- **超时设置**: 合理设置连接和读取超时，避免长时间等待
- **监控**: 实施监控和告警，及时发现和解决连接问题

## 集成指南
### 配置导入
1. 准备JSON格式的MCP服务器配置
2. 使用`batchImportMcpServers`端点批量导入，或使用`saveMcpServer`端点逐个添加
3. 验证导入结果，确保所有服务器都正确配置

### 状态同步
- 使用`list`端点定期获取最新的MCP服务器状态
- 监听状态变化事件，及时响应服务器的启用/禁用操作
- 实现健康检查机制，确保MCP服务器的可用性

### 故障排除
#### 常见问题及解决方案
| 问题 | 可能原因 | 解决方案 |
|------|---------|---------|
| 400 Bad Request | JSON格式错误或缺少必需字段 | 检查请求体格式，确保所有必需字段都存在 |
| 404 Not Found | 服务器ID不存在 | 检查ID是否正确，或先使用`list`端点确认服务器存在 |
| 500 Internal Server Error | 服务器内部错误 | 检查服务器日志，联系技术支持 |
| DNS解析失败 | 主机名无法解析 | 检查网络连接和DNS配置，确认主机名正确 |
| 连接超时 | 服务器不可达 | 检查服务器状态和网络连接，调整超时设置 |

#### 调试技巧
- 使用`list`端点验证配置是否正确保存
- 检查服务器日志获取详细的错误信息
- 逐步测试配置，先测试单个服务器再批量导入
- 使用标准的JSON验证工具检查配置文件的格式

**本文档引用的文件**   
- [McpController.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/controller/McpController.java)
- [McpService.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/service/McpService.java)
- [McpConfigValidator.java](file://src/main/java/com/alibaba/cloud/ai/lynxe/mcp/service/McpConfigValidator.java)