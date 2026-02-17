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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.cloud.ai.lynxe.tool.filesystem.UnifiedDirectoryManager;

/**
 * Code Agent 预览分享控制器。
 * <p>
 * POST /api/code-agent/share — 存储 HTML 到文件系统，返回 shareId 和 url。
 * GET /share/{shareId} — 从文件系统读取并直接返回完整 HTML 页面，不经过 Vue SPA。
 * <p>
 * 分享文件持久化在 extensions/inner_storage/_shares/ 目录下，重启后依然有效。
 */
@RestController
@CrossOrigin(origins = "*")
public class CodeAgentShareController {

	private static final Logger log = LoggerFactory.getLogger(CodeAgentShareController.class);

	private static final String SHARES_DIR = "_shares";

	@Autowired
	private UnifiedDirectoryManager directoryManager;

	@PostMapping("/api/code-agent/share")
	public ResponseEntity<Map<String, String>> createShare(@RequestBody Map<String, String> body) {
		String html = body.get("html");
		if (html == null || html.isBlank()) {
			return ResponseEntity.badRequest().build();
		}
		String shareId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		try {
			Path sharesDir = directoryManager.getInnerStorageRoot().resolve(SHARES_DIR);
			Files.createDirectories(sharesDir);
			Path htmlFile = sharesDir.resolve(shareId + ".html");
			Files.writeString(htmlFile, html, StandardCharsets.UTF_8);
			log.info("[CodeAgent Share] Saved share {} ({} bytes)", shareId, html.length());
		}
		catch (IOException e) {
			log.error("[CodeAgent Share] Failed to save share {}: {}", shareId, e.getMessage());
			return ResponseEntity.internalServerError().build();
		}
		return ResponseEntity.ok(Map.of("shareId", shareId, "url", "/share/" + shareId));
	}

	@GetMapping(value = "/share/{shareId}", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
	public ResponseEntity<String> getSharePage(@PathVariable String shareId) {
		// shareId 只允许字母数字，防止路径穿越
		if (!shareId.matches("^[a-zA-Z0-9]+$")) {
			return ResponseEntity.badRequest().body("<h1>Invalid share ID</h1>");
		}
		try {
			Path htmlFile = directoryManager.getInnerStorageRoot().resolve(SHARES_DIR).resolve(shareId + ".html");
			if (!Files.exists(htmlFile)) {
				return ResponseEntity.status(404).body("<h1>Preview not found or expired</h1>");
			}
			String html = Files.readString(htmlFile, StandardCharsets.UTF_8);
			return ResponseEntity.ok(html);
		}
		catch (IOException e) {
			log.error("[CodeAgent Share] Failed to read share {}: {}", shareId, e.getMessage());
			return ResponseEntity.status(500).body("<h1>Internal error</h1>");
		}
	}

}
