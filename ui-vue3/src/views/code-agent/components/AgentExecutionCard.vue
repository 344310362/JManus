<!--
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
-->

<template>
  <div class="agent-execution-card" :class="{ completed, expanded }">
    <!-- Collapsed header (always visible) -->
    <div class="card-header" @click="expanded = !expanded">
      <div class="card-header-left">
        <Icon
          :icon="completed ? 'carbon:checkmark-filled' : 'carbon:circle-dash'"
          :class="['status-icon', { spin: !completed }]"
        />
        <span class="card-title">
          {{ completed ? 'Agent Completed' : 'Agent Executing...' }}
        </span>
      </div>
      <div class="card-header-right">
        <span class="card-meta" title="Total tokens (input + output)">{{ formatNumber(totalInputTokens) }} / {{ formatNumber(totalOutputTokens) }} tokens</span>
        <span class="card-meta">{{ elapsed }}</span>
        <Icon
          :icon="expanded ? 'carbon:chevron-up' : 'carbon:chevron-down'"
          class="toggle-icon"
        />
      </div>
    </div>

    <!-- Expanded content -->
    <div v-if="expanded" class="card-body">
      <!-- Steps list -->
      <div v-if="steps.length" class="steps-list">
        <div
          v-for="(step, index) in steps"
          :key="index"
          class="step-item"
          :class="stepClass(step, index)"
        >
          <div class="step-row-main">
            <div class="step-indicator">
              <Icon v-if="step.status === 'FINISHED'" icon="carbon:checkmark-filled" class="step-icon completed" />
              <Icon v-else-if="isStepRunning(index)" icon="carbon:circle-dash" class="step-icon running spin" />
              <span v-else class="step-number">{{ index + 1 }}</span>
            </div>
            <div class="step-content">
              <span class="step-agent-name">{{ step.agentName || `Step ${index + 1}` }}</span>
              <span v-if="step.modelName" class="step-model-name" :title="step.modelName">{{ step.modelName }}</span>
              <span class="step-status-badge" :class="stepClass(step, index)">{{ step.status || 'PENDING' }}</span>
            </div>
            <div class="step-meta">
              <span v-if="step.thinkActSteps?.length" class="meta-item" title="Rounds">
                <Icon icon="carbon:repeat" /> {{ step.thinkActSteps.length }}
              </span>
              <span class="meta-item" title="Tokens (in/out)">
                <Icon icon="carbon:meter" /> {{ formatNumber(stepInputTokens(step)) }}/{{ formatNumber(stepOutputTokens(step)) }}
              </span>
            </div>
          </div>

          <!-- Tool call details for running or finished steps -->
          <div v-if="(isStepRunning(index) || step.status === 'FINISHED') && step.thinkActSteps?.length" class="step-tools">
            <div v-for="(tar, tarIdx) in step.thinkActSteps" :key="tarIdx" class="think-act-round">
              <div class="round-header" @click.stop="toggleRound(index, tarIdx)">
                <span class="round-label">Round {{ tarIdx + 1 }}</span>
                <span
                  class="round-status"
                  :class="computeRoundStatusClass(tar, tarIdx, step)"
                >
                  {{ computeRoundStatusText(tar, tarIdx, step) }}
                </span>
                <span class="round-tokens" title="Tokens (in/out)">
                  {{ formatNumber(tar.inputCharCount || 0) }} / {{ formatNumber(tar.outputCharCount || 0) }}
                </span>
                <Icon
                  :icon="isRoundExpanded(index, tarIdx) ? 'carbon:chevron-up' : 'carbon:chevron-down'"
                  class="round-toggle"
                />
              </div>

              <!-- Tool calls (always visible) -->
              <div v-for="(tool, toolIdx) in (tar.actToolInfoList || [])" :key="toolIdx" class="tool-call-item">
                <Icon icon="carbon:function" class="tool-icon" />
                <span class="tool-name">{{ tool.name }}</span>
                <Icon
                  v-if="tool.result"
                  icon="carbon:checkmark"
                  class="tool-result-icon completed"
                />
                <Icon
                  v-else
                  icon="carbon:circle-dash"
                  class="tool-result-icon running spin"
                />
              </div>

              <!-- Expanded round details (thinking context & output) -->
              <div v-if="isRoundExpanded(index, tarIdx)" class="round-details">
                <div v-if="tar.thinkInput" class="round-thinking round-thinking-input">
                  <div class="detail-label">
                    <Icon icon="carbon:data-base" /> Input Context
                  </div>
                  <div class="detail-content">{{ tar.thinkInput }}</div>
                </div>
                <div v-if="tar.thinkOutput" class="round-thinking">
                  <div class="detail-label">
                    <Icon icon="carbon:idea" /> Thinking
                  </div>
                  <div class="detail-content">{{ tar.thinkOutput }}</div>
                </div>
                <div v-else-if="tar.actToolInfoList?.length" class="round-thinking round-thinking-fallback">
                  <div class="detail-label">
                    <Icon icon="carbon:function" /> Tool Calls
                  </div>
                  <div class="detail-content">{{ roundToolSummary(tar) }}</div>
                </div>
                <div v-if="tar.errorMessage" class="round-error">
                  <Icon icon="carbon:warning" /> {{ tar.errorMessage }}
                </div>
              </div>
            </div>

            <!-- LLM Thinking indicator: shown when step is RUNNING but all existing rounds are DONE -->
            <div v-if="isStepRunning(index) && isWaitingForNextRound(step)" class="thinking-section">
              <div class="thinking-indicator" @click.stop="thinkingExpanded = !thinkingExpanded">
                <Icon icon="carbon:circle-dash" class="spin" />
                <span>LLM Thinking...</span>
                <span v-if="step.modelName" class="thinking-model">{{ step.modelName }}</span>
                <span class="thinking-step-progress">Step {{ step.currentStep || (step.thinkActSteps?.length || 0) + 1 }} / {{ step.maxSteps || '?' }}</span>
                <Icon
                  :icon="thinkingExpanded ? 'carbon:chevron-up' : 'carbon:chevron-down'"
                  class="thinking-toggle"
                />
              </div>
              <div v-if="thinkingExpanded" class="thinking-details">
                <div class="thinking-detail-row">
                  <span class="thinking-detail-label">Model</span>
                  <span class="thinking-detail-value mono">{{ step.modelName || 'default' }}</span>
                </div>
                <div class="thinking-detail-row">
                  <span class="thinking-detail-label">Tokens (in/out)</span>
                  <span class="thinking-detail-value mono">{{ formatNumber(stepInputTokens(step)) }} / {{ formatNumber(stepOutputTokens(step)) }}</span>
                </div>
                <div v-if="step.agentRequest" class="thinking-context">
                  <div class="detail-label">
                    <Icon icon="carbon:data-base" /> Step Prompt (Context)
                  </div>
                  <div class="detail-content">{{ step.agentRequest }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="empty-steps">
        <Icon icon="carbon:circle-dash" class="spin" /> Waiting for steps...
      </div>

      <!-- Error -->
      <div v-if="errorMessage" class="card-error">
        <Icon icon="carbon:warning" /> {{ errorMessage }}
      </div>

      <!-- Summary -->
      <div v-if="completed && planRecord?.summary" class="card-summary">
        {{ planRecord.summary }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { PlanExecutionRecord, ThinkActRecord as ThinkActRecordType, AgentExecutionRecord } from '@/types/plan-execution-record'
import { Icon } from '@iconify/vue'
import { computed, ref, reactive, onUnmounted } from 'vue'

interface Props {
  planRecord: PlanExecutionRecord | null
  completed: boolean
}

const props = defineProps<Props>()
const expanded = ref(false)
const thinkingExpanded = ref(false)

// Track which rounds are collapsed: key = `${stepIndex}-${roundIndex}`
// 默认展开所有 round，点击可收起
const collapsedRounds = reactive<Set<string>>(new Set())

function toggleRound(stepIdx: number, roundIdx: number) {
  const key = `${stepIdx}-${roundIdx}`
  if (collapsedRounds.has(key)) {
    collapsedRounds.delete(key)
  } else {
    collapsedRounds.add(key)
  }
}

function isRoundExpanded(stepIdx: number, roundIdx: number) {
  return !collapsedRounds.has(`${stepIdx}-${roundIdx}`)
}

// Compute steps from agentExecutionSequence
const steps = computed(() => {
  if (!props.planRecord?.agentExecutionSequence) return []
  return props.planRecord.agentExecutionSequence
})

const currentStepIndex = computed(() => props.planRecord?.currentStepIndex ?? -1)

function isStepRunning(index: number) {
  return index === currentStepIndex.value && !props.completed
}

function stepClass(step: AgentExecutionRecord, index: number) {
  if (step.status === 'FINISHED') return 'completed'
  if (isStepRunning(index)) return 'running'
  return 'pending'
}

// Round status: inferred from tool results since backend ThinkActRecordEntity has no status column
function isRoundDone(tar: ThinkActRecordType): boolean {
  if (tar.status === 'FINISHED') return true
  // Infer from tools: if all tools have results, the round is done
  const tools = tar.actToolInfoList
  if (tools && tools.length > 0) {
    return tools.every(t => t.result != null && t.result !== '')
  }
  return false
}

function computeRoundStatusClass(tar: ThinkActRecordType, tarIdx: number, step: AgentExecutionRecord) {
  if (isRoundDone(tar)) return 'finished'
  const isLast = tarIdx === (step.thinkActSteps?.length ?? 0) - 1
  const stepRunning = step.status === 'RUNNING' || (step.status !== 'FINISHED' && step.status !== 'IDLE')
  if (isLast && stepRunning) return 'running'
  return 'pending'
}

function computeRoundStatusText(tar: ThinkActRecordType, tarIdx: number, step: AgentExecutionRecord) {
  if (isRoundDone(tar)) return 'DONE'
  const isLast = tarIdx === (step.thinkActSteps?.length ?? 0) - 1
  const stepRunning = step.status === 'RUNNING' || (step.status !== 'FINISHED' && step.status !== 'IDLE')
  if (isLast && stepRunning) return 'RUNNING'
  // If the step is finished but this round doesn't look done, mark it done anyway
  if (step.status === 'FINISHED') return 'DONE'
  return 'PENDING'
}

// Token counts
function stepInputTokens(step: AgentExecutionRecord): number {
  if (!step.thinkActSteps) return 0
  return step.thinkActSteps.reduce((sum, tar) => sum + (tar.inputCharCount || 0), 0)
}

function stepOutputTokens(step: AgentExecutionRecord): number {
  if (!step.thinkActSteps) return 0
  return step.thinkActSteps.reduce((sum, tar) => sum + (tar.outputCharCount || 0), 0)
}

const totalInputTokens = computed(() => {
  if (!props.planRecord?.agentExecutionSequence) return 0
  return props.planRecord.agentExecutionSequence.reduce((sum, agent) => sum + stepInputTokens(agent), 0)
})

const totalOutputTokens = computed(() => {
  if (!props.planRecord?.agentExecutionSequence) return 0
  return props.planRecord.agentExecutionSequence.reduce((sum, agent) => sum + stepOutputTokens(agent), 0)
})

// Elapsed time
const now = ref(Date.now())
let timer: ReturnType<typeof setInterval> | null = null

timer = setInterval(() => {
  if (!props.completed) now.value = Date.now()
}, 1000)

onUnmounted(() => { if (timer) clearInterval(timer) })

const elapsed = computed(() => {
  const start = props.planRecord?.startTime
  if (!start) return '0s'
  const startMs = new Date(start).getTime()
  const endMs = props.planRecord?.endTime
    ? new Date(props.planRecord.endTime).getTime()
    : now.value
  const sec = Math.max(0, Math.floor((endMs - startMs) / 1000))
  if (sec < 60) return `${sec}s`
  return `${Math.floor(sec / 60)}m ${sec % 60}s`
})

// Error message from any agent
const errorMessage = computed(() => {
  if (!props.planRecord?.agentExecutionSequence) return ''
  for (const agent of props.planRecord.agentExecutionSequence) {
    if (agent.errorMessage) return agent.errorMessage
  }
  return ''
})

// Utilities
function formatNumber(n: number): string {
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K'
  return String(n)
}

/** Check if step is waiting for the next round (all existing rounds done, but step still running) */
function isWaitingForNextRound(step: AgentExecutionRecord): boolean {
  const rounds = step.thinkActSteps
  if (!rounds || rounds.length === 0) return true
  return rounds.every(r => isRoundDone(r))
}

/** Summarize tool calls for a round when thinkOutput is empty */
function roundToolSummary(tar: ThinkActRecordType): string {
  const tools = tar.actToolInfoList
  if (!tools || tools.length === 0) return ''
  const names = tools.map(t => t.name || 'unknown')
  const unique = [...new Set(names)]
  if (unique.length === 1) {
    return `Called ${unique[0]} × ${names.length}`
  }
  return unique.join(', ')
}
</script>

<style lang="less" scoped>
.agent-execution-card {
  background: var(--accent-surface-1);
  border: 1px solid var(--accent-glow);
  border-radius: 10px;
  overflow: hidden;
  transition: all 0.2s ease;
  max-width: 85%;

  &.completed {
    border-color: rgba(9, 223, 117, 0.25);
    background: rgba(9, 223, 117, 0.04);
  }

  &:hover {
    border-color: var(--accent-border-3);
  }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;

  &:hover { background: var(--surface-subtle); }
}

.card-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-icon {
  font-size: 16px;
  color: #fbbf24;

  .completed & { color: #09df75; }
}

.card-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary, #fff);
}

.card-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-meta {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.45);
  font-family: monospace;
}

.toggle-icon {
  font-size: 14px;
  color: var(--text-muted);
}

.card-body {
  padding: 0 14px 12px;
}

.steps-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.step-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--surface-hover);
  border: 1px solid var(--surface-subtle);

  &.running {
    background: var(--warning-surface);
    border-color: var(--warning-border);
  }

  &.completed {
    background: rgba(9, 223, 117, 0.05);
    border-color: rgba(9, 223, 117, 0.15);
  }
}

