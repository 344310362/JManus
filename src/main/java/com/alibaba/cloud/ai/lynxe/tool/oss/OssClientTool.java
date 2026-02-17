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
package com.alibaba.cloud.ai.lynxe.tool.oss;

import com.alibaba.cloud.ai.lynxe.config.rpc.AuthContext;
import com.alibaba.cloud.ai.lynxe.tool.ToolStateInfo;
import com.qiniu.util.StringUtils;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.UnifiedDirectoryManager;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.PutObjectRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.common.QiniuException;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OSS Client Tool - Supports Aliyun OSS and Qiniu Cloud Object Storage. Provides
 * functionality for uploading, listing, replacing, and previewing files.
 */
public class OssClientTool extends AbstractBaseTool<OssClientTool.OssClientInput> {

	private static final Logger log = LoggerFactory.getLogger(OssClientTool.class);

	private static final String TOOL_NAME = "oss_client_tool";

	private static final String ROOT_PATH = "manus";

	private final UnifiedDirectoryManager directoryManager;

	private final ObjectMapper objectMapper;

	private final ToolI18nService toolI18nService;

	// Configuration storage
	private static final Map<String, OssConfig> OSS_CONFIGS = new HashMap<>();

	static {
		// Configure Aliyun OSS
		OSS_CONFIGS.put("aliyun", new OssConfig("your-endpoint", // e.g.,
																	// oss-cn-hangzhou.aliyuncs.com
				"your-access-key-id", "your-access-key-secret", "your-bucket-name", ""));

		// Configure Qiniu Cloud
		OSS_CONFIGS.put("qiniu", new OssConfig("s3.cn-east-1.qiniucs.com", // Endpoint not
																			// strictly
																			// required
																			// for Qiniu
																			// SDK
																			// auto-region
				"IAM-d-sLwdqfZ0KI0z9F5IKsRfXTlY-cSwn_qupifXch", "uFnwkexXnlwF_tWbm4u9wRrynBfsJbip09FEGmTqRPxz",
				"exe-test", "https://t-company-res.exexm.com"));
	}

	public OssClientTool(UnifiedDirectoryManager directoryManager, ObjectMapper objectMapper,
			ToolI18nService toolI18nService) {
		this.directoryManager = directoryManager;
		this.objectMapper = objectMapper;
		this.toolI18nService = toolI18nService;
	}

	@Override
	public ToolExecuteResult run(OssClientInput input) {
		AuthContext.setContextPlanId(rootPlanId);
		if (input.ossType == null || input.ossType.isEmpty()) {
			input.ossType = "qiniu";
		}
		String ossType = input.ossType.toLowerCase();
		OssConfig config = OSS_CONFIGS.get(ossType);
		if (config == null) {
			return new ToolExecuteResult("Unsupported or unconfigured OSS type: " + ossType);
		}

		String fileName = input.fileName;
		if (StringUtils.isNullOrEmpty(fileName)
				&& ("upload".equalsIgnoreCase(input.operation) || "replace".equalsIgnoreCase(input.operation))) {
			return new ToolExecuteResult("Error: filename is required");
		}
		try {
			switch (input.operation.toLowerCase()) {
				case "upload":
				case "replace": // Replace is essentially same as upload in OSS
					return upload(input, config, ossType);
				case "list":
					return list(input, config, ossType);
				case "preview":
					return handlePreview(input, config, ossType);
				default:
					return new ToolExecuteResult("Unsupported operation: " + input.operation);
			}
		}
		catch (Exception e) {
			log.error("OSS operation failed", e);
			return new ToolExecuteResult("Error executing OSS operation: " + e.getMessage());
		}
	}

	private ToolExecuteResult upload(OssClientInput input, OssConfig config, String ossType) throws Exception {
		// Step 3: Find the file in root plan directory
		Path sourceFile = findFileInRootPlan(input.fileName);
		if (sourceFile == null || !Files.exists(sourceFile)) {
			return new ToolExecuteResult("Error: File not found: " + input.fileName
					+ ". Please ensure the file exists in the root plan directory (rootPlanId/).");
		}

		String objectKey = buildObjectKey(AuthContext.getUsername(), input.fileName);

		String accessLink = config.domain + "/" + objectKey;
		if ("aliyun".equals(ossType)) {
			OSS ossClient = new OSSClientBuilder().build(config.endpoint, config.accessKeyId, config.accessKeySecret);
			try {
				PutObjectRequest putObjectRequest = new PutObjectRequest(config.bucketName, objectKey,
						Files.newInputStream(sourceFile));
				ossClient.putObject(putObjectRequest);
				return new ToolExecuteResult("Successfully uploaded to Aliyun OSS: " + objectKey);
			}
			finally {
				ossClient.shutdown();
			}
		}
		else if ("qiniu".equals(ossType)) {
			Configuration cfg = new Configuration(Region.autoRegion());
			UploadManager uploadManager = new UploadManager(cfg);
			Auth auth = Auth.create(config.accessKeyId, config.accessKeySecret);
			String upToken = auth.uploadToken(config.bucketName, objectKey);
			try {
				uploadManager.put(sourceFile.toFile(), objectKey, upToken);
				return new ToolExecuteResult("Successfully uploaded to Qiniu Cloud: " + objectKey);
			}
			catch (QiniuException ex) {
				return new ToolExecuteResult("Qiniu upload failed: " + ex.response.toString());
			}
		}

		return new ToolExecuteResult("Upload not implemented for type: " + ossType + "the access link: " + accessLink);
	}

