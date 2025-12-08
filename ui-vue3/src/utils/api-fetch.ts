/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { userStore } from "@/stores/user"

// Unified fetch method with base URL from environment variable
const BASE_URL = import.meta.env.VITE_BASE_URL || '';

export async function apiFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  // Convert input to string for URL manipulation
  const url = input.toString();

  // Prepend base URL if it's a relative path and doesn't already start with http
  const fullUrl = url.startsWith('http') ? url : `${BASE_URL}${url.startsWith('/') ? url : `/${url}`}`;

  // 获取 userStore 实例（注意：必须在组件 setup 或已初始化 Pinia 的上下文中调用）
  const token = userStore.token;
  const username = userStore.user.username;
  const headers = new Headers(init?.headers || {});
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
    headers.set('USERNAME', `${username}`);
  }

  // 构造新的 init 配置
  const finalInit: RequestInit = {
    ...init,
    headers,
  };

  return fetch(fullUrl, finalInit);
}
