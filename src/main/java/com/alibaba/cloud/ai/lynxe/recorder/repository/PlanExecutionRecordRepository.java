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
package com.alibaba.cloud.ai.lynxe.recorder.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.alibaba.cloud.ai.lynxe.recorder.entity.po.PlanExecutionRecordEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanExecutionRecordRepository extends JpaRepository<PlanExecutionRecordEntity, Long> {

	/**
	 * Find plan execution record by current plan ID
	 */
	Optional<PlanExecutionRecordEntity> findByCurrentPlanId(String currentPlanId);

	/**
	 * Find all plan execution records by parent plan ID
	 */
	List<PlanExecutionRecordEntity> findByParentPlanId(String parentPlanId);

	/**
	 * Find all plan execution records by root plan ID
	 */
	List<PlanExecutionRecordEntity> findByRootPlanId(String rootPlanId);

	/**
	 * Check if a plan execution record exists by current plan ID
	 */
	boolean existsByCurrentPlanId(String currentPlanId);

	/**
	 * Delete plan execution record by current plan ID
	 */
	void deleteByCurrentPlanId(String currentPlanId);

	/**
	 * 按 title 模糊匹配查找根计划记录（parentPlanId IS NULL），按开始时间降序排列。
	 * 用于列出特定类型的历史会话，例如 Code Agent 会话。
	 */
	@Query("SELECT p FROM PlanExecutionRecordEntity p WHERE p.parentPlanId IS NULL AND p.title LIKE CONCAT('%', :titlePattern, '%') ORDER BY p.startTime DESC")
	List<PlanExecutionRecordEntity> findRootPlansByTitleContaining(@Param("titlePattern") String titlePattern);

	/**
	 * 按 conversationId 查找所有计划记录，按开始时间升序排列。
	 * 用于获取同一会话中的所有轮次。
	 */
	List<PlanExecutionRecordEntity> findByConversationIdOrderByStartTimeAsc(String conversationId);

	/**
	 * 按 title 模糊匹配查找、按 conversationId 分组去重的根计划记录，
	 * 每个 conversationId 只取最早的一条作为代表。按开始时间降序排列。
	 */
	@Query("SELECT p FROM PlanExecutionRecordEntity p WHERE p.parentPlanId IS NULL AND p.conversationId IS NOT NULL AND p.title LIKE CONCAT('%', :titlePattern, '%') AND p.startTime = (SELECT MIN(p2.startTime) FROM PlanExecutionRecordEntity p2 WHERE p2.conversationId = p.conversationId AND p2.parentPlanId IS NULL) ORDER BY p.startTime DESC")
	List<PlanExecutionRecordEntity> findDistinctSessionsByTitleContaining(@Param("titlePattern") String titlePattern);

	/**
	 * 按 conversationId 分组去重的根计划记录（不过滤 title），
	 * 每个 conversationId 只取最早的一条作为代表。按开始时间降序排列。
	 * 用于 Code Agent 历史会话列表（plan title 已改为用户输入，不再包含固定前缀）。
	 */
	@Query("SELECT p FROM PlanExecutionRecordEntity p WHERE p.parentPlanId IS NULL AND p.conversationId IS NOT NULL AND p.startTime = (SELECT MIN(p2.startTime) FROM PlanExecutionRecordEntity p2 WHERE p2.conversationId = p.conversationId AND p2.parentPlanId IS NULL) ORDER BY p.startTime DESC")
	List<PlanExecutionRecordEntity> findDistinctSessionsByConversationId();

}