	private ToolExecuteResult list(OssClientInput input, OssConfig config, String ossType) throws Exception {
		// Ensure list operation is scoped to user's directory
		String prefix = ROOT_PATH + "/" + AuthContext.getUsername() + "/";
		if (input.fileName != null && !input.fileName.isEmpty()) {
			prefix += input.fileName;
		}

		if ("aliyun".equals(ossType)) {
			OSS ossClient = new OSSClientBuilder().build(config.endpoint, config.accessKeyId, config.accessKeySecret);
			try {
				ObjectListing objectListing = ossClient.listObjects(config.bucketName, prefix);
				List<String> keys = objectListing.getObjectSummaries()
					.stream()
					.map(OSSObjectSummary::getKey)
					.collect(Collectors.toList());
				return new ToolExecuteResult(
						"Files in Aliyun OSS (" + config.bucketName + "):\n" + String.join("\n", keys));
			}
			finally {
				ossClient.shutdown();
			}
		}
		else if ("qiniu".equals(ossType)) {
			Configuration cfg = new Configuration(Region.autoRegion());
			Auth auth = Auth.create(config.accessKeyId, config.accessKeySecret);
			BucketManager bucketManager = new BucketManager(auth, cfg);
			BucketManager.FileListIterator fileListIterator = bucketManager.createFileListIterator(config.bucketName,
					prefix, 100, "");
			StringBuilder result = new StringBuilder("Files in Qiniu OSS (" + config.bucketName + "):\n");
			while (fileListIterator.hasNext()) {
				com.qiniu.storage.model.FileInfo[] items = fileListIterator.next();
				for (com.qiniu.storage.model.FileInfo item : items) {
					result.append("- [")
						.append(item.key)
						.append("]")
						.append("(")
						.append(config.domain)
						.append("/")
						.append(item.key)
						.append("\n");
				}
			}
			return new ToolExecuteResult(result.toString());
		}
		return new ToolExecuteResult("List not implemented for type: " + ossType);
	}

	private ToolExecuteResult handlePreview(OssClientInput input, OssConfig config, String ossType) throws Exception {
		if (input.fileName == null || input.fileName.isEmpty()) {
			return new ToolExecuteResult("File name is required for preview.");
		}
		String objectKey = buildObjectKey(input.userName, input.fileName);

		if ("aliyun".equals(ossType)) {
			OSS ossClient = new OSSClientBuilder().build(config.endpoint, config.accessKeyId, config.accessKeySecret);
			try {
				// Generate a signed URL valid for 1 hour
				Date expiration = new Date(System.currentTimeMillis() + 3600 * 1000);
				URL url = ossClient.generatePresignedUrl(config.bucketName, objectKey, expiration);
				return new ToolExecuteResult("Preview URL (valid for 1 hour): " + url.toString());
			}
			finally {
				ossClient.shutdown();
			}
		}
		else if ("qiniu".equals(ossType)) {
			Auth auth = Auth.create(config.accessKeyId, config.accessKeySecret);
			// Assuming private bucket, generate download token. If public, just concat
			// domain + key.
			// Here we assume we need a download signature, valid for 1 hour.
			// Note: Qiniu requires a domain to be configured. Since we only have bucket
			// info here,
			// and the SDK doesn't auto-fetch domain, this is a limitation.
			// We will return a placeholder or require domain in config.
			// For simplicity, we assume a domain is provided or we return a message about
			// domain configuration.

			// Re-checking config for domain.
			// Adding domain to OssConfig for Qiniu
			String url = auth.privateDownloadUrl(config.domain + "/" + objectKey, 3600);
			return new ToolExecuteResult("Preview URL (valid for 1 hour, ensure domain is configured): " + url);
		}
		return new ToolExecuteResult("Preview not implemented for type: " + ossType);
	}

