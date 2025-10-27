/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { reactive } from "vue";

export interface UserEmits {
  (e: 'user-changed'): void
}

interface UserVO {
  id: number
  avatar: string
  nickname: string
  deptId: number
  username: string
}

interface UserInfoVO {
  permissions: Set<string>
  roles: string[]
  isSetUser: boolean
  user: UserVO,
  token: string
}

export class UserStore implements UserInfoVO {
  permissions: Set<string> = new Set<string>()
  roles: string[] = []
  isSetUser: boolean = false
  user: UserVO = {
    id: 0,
    avatar: '',
    nickname: '',
    deptId: 0,
    username: ''
  }
  token: string = ''

  // 设置用户完整信息

  updateAccessToken(accessToken: string) {
    this.token = accessToken
  }
  setUserInfo(userInfo: {
    permissions: string[]
    roles: string[]
    user: UserVO
    token: string
  }) {
    this.permissions = new Set(userInfo.permissions)
    this.roles = [...userInfo.roles]
    this.user = { ...userInfo.user }
    this.isSetUser = true
    this.emitUserChanged()
    this.token = userInfo.token
  }

  // 更新头像
  setUserAvatar(avatar: string) {
    this.user.avatar = avatar
    this.emitUserChanged()
  }

  // 更新昵称
  setUserNickname(nickname: string) {
    this.user.nickname = nickname
    this.emitUserChanged()
  }

  // 重置用户状态
  reset() {
    this.permissions = new Set<string>()
    this.roles = []
    this.isSetUser = false
    this.user = {
      id: 0,
      avatar: '',
      nickname: '',
      deptId: 0,
      username: ''
    }
    this.token = ''
    this.emitUserChanged()
  }

  // 触发 user-changed 事件（实际使用中可能需结合事件总线或回调）
  private emitUserChanged() {
    // 此处仅为示意；若需真正 emit，应通过外部注入回调或使用 mitt 等
    // 例如：this.onUserChanged?.()
  }

  // 可选：允许外部注册变更回调（如需）
  // private onUserChanged?: () => void
  // setOnUserChanged(fn: () => void) {
  //   this.onUserChanged = fn
  // }
}

export const userStore = reactive(new UserStore())