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
package com.alibaba.cloud.ai.lynxe.codeagent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Event notifier for real-time file push in Code Agent sessions. When a
 * {@link com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators.WriteFileOperator}
 * writes a file to disk, it calls {@link #notifyFileWritten} so the WebSocket handler can
 * push the file content to the frontend immediately, instead of waiting for the next poll
 * cycle.
 *
 * <p>
 * Thread-safe: backed by {@link ConcurrentHashMap}.
 */
@Service
public class CodeAgentFileNotifier {

	private static final Logger log = LoggerFactory.getLogger(CodeAgentFileNotifier.class);

	/** rootPlanId -> callback(path, content) */
	private final ConcurrentHashMap<String, BiConsumer<String, String>> listeners = new ConcurrentHashMap<>();

	/**
	 * Register a callback that will be invoked when a file is written for the given root
	 * plan.
	 * @param rootPlanId the root plan identifier
	 * @param callback receives (normalizedPath, fileContent)
	 */
	public void register(String rootPlanId, BiConsumer<String, String> callback) {
		listeners.put(rootPlanId, callback);
		log.info("[CodeAgentFileNotifier] Registered listener for rootPlanId={}", rootPlanId);
	}

	/**
	 * Unregister the callback for a root plan (e.g. on plan completion or WebSocket
	 * disconnect).
	 * @param rootPlanId the root plan identifier
	 */
	public void unregister(String rootPlanId) {
		if (listeners.remove(rootPlanId) != null) {
			log.info("[CodeAgentFileNotifier] Unregistered listener for rootPlanId={}", rootPlanId);
		}
	}

	/**
	 * Notify that a file has been written to disk. If a listener is registered for the
	 * given rootPlanId, the callback is invoked synchronously (in the caller's thread).
	 * If no listener is registered this is a no-op.
	 * @param rootPlanId the root plan identifier
	 * @param path the normalized file path
	 * @param content the file content
	 */
	public void notifyFileWritten(String rootPlanId, String path, String content) {
		BiConsumer<String, String> callback = listeners.get(rootPlanId);
		if (callback != null) {
			try {
				callback.accept(path, content);
			}
			catch (Exception e) {
				log.error("[CodeAgentFileNotifier] Callback error for rootPlanId={}, path={}: {}", rootPlanId, path,
						e.getMessage(), e);
			}
		}
	}

}