	/**
	 * Find file in root plan directory (same as GlobalFileOperator) Checks root plan
	 * directory first, then subplan directory if applicable
	 */
	private Path findFileInRootPlan(String filename) {
		try {
			if (this.rootPlanId == null || this.rootPlanId.isEmpty()) {
				log.error("rootPlanId is required for file operations but is null or empty");
				return null;
			}

			// Normalize the file path to remove plan ID prefixes and relative path
			// indicators
			String normalizedPath = normalizeFilePath(filename);

			// Get the root plan directory (same as GlobalFileOperator)
			Path rootPlanDirectory = directoryManager.getRootPlanDirectory(rootPlanId);

			// Check root plan directory first
			Path rootPlanPath = rootPlanDirectory.resolve(normalizedPath).normalize();

			// Ensure the path stays within the root plan directory
			if (!rootPlanPath.startsWith(rootPlanDirectory)) {
				log.warn("File path is outside root plan directory: {}", filename);
				return null;
			}

			// If file exists in root plan directory, use it
			if (Files.exists(rootPlanPath)) {
				log.info("Found file in root plan directory: {}", filename);
				return rootPlanPath;
			}

			// If currentPlanId exists and differs from rootPlanId, check subplan
			// directory
			if (this.currentPlanId != null && !this.currentPlanId.isEmpty()
					&& !this.currentPlanId.equals(this.rootPlanId)) {
				Path subplanDirectory = rootPlanDirectory.resolve(this.currentPlanId);
				Path subplanPath = subplanDirectory.resolve(normalizedPath).normalize();

				// Ensure subplan path stays within subplan directory
				if (subplanPath.startsWith(subplanDirectory)) {
					// If file exists in subplan directory, use it
					if (Files.exists(subplanPath)) {
						log.info("Found file in subplan directory: {}", filename);
						return subplanPath;
					}
				}
			}

			log.warn("File not found in root plan directory: {}", filename);
			return null;
		}
		catch (Exception e) {
			log.error("Error finding file: {}", filename, e);
			return null;
		}
	}

	/**
	 * Normalize file path by removing plan ID prefixes and relative path indicators (same
	 * as GlobalFileOperator)
	 */
	private String normalizeFilePath(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return filePath;
		}

		// Remove leading slashes and relative path indicators
		String normalized = filePath.trim();
		while (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}

		// Remove "./" prefix if present
		if (normalized.startsWith("./")) {
			normalized = normalized.substring(2);
		}

		// Remove plan ID prefix (e.g., "plan-1763035234741/")
		if (normalized.matches("^plan-[^/]+/.*")) {
			normalized = normalized.replaceFirst("^plan-[^/]+/", "");
		}

		return normalized;
	}

	private String buildObjectKey(String userName, String fileName) {
		return ROOT_PATH + "/" + userName + "/" + fileName;
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public String getDescription() {
		return "A tool for interacting with OSS (Aliyun, Qiniu). Supports upload, list, replace, and preview operations.";
	}

	@Override
	public String getServiceGroup() {
		return "oss";
	}

	@Override
	public String getParameters() {
		return "{\"type\":\"object\",\"properties\":{\"operation\":{\"type\":\"string\",\"description\":\"Operation type: upload, replace, list, preview\"},\"ossType\":{\"type\":\"string\",\"description\":\"OSS provider type: aliyun, qiniu. Default is qiniu\"},\"userName\":{\"type\":\"string\",\"description\":\"User name for storage isolation\"},\"fileName\":{\"type\":\"string\",\"description\":\"File name to be stored or accessed in OSS\"}},\"required\":[\"operation\",\"userName\"]}";
	}

	@Override
	public Class<OssClientInput> getInputType() {
		return OssClientInput.class;
	}

	@Override
	public ToolStateInfo getCurrentToolStateString() {
		String toolState = "OssClientTool is ready for operator file:\n" + "- Current Plan ID: " + currentPlanId + "\n"
				+ "- Root Plan ID: " + rootPlanId + "\n";
		return new ToolStateInfo(null, toolState);
	}

	@Override
	public void cleanup(String planId) {
		log.info("OssClientTool cleanup for planId: {}", planId);
	}

	public static class OssClientInput {

		@JsonPropertyDescription("Operation type: upload, replace, list, preview")
		@JsonProperty(required = true)
		public String operation;

		@JsonPropertyDescription("OSS provider type: aliyun, qiniu. Default is qiniu")
		@JsonProperty(required = false)
		public String ossType;

		@JsonPropertyDescription("User name for storage isolation")
		@JsonProperty(required = true)
		public String userName;

		@JsonPropertyDescription("File name to be stored or accessed in OSS")
		public String fileName;

	}

	private static class OssConfig {

		String endpoint;

		String accessKeyId;

		String accessKeySecret;

		String bucketName;

		String domain;

		public OssConfig(String endpoint, String accessKeyId, String accessKeySecret, String bucketName,
				String domain) {
			this.endpoint = endpoint;
			this.accessKeyId = accessKeyId;
			this.accessKeySecret = accessKeySecret;
			this.bucketName = bucketName;
			this.domain = domain;
		}

	}

}
