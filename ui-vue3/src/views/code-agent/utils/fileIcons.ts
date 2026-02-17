const iconMap: Record<string, string> = {
  tsx: 'vscode-icons:file-type-reactts',
  ts: 'vscode-icons:file-type-typescript',
  jsx: 'vscode-icons:file-type-reactjs',
  js: 'vscode-icons:file-type-js',
  vue: 'vscode-icons:file-type-vue',
  css: 'vscode-icons:file-type-css',
  html: 'vscode-icons:file-type-html',
  json: 'vscode-icons:file-type-json',
  md: 'vscode-icons:file-type-markdown',
  svg: 'vscode-icons:file-type-svg',
  png: 'vscode-icons:file-type-image',
  jpg: 'vscode-icons:file-type-image',
  gif: 'vscode-icons:file-type-image',
  yaml: 'vscode-icons:file-type-yaml',
  yml: 'vscode-icons:file-type-yaml',
  xml: 'vscode-icons:file-type-xml',
  py: 'vscode-icons:file-type-python',
  java: 'vscode-icons:file-type-java',
  go: 'vscode-icons:file-type-go',
  rs: 'vscode-icons:file-type-rust',
  sh: 'vscode-icons:file-type-shell',
  scss: 'vscode-icons:file-type-scss',
  less: 'vscode-icons:file-type-less',
}

const langMap: Record<string, string> = {
  tsx: 'typescript',
  ts: 'typescript',
  jsx: 'javascript',
  js: 'javascript',
  vue: 'html',
  css: 'css',
  scss: 'scss',
  less: 'less',
  html: 'html',
  json: 'json',
  md: 'markdown',
  xml: 'xml',
  yaml: 'yaml',
  yml: 'yaml',
  py: 'python',
  java: 'java',
  go: 'go',
  rs: 'rust',
  sh: 'shell',
}

function getExt(path: string): string {
  return path.split('.').pop()?.toLowerCase() || ''
}

export function getFileIcon(path: string): string {
  return iconMap[getExt(path)] || 'carbon:document'
}

export function getLanguage(path: string): string {
  return langMap[getExt(path)] || 'plaintext'
}

export function getFileName(path: string): string {
  return path.split('/').pop() || path
}
