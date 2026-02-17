<!--
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
-->
<template>
  <div class="execution-details">
    <!-- Plan overview -->
    <div class="plan-overview" v-if="planExecution">
      <!-- Parent tool call information for sub-plans -->
      <div v-if="planExecution.parentActToolCall" class="parent-tool-call">
        <div class="parent-tool-header">
          <Icon icon="carbon:flow" class="tool-icon" />
          <span class="tool-label">{{ $t('chat.triggeredByTool') }}:</span>
          <span class="tool-name">{{ planExecution.parentActToolCall.name }}</span>
        </div>
        <div v-if="planExecution.parentActToolCall.parameters" class="tool-parameters">
          <span class="param-label">{{ $t('common.parameters') }}:</span>
          <pre class="param-content">{{
            formatToolParameters(planExecution.parentActToolCall.parameters)
          }}</pre>
        </div>
      </div>
    </div>

    <!-- Agent execution sequence -->
    <div
      class="agent-execution-container"
      v-if="(planExecution?.agentExecutionSequence?.length ?? 0) > 0"
    >
      <h4 class="section-title">{{ $t('chat.agentExecutionSequence') }}</h4>

      <div
        v-for="(agentExecution, agentIndex) in planExecution?.agentExecutionSequence"
        :key="agentExecution.id || agentIndex"
        class="agent-execution-item"
        :class="getAgentStatusClass(agentExecution.status)"
      >
        <!-- Agent execution header -->
        <div
          class="agent-header"
          @click="handleAgentClick(agentExecution)"
          :title="
            agentExecution.agentName === 'ConfigurableDynaAgent'
              ? $t('chat.clickToViewExecutionDetails')
              : ''
          "
        >
          <div class="agent-info">
            <div class="agent-details">
              <div class="agent-name">
                {{
                  agentExecution.agentName === 'ConfigurableDynaAgent'
                    ? planExecution.title ||
                      agentExecution.latestMethodName ||
                      $t('chat.funcAgentExecutionDetails')
                    : agentExecution.agentName || $t('chat.unknownAgent')
                }}
              </div>
              <div class="request-content">
                <span class="click-hint">{{ $t('chat.clickToViewExecutionDetails') }}</span>
              </div>
            </div>
          </div>
          <div class="agent-controls">
            <div class="agent-status-badge" :class="getAgentStatusClass(agentExecution.status)">
              {{ getAgentStatusText(agentExecution.status) }}
            </div>
          </div>
        </div>

        <!-- Agent execution info -->
        <div class="agent-execution-info">
          <!-- Agent result -->
          <div v-if="agentExecution.result" class="agent-result">
            <div class="result-header">
              <Icon icon="carbon:checkmark" class="result-icon" />
              <span class="result-label">{{ $t('chat.agentResult') }}:</span>
            </div>
            <pre class="result-content">{{ formatExecutionResult(agentExecution.result) }}</pre>
          </div>

          <!-- Error message -->
          <div v-if="agentExecution.errorMessage" class="agent-error">
            <div class="error-header">
              <Icon icon="carbon:warning" class="error-icon" />
              <span class="error-label">{{ $t('chat.errorMessage') }}:</span>
            </div>
            <pre class="error-content">{{ agentExecution.errorMessage }}</pre>
          </div>

          <!-- Latest tool info -->
          <div
            v-if="
              agentExecution.agentRequest ||
              agentExecution.latestMethodName ||
              agentExecution.latestMethodArgs ||
              agentExecution.latestRoundNumber
            "
            class="agent-tool-info"
          >
            <div
              class="tool-info-header"
              @click="toggleToolInfo(agentExecution)"
              :class="{ expanded: isToolInfoExpanded(agentExecution) }"
            >
              <span
                v-if="
                  agentExecution.agentName === 'ConfigurableDynaAgent' &&
                  agentExecution.latestRoundNumber !== undefined &&
                  agentExecution.latestRoundNumber !== null
                "
                class="tool-info-round-info"
              >
                {{ $t('chat.roundLabel', { round: agentExecution.latestRoundNumber }) }}
              </span>
              <span v-if="agentExecution.latestMethodName" class="tool-info-method-name">
                {{ agentExecution.latestMethodName }}
              </span>
              <Icon
                :icon="
                  isToolInfoExpanded(agentExecution) ? 'carbon:chevron-up' : 'carbon:chevron-right'
                "
                class="tool-info-toggle-icon"
              />
            </div>
            <div v-if="isToolInfoExpanded(agentExecution)" class="tool-info-content">
              <!-- User request detail -->
              <div v-if="agentExecution.agentRequest" class="tool-info-item">
                <Icon icon="carbon:chat" class="tool-info-item-icon" />
                <span class="tool-info-item-label">{{ $t('chat.userRequest') }}:</span>
                <pre class="tool-info-item-value tool-args-content">{{
                  agentExecution.agentRequest
                }}</pre>
              </div>
              <div v-if="agentExecution.latestMethodName" class="tool-info-item">
                <Icon icon="carbon:code" class="tool-info-item-icon" />
                <span class="tool-info-item-label">{{ $t('chat.methodName') }}:</span>
                <span class="tool-info-item-value">{{ agentExecution.latestMethodName }}</span>
              </div>
              <div v-if="agentExecution.latestMethodArgs" class="tool-info-item">
                <Icon icon="carbon:settings" class="tool-info-item-icon" />
                <span class="tool-info-item-label">{{ $t('chat.methodArgs') }}:</span>
                <pre class="tool-info-item-value tool-args-content">{{
                  formatToolParameters(agentExecution.latestMethodArgs)
                }}</pre>
              </div>
            </div>
          </div>
        </div>

        <!-- Sub-plan executions with nested support -->
        <div v-if="agentExecution.subPlanExecutionRecords?.length" class="sub-plans-container">
          <div class="sub-plans-header">
            <Icon icon="carbon:tree-view" class="sub-plans-icon" />
            <span class="sub-plans-title">
              {{ $t('chat.subPlanExecutions') }} ({{
                agentExecution.subPlanExecutionRecords.length
              }})
            </span>
          </div>

          <div class="sub-plans-list">
            <RecursiveSubPlan
              v-for="(subPlan, subPlanIndex) in agentExecution.subPlanExecutionRecords"
              :key="subPlan.currentPlanId || subPlanIndex"
              :sub-plan="subPlan"
              :sub-plan-index="subPlanIndex"
              :nesting-level="0"
              :max-nesting-depth="3"
              :max-visible-steps="2"
              @sub-plan-selected="handleSubPlanClick"
              @step-selected="handleStepSelected"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { CompatiblePlanExecutionRecord } from '@/types/message-dialog'
