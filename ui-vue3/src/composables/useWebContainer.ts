import { ref, shallowRef } from 'vue'
import { WebContainer } from '@webcontainer/api'

export type WebContainerStatus = 'idle' | 'booting' | 'ready' | 'installing' | 'starting' | 'running' | 'error'

// npm 镜像源配置（国内访问 npm 官方源极慢，使用 npmmirror）
const NPM_REGISTRY = 'https://registry.npmmirror.com'
// 安装超时时间（毫秒）
const INSTALL_TIMEOUT_MS = 120_000

export function useWebContainer() {
  const status = ref<WebContainerStatus>('idle')
  const error = ref('')
  const previewUrl = ref('')
  const logs = ref<string[]>([])

  const instance = shallowRef<WebContainer | null>(null)
  let serverProcess: any = null

  function appendLog(line: string) {
    logs.value.push(line)
    if (logs.value.length > 500) {
      logs.value = logs.value.slice(-400)
    }
  }

  async function boot() {
    if (instance.value) return
    try {
      status.value = 'booting'
      error.value = ''
      appendLog('[sandbox] Booting WebContainer...')
      instance.value = await WebContainer.boot()
      status.value = 'ready'
      appendLog('[sandbox] Ready')
    } catch (e: any) {
      status.value = 'error'
      error.value = e.message || 'Failed to boot WebContainer'
      appendLog(`[sandbox] Boot error: ${error.value}`)
    }
  }

  async function writeFiles(files: Record<string, string>) {
    if (!instance.value) return
    for (const [path, content] of Object.entries(files)) {
      const parts = path.split('/')
      if (parts.length > 1) {
        const dir = parts.slice(0, -1).join('/')
        await instance.value.fs.mkdir(dir, { recursive: true })
      }
      await instance.value.fs.writeFile(path, content)
    }
    appendLog(`[sandbox] Wrote ${Object.keys(files).length} file(s)`)
  }

  /** 写入 .npmrc 配置镜像源 */
  async function configureNpmRegistry() {
    if (!instance.value) return
    await instance.value.fs.writeFile('.npmrc', `registry=${NPM_REGISTRY}\n`)
    appendLog(`[install] Using npm registry: ${NPM_REGISTRY}`)
  }

  async function installAndStart() {
    if (!instance.value) return

    try {
      status.value = 'installing'
      const t0 = performance.now()

      // 配置国内镜像源
      await configureNpmRegistry()

      appendLog('[install] Running npm install...')

      const proc = await instance.value.spawn('npm', [
        'install',
        '--legacy-peer-deps',
        `--registry=${NPM_REGISTRY}`,
      ])
      const output: string[] = []
      proc.output.pipeTo(new WritableStream({
        write(chunk) {
          appendLog(chunk)
          output.push(chunk)
        }
      }))

      // 带超时的等待安装完成
      const exitCode = await Promise.race([
        proc.exit,
        new Promise<never>((_, reject) =>
          setTimeout(() => {
            proc.kill()
            reject(new Error(`npm install timed out after ${INSTALL_TIMEOUT_MS / 1000}s`))
          }, INSTALL_TIMEOUT_MS)
        ),
      ])

      const elapsed = Math.round(performance.now() - t0)

      if (exitCode !== 0) {
        appendLog(`[install] Failed (exit ${exitCode}, ${elapsed}ms)`)
        throw new Error(`Dependency installation failed (exit code ${exitCode})`)
      }
      appendLog(`[install] Done in ${elapsed}ms`)

      // 启动 dev server
      status.value = 'starting'
      appendLog('[dev] Starting vite...')
      serverProcess = await instance.value.spawn('npx', ['vite', '--host'])

      serverProcess.output.pipeTo(new WritableStream({
        write(chunk) { appendLog(chunk) }
      }))

      instance.value.on('server-ready', (_port: number, url: string) => {
        previewUrl.value = url
        status.value = 'running'
        appendLog(`[dev] Server ready at ${url}`)
      })
    } catch (e: any) {
      status.value = 'error'
      error.value = e.message || 'Failed to start dev server'
      appendLog(`[dev] Error: ${error.value}`)
    }
  }

  async function teardown() {
    if (serverProcess) {
      serverProcess.kill()
      serverProcess = null
    }
    if (instance.value) {
      instance.value.teardown()
      instance.value = null
    }
    status.value = 'idle'
    previewUrl.value = ''
    error.value = ''
    logs.value = []
  }

  return {
    status,
    error,
    previewUrl,
    logs,
    instance,
    boot,
    writeFiles,
    installAndStart,
    teardown,
  }
}