.step-row-main {
  display: flex;
  align-items: center;
  gap: 10px;
}

.step-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
}

.step-number {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--border-default);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: var(--text-tertiary);
}

.step-icon {
  font-size: 16px;
  &.completed { color: #09df75; }
  &.running { color: #fbbf24; }
}

.step-content {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.step-agent-name {
  font-size: 12px;
  color: var(--text-primary, #fff);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.step-model-name {
  font-size: 10px;
  color: var(--text-tertiary);
  font-family: monospace;
  padding: 1px 5px;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 160px;
  flex-shrink: 0;
}

.step-status-badge {
  padding: 1px 6px;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 500;
  flex-shrink: 0;

  &.completed {
    background: rgba(9, 223, 117, 0.15);
    color: #09df75;
  }
  &.running {
    background: var(--accent-surface-3);
    color: var(--accent-primary, #667eea);
  }
  &.pending {
    background: rgba(156, 163, 175, 0.15);
    color: var(--text-tertiary);
  }
}

.step-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 10px;
  color: var(--text-muted);
  font-family: monospace;

  .iconify { font-size: 11px; }
}

// Tool call details
.step-tools {
  width: 100%;
  padding-left: 32px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.think-act-round {
  display: flex;
  flex-direction: column;
  gap: 2px;
  border-left: 2px solid var(--border-default);
  padding-left: 8px;
  margin-bottom: 2px;
}

.round-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 0;
  cursor: pointer;
  user-select: none;

  &:hover {
    .round-toggle { color: var(--text-primary); }
  }
}

.round-label {
  font-size: 10px;
  font-weight: 600;
  color: var(--text-secondary, rgba(255, 255, 255, 0.7));
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.round-status {
  font-size: 9px;
  padding: 1px 5px;
  border-radius: 6px;
  font-weight: 500;

  &.finished {
    background: rgba(9, 223, 117, 0.12);
    color: #09df75;
  }
  &.running {
    background: var(--accent-surface-3);
    color: var(--accent-primary, #667eea);
  }
  &.pending {
    background: rgba(156, 163, 175, 0.12);
    color: var(--text-tertiary);
  }
}

.round-tokens {
  font-size: 9px;
  color: var(--text-muted);
  font-family: monospace;
  margin-left: auto;
}

.round-toggle {
  font-size: 11px;
  color: var(--text-muted);
  transition: color 0.15s;
}

.tool-call-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 8px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.03);
}

.tool-icon {
  font-size: 12px;
  color: var(--accent-primary, #667eea);
  flex-shrink: 0;
}

.tool-name {
  font-size: 11px;
  font-family: monospace;
  color: var(--text-primary, #fff);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-result-icon {
  font-size: 12px;
  flex-shrink: 0;

  &.completed {
    color: #09df75;
  }
  &.running {
    color: #fbbf24;
  }
}

// Round expanded details
.round-details {
  padding: 4px 8px;
  margin-top: 2px;
}

.round-thinking {
  background: rgba(255, 255, 255, 0.03);
  border-radius: 4px;
  padding: 6px 8px;

  &.round-thinking-fallback {
    opacity: 0.7;
  }

  &.round-thinking-input {
    border-left: 2px solid var(--accent-primary, #667eea);
    margin-bottom: 4px;

    .detail-label .iconify { color: var(--accent-primary, #667eea); }
  }

  .detail-label {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 10px;
    font-weight: 600;
    color: var(--text-secondary, rgba(255, 255, 255, 0.7));
    margin-bottom: 4px;
    text-transform: uppercase;
    letter-spacing: 0.3px;

    .iconify { font-size: 12px; color: #fbbf24; }
  }

  .detail-content {
    font-size: 11px;
    color: var(--text-primary, rgba(255, 255, 255, 0.85));
    line-height: 1.5;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 400px;
    overflow-y: auto;

    &::-webkit-scrollbar { width: 4px; }
    &::-webkit-scrollbar-thumb { background: var(--border-primary); border-radius: 2px; }
  }
}

.thinking-section {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.thinking-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  margin-top: 2px;
  border-left: 2px solid var(--accent-primary, #667eea);
  color: var(--accent-primary, #667eea);
  font-size: 11px;
  font-weight: 500;
  animation: thinking-pulse 2s ease-in-out infinite;
  cursor: pointer;
  user-select: none;

  &:hover {
    background: rgba(255, 255, 255, 0.03);
    .thinking-toggle { color: var(--text-primary); }
  }

  .iconify:first-child { font-size: 13px; }
}

.thinking-model {
  font-family: monospace;
  font-size: 10px;
  padding: 1px 5px;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 4px;
  color: var(--text-secondary, rgba(255, 255, 255, 0.7));
}

.thinking-step-progress {
  font-size: 10px;
  font-family: monospace;
  color: var(--text-muted);
  margin-left: auto;
}

.thinking-toggle {
  font-size: 11px;
  color: var(--text-muted);
  transition: color 0.15s;
}

.thinking-details {
  padding: 6px 8px 8px;
  margin-left: 2px;
  border-left: 2px solid var(--accent-primary, #667eea);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.thinking-detail-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
}

.thinking-detail-label {
  color: var(--text-muted);
  min-width: 80px;
  font-weight: 500;
}

.thinking-detail-value {
  color: var(--text-primary, #fff);

  &.mono { font-family: monospace; }
}

.thinking-context {
  background: rgba(255, 255, 255, 0.03);
  border-radius: 4px;
  padding: 6px 8px;
  border-left: 2px solid var(--accent-primary, #667eea);

  .detail-label {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 10px;
    font-weight: 600;
    color: var(--text-secondary, rgba(255, 255, 255, 0.7));
    margin-bottom: 4px;
    text-transform: uppercase;
    letter-spacing: 0.3px;

    .iconify { font-size: 12px; color: var(--accent-primary, #667eea); }
  }

  .detail-content {
    font-size: 11px;
    color: var(--text-primary, rgba(255, 255, 255, 0.85));
    line-height: 1.5;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 400px;
    overflow-y: auto;

    &::-webkit-scrollbar { width: 4px; }
    &::-webkit-scrollbar-thumb { background: var(--border-primary); border-radius: 2px; }
  }
}

@keyframes thinking-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.round-error {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  margin-top: 4px;
  padding: 4px 8px;
  background: var(--error-surface);
  border: 1px solid var(--error-border);
  border-radius: 4px;
  color: var(--error-text);
  font-size: 11px;
  line-height: 1.4;

  .iconify { flex-shrink: 0; margin-top: 1px; }
}

.empty-steps {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 0;
  color: var(--text-muted);
  font-size: 12px;
}

.card-error {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 6px 10px;
  background: var(--error-surface);
  border: 1px solid var(--error-border);
  border-radius: 6px;
  color: var(--error-text);
  font-size: 12px;
}

.card-summary {
  margin-top: 8px;
  padding: 8px 10px;
  background: var(--surface-hover);
  border-radius: 6px;
  font-size: 12px;
  color: var(--text-primary, #fff);
  line-height: 1.5;
  white-space: pre-wrap;
}

.spin {
  animation: spin 1s linear infinite;
  display: inline-block;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
