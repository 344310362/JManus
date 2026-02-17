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
package com.alibaba.cloud.ai.lynxe.codeagent.model;

import java.util.Map;

/**
 * WebSocket message DTOs for Code Agent communication.
 */
public class CodeAgentMessage {

	/**
	 * Message sent from frontend to backend via WebSocket.
	 */
	public static class Request {

		private String type; // "chat" | "edit"

		private String content;

		private Map<String, String> files; // optional: manual file edits from user

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getContent() {
			return content;
		}

		public void setContent(String content) {
			this.content = content;
		}

		public Map<String, String> getFiles() {
			return files;
		}

		public void setFiles(Map<String, String> files) {
			this.files = files;
		}

	}

	/**
	 * Message sent from backend to frontend via WebSocket.
	 */
	public static class Response {

		private String type; // "token" | "file" | "status" | "error" | "done" |
								// "plan_started" | "plan_progress" | "plan_completed"

		private String content;

		private FileChange file;

		private String planId;

		private Object planRecord;

		private String conversationId;

		public Response() {
		}

		public Response(String type, String content) {
			this.type = type;
			this.content = content;
		}

		public static Response token(String content) {
			return new Response("token", content);
		}

		public static Response file(String path, String content) {
			Response r = new Response();
			r.setType("file");
			r.setFile(new FileChange(path, content));
			return r;
		}

		public static Response status(String message) {
			return new Response("status", message);
		}

		public static Response error(String message) {
			return new Response("error", message);
		}

		public static Response done() {
			return new Response("done", null);
		}

		public static Response planStarted(String planId) {
			Response r = new Response();
			r.setType("plan_started");
			r.setPlanId(planId);
			return r;
		}

		public static Response planProgress(String planId, Object planRecord) {
			Response r = new Response();
			r.setType("plan_progress");
			r.setPlanId(planId);
			r.setPlanRecord(planRecord);
			return r;
		}

		public static Response planCompleted(String planId, Object planRecord) {
			Response r = new Response();
			r.setType("plan_completed");
			r.setPlanId(planId);
			r.setPlanRecord(planRecord);
			return r;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getContent() {
			return content;
		}

		public void setContent(String content) {
			this.content = content;
		}

		public FileChange getFile() {
			return file;
		}

		public void setFile(FileChange file) {
			this.file = file;
		}

		public String getPlanId() {
			return planId;
		}

		public void setPlanId(String planId) {
			this.planId = planId;
		}

		public Object getPlanRecord() {
			return planRecord;
		}

		public void setPlanRecord(Object planRecord) {
			this.planRecord = planRecord;
		}

		public String getConversationId() {
			return conversationId;
		}

		public void setConversationId(String conversationId) {
			this.conversationId = conversationId;
		}

	}

	/**
	 * Represents a single file change from AI output.
	 */
	public static class FileChange {

		private String path;

		private String content;

		public FileChange() {
		}

		public FileChange(String path, String content) {
			this.path = path;
			this.content = content;
		}

		public String getPath() {
			return path;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public String getContent() {
			return content;
		}

		public void setContent(String content) {
			this.content = content;
		}

	}

}
