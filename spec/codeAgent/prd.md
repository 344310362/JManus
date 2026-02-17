一、 产品需求文档 (PRD)
1. 产品定位
基于当前的 manus 框架，独立出一个专门用于 web 网页生成的智能体（后端复用，前端独立模块），打造一个低门槛、零部署的生成式 Web 开发平台。用户通过自然语言描述需求，系统自动生成代码、安装依赖、并在浏览器内实时渲染预览。

2. 核心功能
多模态对话： 支持文字、图片上传（设计稿转代码）。

流式代码编辑器： 支持 AI 实时写入代码，并允许手动二次编辑。

实时沙盒预览： 自动执行依赖安装与环境启动，预览窗支持热更新。

版本管理： 记录每次生成的状态，支持一键回滚。

一键部署： 支持将生成的站点直接发布到 Vercel 或 Netlify。

3. 用户流程
输入： 用户输入“创建一个带暗黑模式的护肤品展示页”。

规划： AI 拆解任务（安装 lucide-react、编写 Navbar、Hero 组件）。

生成： 编辑器显示代码流式输出。

预览： 右侧预览框显示“正在启动服务...”，随后渲染出页面。

二、 技术架构文档 (TDD)
实现这一需求，核心在于解决 “AI 意图到运行态” 的映射。

1. 总体架构图
2. 技术栈选型
前端： Next.js + Tailwind CSS + Lucide Icons。

编辑器： Monaco Editor (VS Code 同款)

沙盒环境： StackBlitz WebContainer API (核心)。

Agent: 延用已有的 Agent。


3. 核心模块设计
A. AI 指令处理层 (Agent Engine)
系统需要将用户需求封装进一个复杂的 System Prompt。

Prompt 策略：

强制输出结构化 JSON 或特定标记（如 <file path="src/App.tsx">...</file>）。

内置主流技术规范（如：始终使用 Tailwind、优先使用现有组件库）。

B. 虚拟运行时 (The Runtime)
使用 WebContainer 在浏览器主线程外开启一个 Worker。

虚拟文件系统： 将 AI 生成的代码通过 webcontainerInstance.fs.writeFile 实时写入内存。

依赖预加载： 为了提速，系统会维护一个常用依赖的 Binary Cache。当 AI 修改 package.json 时，WebContainer 内部执行 pnpm install 会优先从缓存中拉取。

C. 同步与渲染逻辑
File Watcher： 监控虚拟文件系统变动。

热更新 (HMR)： 启动一个内部 Vite Server，通过 iframe 的 URL 代理（hostname.stackblitz.io）实现预览。

三、 关键代码实现思路 (伪代码)
1. 初始化 WebContainer
JavaScript
import { WebContainer } from '@webcontainer/api';

const webcontainerInstance = await WebContainer.boot();
await webcontainerInstance.mount(files); // 加载初始项目结构

// 启动安装
const installProcess = await webcontainerInstance.spawn('pnpm', ['install']);
installProcess.output.pipeTo(new WritableStream({
  write(data) { console.log(data); } // 将日志流回 UI
}));
2. 启动开发服务器
JavaScript
const devProcess = await webcontainerInstance.spawn('pnpm', ['run', 'dev']);
webcontainerInstance.on('server-ready', (port, url) => {
  iframeEl.src = url; // 将预览框指向虚拟服务器地址
});