import type {
  AgentExecutionRecord,
  ExecutionStatus,
  PlanExecutionRecord,
} from '@/types/plan-execution-record'
import { Icon } from '@iconify/vue'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import RecursiveSubPlan from './RecursiveSubPlan.vue'

interface Props {
  planExecution: CompatiblePlanExecutionRecord
}

interface Emits {
  (
    e: 'sub-plan-selected',
    agentIndex: number,
    subPlanIndex: number,
    subPlan: PlanExecutionRecord
  ): void
  (e: 'step-selected', stepId: string): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

// Initialize i18n
const { t } = useI18n()

// Collapsible state for tool info (using agent execution ID as key)
const toolInfoExpanded = ref<Record<string, boolean>>({})

// Toggle tool info expansion
const toggleToolInfo = (agentExecution: AgentExecutionRecord) => {
  const key = agentExecution.id?.toString() || agentExecution.stepId || ''
  if (key) {
    toolInfoExpanded.value[key] = !toolInfoExpanded.value[key]
  }
}

// Check if tool info is expanded
const isToolInfoExpanded = (agentExecution: AgentExecutionRecord): boolean => {
  const key = agentExecution.id?.toString() || agentExecution.stepId || ''
  return toolInfoExpanded.value[key] || false
}

// Agent click handler
const handleAgentClick = (agentExecution: AgentExecutionRecord) => {
  if (agentExecution.stepId) {
    emit('step-selected', agentExecution.stepId)
  } else {
    console.warn('[ExecutionDetails] Agent execution has no stepId:', agentExecution)
  }
}

// Agent status methods
const getAgentStatusClass = (status?: ExecutionStatus): string => {
  switch (status) {
    case 'RUNNING':
      return 'running'
    case 'FINISHED':
      return 'completed'
    case 'IDLE':
    default:
      return 'pending'
  }
}

const getAgentStatusText = (status?: ExecutionStatus): string => {
  switch (status) {
    case 'RUNNING':
      return t('chat.status.executing')
    case 'FINISHED':
      return t('chat.status.completed')
    case 'IDLE':
    default:
      return t('chat.status.pending')
  }
}

// Note: Sub-plan status methods are now handled by RecursiveSubPlan component

// Note: Agent preview status methods are now handled by RecursiveSubPlan component

// Event handlers
const handleSubPlanClick = (
  agentIndex: number,
  subPlanIndex: number,
  subPlan: PlanExecutionRecord
) => {
  // If clicking on sub-plan header, select the first agent's stepId if available
  if (agentIndex === -1 && subPlan.agentExecutionSequence?.length) {
    const firstAgent = subPlan.agentExecutionSequence[0]
    if (firstAgent.stepId) {
      emit('step-selected', firstAgent.stepId)
      return
    }
  }
  emit('sub-plan-selected', agentIndex, subPlanIndex, subPlan)
}

const handleStepSelected = (stepId: string) => {
  emit('step-selected', stepId)
}

// Note: Sub-plan agent and think-act step handling is now done by RecursiveSubPlan component

// Helper methods

/**
 * Truncate long text by keeping start and end, replacing middle with ellipsis
 * @param text - The text to truncate
 * @param maxLength - Maximum length before truncation (default: 20000)
 * @param startLength - Length to keep at the start (default: 10000)
 * @param endLength - Length to keep at the end (default: 10000)
 * @returns Truncated text if exceeds maxLength, original text otherwise
 */
const truncateLongText = (
  text: string,
  maxLength = 20000,
  startLength = 10000,
  endLength = 10000
): string => {
  if (!text || text.length <= maxLength) {
    return text
  }
  const ellipsis = '\n\n... [Content truncated, middle part removed] ...\n\n'
  const start = text.substring(0, startLength)
  const end = text.substring(text.length - endLength)
  return start + ellipsis + end
}

const formatToolParameters = (parameters?: string): string => {
  if (!parameters) return ''

  try {
    const parsed = JSON.parse(parameters)
    const formatted = JSON.stringify(parsed, null, 2)
    return truncateLongText(formatted)
  } catch {
    return truncateLongText(parameters)
  }
}

/**
 * Format execution result text, truncating if too long
 * @param result - The result text to format
 * @returns Formatted and truncated result text
 */
const formatExecutionResult = (result?: string): string => {
  if (!result) return ''
  return truncateLongText(result)
}
</script>

<style lang="less" scoped>
.execution-details {
  // Plan overview
  .plan-overview {
    margin-bottom: 20px;

    .parent-tool-call {
      background: rgba(var(--bg-primary-rgb), 0.1);
      border: 1px solid var(--border-primary);
      border-radius: 8px;
      padding: 12px;

      .parent-tool-header {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 8px;

        .tool-icon {
          font-size: 16px;
          color: var(--accent-primary, #667eea);
        }

        .tool-label {
          color: var(--text-tertiary, #aaaaaa);
          font-size: 13px;
        }

        .tool-name {
          color: var(--text-primary, #ffffff);
          font-weight: 600;
          font-size: 14px;
        }
      }

      .tool-parameters {
        .param-label {
          color: var(--text-tertiary, #aaaaaa);
          font-size: 12px;
          margin-bottom: 4px;
          display: block;
        }

        .param-content {
          margin: 0;
          padding: 8px;
          background: var(--overlay-light);
          border-radius: 4px;
          font-family: monospace;
          font-size: 11px;
          color: var(--text-secondary, #cccccc);
          white-space: pre-wrap;
          max-height: 120px;
          overflow-y: auto;
        }
      }
    }
  }

  // Agent execution container
  .agent-execution-container {
    .section-title {
      margin: 0 0 16px 0;
      font-size: 14px;
      font-weight: 600;
      color: var(--text-primary, #ffffff);
      padding-bottom: 8px;
      border-bottom: 1px solid var(--border-default);
    }

    .agent-execution-item {
      margin-bottom: 16px;
      border: 1px solid var(--border-primary);
      border-radius: 8px;
      overflow: hidden;
      transition: all 0.2s ease;

      &:last-child {
        margin-bottom: 0;
      }

      &.running {
        border-color: rgba(102, 126, 234, 0.4);
        box-shadow: 0 0 8px var(--accent-surface-3);
      }

      &.completed {
        border-color: var(--success-border);
      }

      &.pending {
        opacity: 0.8;
      }

      .agent-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 12px 16px;
        background: rgba(var(--bg-primary-rgb), 0.02);
        cursor: pointer;
        transition: background 0.2s ease;

        &:hover {
          background: var(--scrollbar-track, rgba(255, 255, 255, 0.05));
        }

        .agent-info {
          display: flex;
          align-items: center;
          gap: 12px;
          flex: 1;

          .agent-details {
            .agent-name {
              font-weight: 600;
              color: var(--text-primary, #ffffff);
              font-size: 14px;
              margin-bottom: 2px;
            }

            .request-content {
              margin: 4px 0 0 0;
              padding: 8px;
              background: var(--overlay-light);
              border-radius: 4px;
              font-family: monospace;
              font-size: 14px;
              color: var(--text-secondary, #cccccc);
              white-space: pre-wrap;
              word-wrap: break-word;
              word-break: break-word;
              overflow-wrap: break-word;
              max-height: 120px;
              overflow-y: auto;
              line-height: 1.4;
            }
          }
        }

        .agent-controls {
          display: flex;
          align-items: center;
          gap: 12px;

          .agent-status-badge {
            padding: 3px 8px;
            border-radius: 10px;
            font-size: 11px;
            font-weight: 500;

            &.running {
              background: var(--accent-surface-3);
              color: var(--accent-primary, #667eea);
            }

            &.completed {
              background: var(--success-border);
              color: var(--success, #22c55e);
            }

            &.pending {
              background: rgba(156, 163, 175, 0.2);
              color: var(--text-tertiary);
            }
          }

          .step-select-icon {
            font-size: 16px;
            color: var(--accent-primary, #667eea);
            transition: transform 0.2s ease;
          }
        }
      }

      .agent-execution-info {
        padding: 6px 16px;
        background: var(--overlay-subtle);
        border-top: 1px solid var(--surface-subtle);

        .agent-request,
        .agent-result,
        .agent-error,
        .agent-tool-info {
          margin-bottom: 12px;

          &:last-child {
            margin-bottom: 0;
          }

          .request-header,
          .result-header,
          .error-header,
          .tool-info-header {
            display: flex;
            align-items: center;
            gap: 6px;
            margin-bottom: 6px;

            &.expanded {
              margin-bottom: 8px;
            }

            .request-icon,
            .result-icon,
            .error-icon,
            .tool-info-icon {
              font-size: 14px;
            }

            .result-icon {
              color: var(--success, #22c55e);
            }

            .error-icon {
              color: var(--error, #ef4444);
            }

            .tool-info-icon {
              color: var(--accent-primary);
            }

            .request-label,
            .result-label,
            .error-label,
            .tool-info-label {
              color: var(--text-secondary, #cccccc);
              font-size: 13px;
              font-weight: 500;
            }

            // Collapsible tool info header styles
            &.expanded,
            &:has(.tool-info-toggle-icon) {
              cursor: pointer;
              padding: 4px 8px;
              border-radius: 4px;
              transition: background 0.2s ease;

              &:hover {
                background: var(--surface-subtle);
              }
            }

            .tool-info-request {
              color: #cccccc;
              font-size: 12px;
              font-style: italic;
              overflow: hidden;
              text-overflow: ellipsis;
              white-space: nowrap;
              max-width: 200px;
            }

            .tool-info-separator {
              color: #666666;
              font-size: 12px;
              margin: 0 6px;
              flex-shrink: 0;
            }

            .tool-info-round-info {
              color: var(--accent-primary);
              font-weight: 500;
              font-size: 13px;
              white-space: nowrap;
              line-height: 1.5;
            }

            .tool-info-method-name {
              flex: 1;
              color: #ffffff;
              font-size: 13px;
              font-weight: 500;
            }
          }

          .result-content, .error-content {
            margin: 0;
            padding: 8px;
            background: rgba(var(--bg-primary-rgb), 0.2);
            border-radius: 4px;
            font-family: monospace;
            font-size: 12px;
            white-space: pre-wrap;
            max-height: 150px;
            overflow-y: auto;
            color: var(--text-secondary, #cccccc);
          }

          .error-content {
            color: #ff9999;
            border: 1px solid rgba(239, 68, 68, 0.2);
          }
        }
      }

      .sub-plans-container {
        padding: 16px;
        background: var(--overlay-subtle);
        border-top: 1px solid var(--scrollbar-track, rgba(255, 255, 255, 0.05));

        .sub-plans-header {
          display: flex;
          align-items: center;
          gap: 8px;
          margin-bottom: 12px;

          .sub-plans-icon {
            font-size: 11px;
            color: var(--accent-primary, #667eea);
          }

          .sub-plans-title {
            color: var(--text-primary, #ffffff);
            font-weight: 600;
            font-size: 11px;
          }
        }

        .sub-plans-list {
          display: flex;
          flex-direction: column;
          gap: 12px;

          .sub-plan-item {
            background: var(--accent-surface-1);
            border: 1px solid var(--accent-surface-2);
            border-radius: 6px;
            padding: 12px;
            cursor: pointer;
            transition: all 0.2s ease;

            &:hover {
              background: var(--accent-surface-2);
              border-color: var(--accent-surface-3);
            }

            &.running {
              border-color: var(--selection-bg, rgba(102, 126, 234, 0.3));
              background: rgba(102, 126, 234, 0.08);
              box-shadow: 0 0 8px var(--accent-glow);
            }

            &.completed {
              border-color: var(--success-border);
              background: var(--success-surface);
            }

            &.pending {
              opacity: 0.7;
            }

            .sub-plan-header {
              display: flex;
              align-items: center;
              justify-content: space-between;
              margin-bottom: 8px;

              .sub-plan-info {
                display: flex;
                align-items: center;
                gap: 8px;
                flex: 1;

                .sub-plan-status-icon {
                  font-size: 16px;

                  &.completed {
                    color: var(--success, #22c55e);
                  }

                  &.running {
                    color: var(--accent-primary, #667eea);
                  }

                  &.in-progress {
                    color: var(--warning, #fbbf24);
                  }

                  &.pending {
                    color: var(--text-tertiary);
                  }
                }

                .sub-plan-details {
                  .sub-plan-title {
                    font-weight: 600;
                    color: var(--text-primary, #ffffff);
                    font-size: 13px;
                    margin-bottom: 2px;
                  }

                  .sub-plan-id {
                    color: var(--text-tertiary, #aaaaaa);
                    font-size: 11px;
                    font-family: monospace;
                  }
                }
              }

              .sub-plan-meta {
                display: flex;
                align-items: center;
                gap: 8px;

                .trigger-tool {
                  display: flex;
                  align-items: center;
                  gap: 4px;
                  padding: 2px 6px;
                  background: var(--accent-surface-2);
                  border-radius: 4px;
                  font-size: 10px;

                  .trigger-icon {
                    font-size: 10px;
                    color: var(--accent-primary, #667eea);
                  }

                  .trigger-text {
                    color: var(--text-secondary, #cccccc);
                    font-weight: 500;
                  }
                }
              }

              .sub-plan-status-badge {
                padding: 2px 6px;
                border-radius: 8px;
                font-size: 10px;
                font-weight: 500;

                &.completed {
                  background: var(--success-border);
                  color: var(--success, #22c55e);
                }

                &.running {
                  background: var(--accent-surface-3);
                  color: var(--accent-primary, #667eea);
                }

                &.in-progress {
                  background: var(--warning-border);
                  color: var(--warning, #fbbf24);
                }

                &.pending {
                  background: rgba(156, 163, 175, 0.2);
                  color: var(--text-tertiary);
                }
              }
            }

            .sub-plan-progress {
              margin-bottom: 8px;

              .progress-info {
                .progress-text {
                  color: var(--text-tertiary, #aaaaaa);
                  font-size: 10px;
                  margin-bottom: 4px;
                }

                .progress-bar {
                  background: var(--overlay-light);
                  border-radius: 4px;
                  height: 4px;
                  overflow: hidden;

                  .progress-fill {
                    height: 100%;
                    background: linear-gradient(90deg, var(--accent-primary, #667eea), #09df75);
                    transition: width 0.3s ease;
                    border-radius: 4px;
                  }
                }
              }
            }

            .sub-plan-agents-steps {
              .agents-steps-header {
                color: var(--text-tertiary, #aaaaaa);
                font-size: 11px;
                margin-bottom: 6px;
                font-weight: 500;
              }

              .agents-steps-list {
                display: flex;
                flex-direction: column;
                gap: 8px;

                .agent-step-item {
                  border: 1px solid var(--border-primary);
                  border-radius: 6px;
                  padding: 8px;
                  background: var(--overlay-subtle);
                  cursor: pointer;
                  transition: all 0.2s;

                  &:hover {
                    background: var(--overlay-subtle);
                    border-color: var(--scrollbar-thumb, rgba(255, 255, 255, 0.2));
                  }

                  &.completed {
                    border-color: var(--success-border);
                    background: var(--success-surface);
                  }

                  &.running {
                    border-color: var(--selection-bg, rgba(102, 126, 234, 0.3));
                    background: rgba(102, 126, 234, 0.08);
                  }

                  &.pending {
                    opacity: 0.7;
                  }

                  .agent-step-header {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    margin-bottom: 8px;

                    .agent-icon {
                      font-size: 14px;

                      &.completed {
                        color: var(--success, #22c55e);
                      }

                      &.running {
                        color: var(--accent-primary, #667eea);
                      }

                      &.pending {
                        color: var(--text-tertiary);
                      }
                    }

                    .agent-name {
                      color: var(--text-primary, #ffffff);
                      font-size: 13px;
                      font-weight: 500;
                      flex: 1;
                    }

                    .agent-status-badge {
                      padding: 2px 6px;
                      border-radius: 3px;
                      font-size: 10px;
                      font-weight: 500;

                      &.completed {
                        background: var(--success-border);
                        color: var(--success, #22c55e);
                      }

                      &.running {
                        background: var(--accent-surface-3);
                        color: var(--accent-primary, #667eea);
                      }

                      &.pending {
                        background: rgba(156, 163, 175, 0.2);
                        color: var(--text-tertiary);
                      }
                    }
                  }

                  .sub-agent-execution-info {
                    margin-left: 22px;

                    .agent-result, .agent-error {
                      margin-bottom: 8px;

                      &:last-child {
                        margin-bottom: 0;
                      }

                      .result-header, .error-header {
                        display: flex;
                        align-items: center;
                        gap: 4px;
                        margin-bottom: 4px;

                        .result-icon, .error-icon {
                          font-size: 12px;
                        }

                        .result-icon {
                          color: var(--success, #22c55e);
                        }

                        .error-icon {
                          color: var(--error, #ef4444);
                        }

                        .result-label, .error-label {
                          color: var(--text-primary, #ffffff);
                          font-size: 11px;
                          font-weight: 500;
                        }
                      }

                      .result-content, .error-content {
                        margin: 0;
                        padding: 6px;
                        background: var(--overlay-light);
                        border-radius: 3px;
                        font-family: monospace;
                        font-size: 10px;
                        white-space: pre-wrap;
                        max-height: 80px;
                        overflow-y: auto;
                        color: var(--text-secondary, #cccccc);
                        line-height: 1.3;
                      }
                    }

                    .think-act-preview {
                      margin-top: 8px;

                      .think-act-header {
                        display: flex;
                        align-items: center;
                        gap: 4px;
                        margin-bottom: 6px;

                        .think-act-icon {
                          font-size: 12px;
                          color: var(--accent-primary, #667eea);
                        }

                        .think-act-label {
                          color: var(--text-tertiary, #aaaaaa);
                          font-size: 11px;
                          font-weight: 500;
                        }
                      }

                      .think-act-steps-preview {
                        display: flex;
                        flex-direction: column;
                        gap: 3px;

                        .think-act-step-preview {
                          display: flex;
                          align-items: center;
                          gap: 6px;
                          padding: 4px 6px;
                          background: var(--overlay-subtle);
                          border-radius: 3px;
                          cursor: pointer;
                          transition: all 0.2s;
                          font-size: 10px;

                          &:hover {
                            background: var(--overlay-light);
                          }

                          .step-number {
                            color: var(--accent-primary, #667eea);
                            font-weight: 500;
                            min-width: 20px;
                          }

                          .step-description {
                            color: var(--text-secondary, #cccccc);
                            flex: 1;
                            white-space: nowrap;
                            overflow: hidden;
                            text-overflow: ellipsis;
                          }

                          .step-arrow {
                            font-size: 10px;
                            color: #888888;
                          }
                        }

                        .more-steps {
                          padding: 2px 6px;
                          color: #888888;
                          font-size: 9px;
                          font-style: italic;
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
</style>
